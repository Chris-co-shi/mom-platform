package io.github.chrisshi.mom.qms;

import io.github.chrisshi.mom.i18n.validation.I18nRules;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 QMS 最终 Key 约束、历史数据预检及 Flyway 失败时的数据保留。 */
@Testcontainers
class QmsI18nSchemaPostgresqlIT {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"));

    @Test
    void originalV4DataMustFailExplicitlyWithoutChangingHistory() {
        String schema = schema();
        flyway(schema, "4").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        List<String> before = snapshot(jdbc);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM qms_i18n_message_definition
                WHERE message_key <> lower(message_key) AND NOT enabled
                """, Integer.class)).isPositive();

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("QMS I18n message_key precheck failed");

        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("4");
        // 失败回滚后，旧约束仍禁止无前缀 Key，且仍允许旧格式。
        assertThatThrownBy(() -> insert(jdbc, "101", "title"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insert(jdbc, "102", "qms.Legacy");
    }

    @Test
    void compliantV4DataMustUpgradeAndMatchFrameworkRules() {
        String schema = compliantV4Fixture();
        JdbcTemplate jdbc = jdbc(schema);
        List<String> before = snapshot(jdbc);
        flyway(schema, null).migrate();

        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(snapshot(jdbc)).isEqualTo(before);
        int id = 100;
        for (String key : List.of("title", "inspection.result", "a1_b2-c3.d4", "qms.existing", "a".repeat(160))) {
            assertThat(I18nRules.messageKey(key)).isEqualTo(key);
            insert(jdbc, Integer.toString(id++), key);
        }
        for (String key : List.of("", "1title", ".title", "title.", "a..b", "a_-b", "a/b",
                "has space", " title", "title\n", "qms.Title", "TITLE", "中文", "ｔｉｔｌｅ", "a".repeat(161))) {
            String recordId = Integer.toString(id++);
            assertThatThrownBy(() -> insert(jdbc, recordId, key))
                    .as("数据库拒绝非法持久化 Key: %s", key)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .isNotInstanceOf(DuplicateKeyException.class);
        }
        assertThatThrownBy(() -> insert(jdbc, "200", "title")).isInstanceOf(DuplicateKeyException.class);
        // 逻辑删除不能释放稳定 Key，保持原唯一约束语义。
        jdbc.update("UPDATE qms_i18n_message_definition SET deleted = true WHERE message_key = 'title'");
        assertThatThrownBy(() -> insert(jdbc, "201", "title")).isInstanceOf(DuplicateKeyException.class);
        assertThat(I18nRules.messageKey("TITLE")).isEqualTo("title");
    }

    @Test
    void precheckMustIncludeEnabledDisabledAndDeletedRows() {
        for (String state : List.of("enabled", "disabled", "deleted")) {
            String schema = compliantV4Fixture();
            JdbcTemplate jdbc = jdbc(schema);
            insert(jdbc, "101", "qms.Legacy");
            jdbc.update("UPDATE qms_i18n_message_definition SET enabled = ?, deleted = ? WHERE id = '101'",
                    !state.equals("disabled"), state.equals("deleted"));
            List<String> before = snapshot(jdbc);

            assertThatThrownBy(() -> flyway(schema, null).migrate())
                    .as("预检覆盖 %s 数据", state)
                    .isInstanceOf(FlywayException.class)
                    .hasStackTraceContaining("QMS I18n message_key precheck failed");
            assertThat(snapshot(jdbc)).isEqualTo(before);
            assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("4");
        }
    }

    private String compliantV4Fixture() {
        String schema = schema();
        flyway(schema, "4").migrate();
        // 仅构造全量合规的测试前置数据，不代表批准生产 Key 改名；实际迁移不得执行此操作。
        jdbc(schema).update("UPDATE qms_i18n_message_definition SET message_key = lower(message_key)");
        return schema;
    }

    private String schema() {
        return "qms_i18n_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration/qms");
        if (target != null) config.target(target);
        return config.load();
    }

    private JdbcTemplate jdbc(String schema) {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));
    }

    private void insert(JdbcTemplate jdbc, String id, String key) {
        jdbc.update("""
                INSERT INTO qms_i18n_message_definition
                    (id, message_key, created_by, created_at, updated_by, updated_at)
                VALUES (?, ?, 'TEST', CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP)
                """, id, key);
    }

    private List<String> snapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT row_to_json(m)::text FROM qms_i18n_message_definition m ORDER BY id
                """, String.class);
    }
}
