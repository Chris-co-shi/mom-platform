package io.github.chrisshi.mom.coderule.store;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.application.DefaultCodeGenerationApplication;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.handler.SequenceCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在真实 MySQL 上验证规则行锁、事务型计数器和发号幂等的组合行为。 */
@Testcontainers
class CodeRuleMysqlIT {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withUrlParam("preserveInstants", "true");

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2041-05-01T08:09:10.123456Z"), ZoneOffset.UTC);
    private static final CodeRuleDefinition DEFINITION = new CodeRuleDefinition(1,
            List.of(new CodeRuleDefinition.Segment("seq", "SEQUENCE", Map.of("width", 1, "step", 1))));

    private JdbcTemplate jdbc;
    private JdbcCodeRuleStore store;
    private TransactionTemplate transactions;
    private DefaultCodeGenerationApplication generator;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        store = new JdbcCodeRuleStore(source, JsonMapper.builder().build(), 200,
                () -> Optional.of(new AuditActor("test", ActorType.SYSTEM)), CLOCK, "mdm", true);
        generator = new DefaultCodeGenerationApplication(store,
                new CodeSegmentRegistry(List.of(new SequenceCodeSegmentHandler())), new CodeFieldRegistry(List.of()), CLOCK);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mdm_code_rule (
                    id varchar(19) PRIMARY KEY, rule_code varchar(64) NOT NULL UNIQUE,
                    usage_scenario varchar(128), description varchar(1024), version bigint NOT NULL,
                    created_at datetime(6) NOT NULL, created_by varchar(64) NOT NULL,
                    updated_at datetime(6) NOT NULL, updated_by varchar(64) NOT NULL
                ) ENGINE=InnoDB COLLATE=utf8mb4_0900_bin
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mdm_code_rule_revision (
                    id varchar(19) PRIMARY KEY, rule_id varchar(19) NOT NULL, revision_no int NOT NULL,
                    status varchar(16) NOT NULL, definition json NOT NULL,
                    created_at datetime(6) NOT NULL, created_by varchar(64) NOT NULL,
                    updated_at datetime(6) NOT NULL, updated_by varchar(64) NOT NULL,
                    draft_rule_id varchar(19) GENERATED ALWAYS AS
                        (CASE WHEN status = 'DRAFT' THEN rule_id END) STORED,
                    enabled_rule_id varchar(19) GENERATED ALWAYS AS
                        (CASE WHEN status = 'ENABLED' THEN rule_id END) STORED,
                    UNIQUE KEY uk_draft (draft_rule_id), UNIQUE KEY uk_enabled (enabled_rule_id)
                ) ENGINE=InnoDB COLLATE=utf8mb4_0900_bin
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mdm_code_rule_sequence (
                    rule_id varchar(19) NOT NULL, scope_key varchar(16) NOT NULL,
                    `last_value` bigint NOT NULL, PRIMARY KEY (rule_id, scope_key)
                ) ENGINE=InnoDB COLLATE=utf8mb4_0900_bin
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mdm_code_rule_issue (
                    rule_id varchar(19) NOT NULL, request_key varchar(128) PRIMARY KEY,
                    request_hash char(64) NOT NULL, code varchar(128) NOT NULL,
                    revision_id varchar(19) NOT NULL, revision_no int NOT NULL, created_at datetime(6) NOT NULL,
                    UNIQUE KEY uk_mdm_code_rule_issue_code (rule_id, code)
                ) ENGINE=InnoDB COLLATE=utf8mb4_0900_bin
                """);
        for (String table : List.of("mdm_code_rule", "mdm_code_rule_revision", "mdm_code_rule_sequence", "mdm_code_rule_issue")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        store.insertRule("123", "part", "测试", null);
        store.insertRevision("456", "123", 1, "ENABLED", DEFINITION);
    }

    @Test
    void concurrentFirstAllocationsSerializeAndReplayWithoutConsumingSequence() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return generate("req-1");
            });
            var second = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return generate("req-2");
            });

            String firstCode = first.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstCode, second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder("1", "2");
            assertThat(generate("req-1")).isEqualTo(firstCode);
            assertThat(generate("req-3")).isEqualTo("3");
        }
    }

    @Test
    void rollbackAndOverflowDoNotConsumeNumber() {
        transactions.executeWithoutResult(status -> {
            assertThat(generator.generate("part", Map.of(), "cancelled").code()).isEqualTo("1");
            status.setRollbackOnly();
        });
        assertThat(store.issued("cancelled")).isNull();
        assertThat(generate("retry")).isEqualTo("1");
        jdbc.update("UPDATE mdm_code_rule_sequence SET `last_value` = 9");
        assertThatThrownBy(() -> generate("overflow")).isInstanceOf(CodeRuleFailure.class);
        assertThat(jdbc.queryForObject("SELECT `last_value` FROM mdm_code_rule_sequence", Long.class)).isEqualTo(9L);
        assertThat(store.issued("overflow")).isNull();
    }

    @Test
    void jsonAndUtcTimeRoundTripAndConstraintConflictsRemainDistinct() {
        assertThat(store.revision("456").definition()).isEqualTo(DEFINITION);
        assertThat(store.revision("456").createdAt()).isEqualTo(CLOCK.instant());
        store.insertRevision("457", "123", 2, "DRAFT", DEFINITION);
        assertThatThrownBy(() -> store.insertRevision("458", "123", 3, "DRAFT", DEFINITION))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> store.insertRevision("458", "123", 3, "ENABLED", DEFINITION))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(store.insertIssue(new CodeRuleStore.Issue("123", "req", "hash", "1", "456", 1))).isTrue();
        assertThat(store.insertIssue(new CodeRuleStore.Issue("123", "req", "hash", "2", "456", 1))).isFalse();
        assertThatThrownBy(() -> store.insertIssue(new CodeRuleStore.Issue("123", "req-2", "hash", "1", "456", 1)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private String generate(String key) {
        return transactions.execute(status -> generator.generate("part", Map.of(), key).code());
    }
}
