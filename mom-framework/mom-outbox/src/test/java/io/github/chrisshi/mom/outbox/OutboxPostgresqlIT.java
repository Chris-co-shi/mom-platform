package io.github.chrisshi.mom.outbox;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import io.github.chrisshi.mom.outbox.model.OutboxStatus;
import io.github.chrisshi.mom.outbox.persistence.JdbcOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Outbox/Inbox JDBC Adapter 在真实 PostgreSQL 上的事务、Lease、SKIP LOCKED、CAS 与幂等边界。
 *
 * <p>测试创建独立临时表，不读取或修改 System V9 等历史 Migration。业务写与 Outbox/Inbox 使用同一个
 * DataSourceTransactionManager；Broker 调用不在本测试范围，真实中断恢复和 DLQ 由 Messaging Smoke
 * 负责。Docker 不可用时本地跳过，PostgreSQL CI 必须执行。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxPostgresqlIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcOutboxRepository repository;

    /** 创建与生产字段一致的最小测试表并清理上一用例数据。 */
    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        repository = new JdbcOutboxRepository(jdbc, transactions);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mom_outbox_event (
                    event_id varchar(64) PRIMARY KEY,
                    event_type varchar(128) NOT NULL,
                    event_version integer NOT NULL,
                    aggregate_type varchar(128) NOT NULL,
                    aggregate_id varchar(64) NOT NULL,
                    occurred_at timestamptz NOT NULL,
                    producer varchar(128) NOT NULL,
                    correlation_id varchar(128) NOT NULL,
                    payload_json text NOT NULL,
                    status varchar(16) NOT NULL,
                    retry_count integer NOT NULL,
                    next_attempt_at timestamptz NOT NULL,
                    lease_owner varchar(128),
                    lease_until timestamptz,
                    last_error varchar(1000),
                    sent_at timestamptz,
                    created_at timestamptz NOT NULL,
                    updated_at timestamptz NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mom_inbox_event (
                    event_id varchar(64) NOT NULL,
                    consumer_name varchar(128) NOT NULL,
                    event_type varchar(128) NOT NULL,
                    event_version integer NOT NULL,
                    correlation_id varchar(128) NOT NULL,
                    received_at timestamptz NOT NULL,
                    processed_at timestamptz,
                    created_at timestamptz NOT NULL,
                    PRIMARY KEY (event_id, consumer_name)
                )
                """);
        jdbc.execute("CREATE TABLE IF NOT EXISTS business_effect (id varchar(64) PRIMARY KEY)");
        jdbc.execute("TRUNCATE mom_outbox_event, mom_inbox_event, business_effect");
    }

    /** 验证 Outbox 只能在活动本地事务内追加，且事件身份和内容一次写定。 */
    @Test
    void shouldAppendOnlyInsideActiveBusinessTransaction() {
        OutboxAppender appender = new OutboxAppender(repository);
        EventEnvelope event = event("event-1");

        assertThatThrownBy(() -> appender.append(event)).isInstanceOf(IllegalStateException.class);
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO business_effect(id) VALUES ('business-1')");
            appender.append(event);
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_effect", Integer.class)).isOne();
        assertThat(repository.findByEventId("event-1")).hasValueSatisfying(record -> {
            assertThat(record.status()).isEqualTo(OutboxStatus.PENDING);
            assertThat(record.toEnvelope()).isEqualTo(event);
        });
    }

    /** 验证两个实例领取不同记录，且只有当前 Lease Owner 可以执行 SENT/RETRY CAS。 */
    @Test
    void shouldLeaseWithSkipLockedAndProtectStateUsingCas() {
        transactions.executeWithoutResult(status -> {
            repository.append(event("event-1"));
            repository.append(event("event-2"));
        });

        var first = repository.claimAvailable("owner-a", 1, Duration.ofSeconds(30));
        var second = repository.claimAvailable("owner-b", 1, Duration.ofSeconds(30));

        assertThat(first).extracting(record -> record.eventId()).containsExactly("event-1");
        assertThat(second).extracting(record -> record.eventId()).containsExactly("event-2");
        assertThat(repository.markSent("event-1", "owner-b")).isFalse();
        assertThat(repository.markSent("event-1", "owner-a")).isTrue();
        assertThat(repository.markFailure(
                "event-2", "owner-b", 1, OutboxStatus.RETRY,
                Instant.now().plusSeconds(1), "IllegalStateException")).isTrue();
        assertThat(repository.findByEventId("event-2"))
                .hasValueSatisfying(record -> assertThat(record.status()).isEqualTo(OutboxStatus.RETRY));
    }

    /** 验证 Inbox 业务动作失败时记录与业务写整体回滚，成功后重复投递不再执行。 */
    @Test
    void shouldRollbackInboxWithBusinessFailureAndDeduplicateRetry() {
        InboxDeduplicator inbox = new InboxDeduplicator(jdbc, transactions);
        EventEnvelope event = event("event-1");

        assertThatThrownBy(() -> inbox.executeOnce(event, "system-cache", () -> {
            jdbc.update("INSERT INTO business_effect(id) VALUES ('effect-1')");
            throw new IllegalStateException("business rejected");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mom_inbox_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_effect", Integer.class)).isZero();

        assertThat(inbox.executeOnce(event, "system-cache", () ->
                jdbc.update("INSERT INTO business_effect(id) VALUES ('effect-1')"))).isTrue();
        assertThat(inbox.executeOnce(event, "system-cache", () ->
                jdbc.update("INSERT INTO business_effect(id) VALUES ('effect-2')"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_effect", Integer.class)).isOne();
    }

    private static EventEnvelope event(String eventId) {
        return new EventEnvelope(
                eventId,
                "system.dictionary.changed",
                1,
                "system-dictionary",
                "dictionary-1",
                Instant.parse("2026-08-01T00:00:00Z"),
                "mom-system-server",
                "correlation-1",
                "{\"version\":1}"
        );
    }
}
