package io.github.chrisshi.mom.mdm;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** 验证普通 UI 冗余种子的受控删除、自定义内容保护及 PostgreSQL 原子回滚。 */
@Testcontainers
class MdmI18nDeduplicationPostgresqlIT {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"));

    private static final String IDS =
            "'9730000000000000035','9730000000000000037','9730000000000001034','9730000000000001035'";

    @Test
    void emptyDatabaseMustReachLatestWithoutRedundantKeys() {
        String schema = schema();
        flyway(schema, null).migrate();
        assertCleaned(schema);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM mdm_i18n_message_definition", Integer.class))
                .isEqualTo(318);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM mdm_i18n_translation", Integer.class))
                .isEqualTo(636);
    }

    @Test
    void v127UpgradeMustOnlyRemoveFourDefinitionsAndEightTranslations() {
        String schema = baseline();
        JdbcTemplate jdbc = jdbc(schema);
        // 非目标文案允许人工编辑、额外语言和逻辑删除，必须逐字保留所有字段。
        jdbc.update("UPDATE mdm_i18n_message_definition SET description = '自定义说明', version = 3 WHERE message_key = 'mdm.struct.refresh'");
        jdbc.update("UPDATE mdm_i18n_translation SET message_text = '自定义刷新', version = 2 WHERE id = '9740000000000000007'");
        jdbc.update("""
                INSERT INTO mdm_i18n_translation
                  (id, message_id, locale_code, message_text, deleted, created_by, created_at, updated_by, updated_at)
                VALUES ('custom-fr', '9730000000000000001', 'fr-FR', 'Personnalisé', true,
                        'USER', CURRENT_TIMESTAMP, 'USER', CURRENT_TIMESTAMP)
                """);
        List<String> messages = snapshot(jdbc, "mdm_i18n_message_definition", "id NOT IN (" + IDS + ")");
        List<String> translations = snapshot(jdbc, "mdm_i18n_translation", "message_id NOT IN (" + IDS + ")");

        flyway(schema, null).migrate();

        assertCleaned(schema);
        assertThat(snapshot(jdbc, "mdm_i18n_message_definition", "true")).isEqualTo(messages);
        assertThat(snapshot(jdbc, "mdm_i18n_translation", "true")).isEqualTo(translations);
        assertThat(flyway(schema, null).migrate().migrationsExecuted).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE mdm_i18n_message_definition SET id = 'replacement' WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET message_key = 'mdm.custom.cancel' WHERE id = '9730000000000000035'",
            "DELETE FROM mdm_i18n_message_definition WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET description = '人工说明' WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET enabled = false WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET deleted = true WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET version = 1 WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET updated_by = 'USER' WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_message_definition SET updated_at = updated_at + interval '1 second' WHERE id = '9730000000000000035'",
            "UPDATE mdm_i18n_translation SET message_text = '自定义取消' WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET message_text = 'Cancel {0}' WHERE id = '9740000000000000070'",
            "UPDATE mdm_i18n_translation SET id = 'replacement' WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET locale_code = 'fr-FR' WHERE id = '9740000000000000069'",
            "DELETE FROM mdm_i18n_translation WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET deleted = true WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET version = 1 WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET updated_by = 'USER' WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET updated_at = updated_at + interval '1 second' WHERE id = '9740000000000000069'",
            "UPDATE mdm_i18n_translation SET message_id = 'missing' WHERE id = '9740000000000000069'",
            "INSERT INTO mdm_i18n_translation (id,message_id,locale_code,message_text,created_by,created_at,updated_by,updated_at) VALUES ('extra','9730000000000000035','fr-FR','Annuler','USER',CURRENT_TIMESTAMP,'USER',CURRENT_TIMESTAMP)",
            "INSERT INTO mdm_i18n_message_definition (id,message_key,created_by,created_at,updated_by,updated_at) VALUES ('replacement','mdm.custom.action','USER',CURRENT_TIMESTAMP,'USER',CURRENT_TIMESTAMP); UPDATE mdm_i18n_translation SET message_id = 'replacement' WHERE id = '9740000000000000069'"
    })
    void anyTargetDriftMustFailAndPreserveEveryRow(String mutation) {
        String schema = baseline();
        jdbc(schema).execute(mutation);
        assertBlocked(schema, "seed mismatch");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE custom_reference (message_id varchar(19) REFERENCES mdm_i18n_message_definition(id) ON DELETE CASCADE); INSERT INTO custom_reference VALUES ('9730000000000000035')",
            "CREATE TABLE custom_reference (message_key varchar(160) REFERENCES mdm_i18n_message_definition(message_key) ON DELETE CASCADE); INSERT INTO custom_reference VALUES ('mdm.struct.cancel')",
            "CREATE TABLE custom_reference (translation_id varchar(19) REFERENCES mdm_i18n_translation(id) ON DELETE CASCADE); INSERT INTO custom_reference VALUES ('9740000000000000069')",
            "CREATE TABLE custom_reference (message_id varchar(19)); INSERT INTO custom_reference VALUES ('9730000000000000035')",
            "CREATE TABLE custom_reference (i18n_key varchar(160)); INSERT INTO custom_reference VALUES ('mdm.struct.cancel')",
            "CREATE TABLE custom_reference (payload jsonb); INSERT INTO custom_reference VALUES ('{\"key\":\"mdm.struct.cancel\"}')"
    })
    void foreignOrLogicalReferenceMustBlockInsteadOfCascading(String ddl) {
        String schema = baseline();
        JdbcTemplate jdbc = jdbc(schema);
        jdbc.execute(ddl);
        List<String> before = snapshot(jdbc, "custom_reference", "true");
        assertBlocked(schema, "reference");
        assertThat(snapshot(jdbc, "custom_reference", "true")).isEqualTo(before);
    }

    @Test
    void deleteFailureAfterTranslationDeleteMustRollbackBothTablesAndHistory() {
        String schema = baseline();
        JdbcTemplate jdbc = jdbc(schema);
        jdbc.execute("""
                CREATE FUNCTION reject_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected delete failure'; END $$;
                CREATE TRIGGER reject_cleanup BEFORE DELETE ON mdm_i18n_message_definition
                FOR EACH ROW EXECUTE FUNCTION reject_cleanup();
                """);
        assertBlocked(schema, "injected delete failure");
    }

    @Test
    void embeddedOldKeyInAnotherTranslationMustBePreserved() {
        String schema = baseline();
        jdbc(schema).update("UPDATE mdm_i18n_translation SET message_text = 'Use mdm.struct.cancel' WHERE id = '9740000000000000001'");
        assertBlocked(schema, "embedded message reference");
    }

    @Test
    void suppressedDefinitionDeleteMustRollbackTranslations() {
        String schema = baseline();
        jdbc(schema).execute("""
                CREATE FUNCTION suppress_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RETURN NULL; END $$;
                CREATE TRIGGER suppress_cleanup BEFORE DELETE ON mdm_i18n_message_definition
                FOR EACH ROW EXECUTE FUNCTION suppress_cleanup();
                """);
        assertBlocked(schema, "message delete count mismatch");
    }

    private void assertBlocked(String schema, String reason) {
        JdbcTemplate jdbc = jdbc(schema);
        List<String> messages = snapshot(jdbc, "mdm_i18n_message_definition", "true");
        List<String> translations = snapshot(jdbc, "mdm_i18n_translation", "true");
        List<String> history = snapshot(jdbc, "flyway_schema_history", "true");
        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(FlywayException.class).hasStackTraceContaining(reason);
        assertThat(snapshot(jdbc, "mdm_i18n_message_definition", "true")).isEqualTo(messages);
        assertThat(snapshot(jdbc, "mdm_i18n_translation", "true")).isEqualTo(translations);
        assertThat(snapshot(jdbc, "flyway_schema_history", "true")).isEqualTo(history);
        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("127");
    }

    private void assertCleaned(String schema) {
        JdbcTemplate jdbc = jdbc(schema);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mdm_i18n_message_definition WHERE id IN (" + IDS + ")", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mdm_i18n_translation WHERE message_id IN (" + IDS + ")", Integer.class)).isZero();
        // V128 保证清理成立；之后的 V129+ 可以新增独立能力，不应使清理测试失效。
        assertThat(Integer.parseInt(flyway(schema, null).info().current().getVersion().getVersion())).isGreaterThanOrEqualTo(128);
    }

    private String baseline() {
        String schema = schema();
        flyway(schema, "127").migrate();
        return schema;
    }

    private String schema() {
        return "mdm_dedup_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration/mdm");
        if (target != null) config.target(target);
        return config.load();
    }

    private JdbcTemplate jdbc(String schema) {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));
    }

    private List<String> snapshot(JdbcTemplate jdbc, String table, String predicate) {
        return jdbc.queryForList("SELECT to_jsonb(t)::text FROM " + table + " t WHERE " + predicate
                + " ORDER BY to_jsonb(t)::text", String.class);
    }
}
