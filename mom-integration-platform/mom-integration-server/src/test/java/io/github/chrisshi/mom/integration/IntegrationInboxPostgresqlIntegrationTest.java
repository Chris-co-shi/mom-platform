package io.github.chrisshi.mom.integration;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration Inbox 真实 PostgreSQL 幂等与事务集成测试。
 *
 * <p>测试不启动 RocketMQ Binder，只验证 Binder 调用函数后的数据库语义：同一事件重复三次只执行一次业务
 * 动作；业务动作失败时 Inbox INSERT 必须回滚，使后续 RocketMQ 重新投递仍可正常处理。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomIntegrationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.integration.message-consumer.enabled=false",
                "spring.cloud.function.definition=",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
class IntegrationInboxPostgresqlIntegrationTest {

    private static final String SCHEMA = "mom_integration";
    private static final String CONSUMER = "integration-inbox-test-v1";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private InboxDeduplicator deduplicator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 注入 Integration 独立 Schema 的真实 PostgreSQL 连接。
     *
     * @param registry Spring 测试动态属性注册器
     */
    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true"
                + "&ApplicationName=mom-integration-inbox-test");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /**
     * 验证相同事件重复执行三次只产生一次业务结果和一条 Inbox 记录。
     */
    @Test
    void duplicateDeliveriesShouldProduceOneBusinessResult() {
        EventEnvelope event = event("deduplicate");
        AtomicInteger actionCount = new AtomicInteger();

        assertTrue(deduplicator.executeOnce(event, CONSUMER, actionCount::incrementAndGet));
        assertFalse(deduplicator.executeOnce(event, CONSUMER, actionCount::incrementAndGet));
        assertFalse(deduplicator.executeOnce(event, CONSUMER, actionCount::incrementAndGet));

        assertEquals(1, actionCount.get());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from mom_inbox_event where event_id = ? and consumer_name = ? and processed_at is not null",
                Long.class,
                event.eventId(),
                CONSUMER));
    }

    /**
     * 验证业务失败不会留下 Inbox 占位，后续重新投递可以成功执行。
     */
    @Test
    void failedBusinessActionShouldRollbackInboxAndAllowRetry() {
        EventEnvelope event = event("rollback");

        assertThrows(IllegalStateException.class, () -> deduplicator.executeOnce(
                event,
                CONSUMER,
                () -> {
                    throw new IllegalStateException("主动触发消费者事务回滚");
                }));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from mom_inbox_event where event_id = ? and consumer_name = ?",
                Long.class,
                event.eventId(),
                CONSUMER));

        assertTrue(deduplicator.executeOnce(event, CONSUMER, () -> {
        }));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from mom_inbox_event where event_id = ? and consumer_name = ? and processed_at is not null",
                Long.class,
                event.eventId(),
                CONSUMER));
    }

    private static EventEnvelope event(String prefix) {
        String eventId = UUID.randomUUID().toString();
        return new EventEnvelope(
                eventId,
                "mdm.technical-probe.created",
                1,
                "MdmDataProbe",
                prefix + "-aggregate",
                Instant.now(),
                "mom-mdm-server",
                prefix + "-correlation",
                "{\"probeId\":\"" + prefix + "\"}");
    }
}
