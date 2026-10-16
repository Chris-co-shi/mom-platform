package io.github.chrisshi.mom.qms;

import io.github.chrisshi.mom.i18n.validation.I18nRules;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 QMS 最终 Key 约束、历史数据预检及 Flyway 失败时的数据保留。 */
@Testcontainers
class QmsI18nSchemaPostgresqlIT {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"));

    @Test
    void emptyDatabaseMustMigrateThroughNormalizationToLatest() {
        String schema = schema();
        flyway(schema, null).migrate();
        JdbcTemplate jdbc = jdbc(schema);
        // Flyway 还会写一条无版本的 Schema 创建记录，此处核对所有版本迁移的实际顺序。
        assertThat(jdbc.queryForList("""
                SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank
                """, String.class))
                .containsExactly("1", "2", "3", "4", "4.1", "5", "6");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qms_i18n_message_definition", Integer.class))
                .isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qms_i18n_translation", Integer.class))
                .isEqualTo(72);
        assertThat(jdbc.queryForList("SELECT message_key FROM qms_i18n_message_definition", String.class))
                .allSatisfy(key -> assertThat(I18nRules.messageKey(key)).isEqualTo(key));
    }

    @Test
    void originalV4DataMustUpgradeWithoutChangingMetadataOrTranslations() {
        String schema = schema();
        flyway(schema, "4").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM qms_i18n_message_definition
                WHERE message_key <> lower(message_key) AND NOT enabled
                """, Integer.class)).isEqualTo(7);
        // 覆盖历史种子被重新启用、逻辑删除的情况；规范化不能改变这些状态或版本。
        jdbc.update("UPDATE qms_i18n_message_definition SET enabled = true WHERE message_key = 'qms.spec.addItem'");
        jdbc.update("UPDATE qms_i18n_message_definition SET deleted = true WHERE message_key = 'qms.spec.removeItem'");
        List<String> expected = normalizedSnapshot(jdbc);
        List<String> translations = translationSnapshot(jdbc);

        flyway(schema, null).migrate();
        assertThat(snapshot(jdbc)).isEqualTo(expected);
        assertThat(translationSnapshot(jdbc)).isEqualTo(translations);
        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("6");
    }

    @Test
    void compliantV4DataMustUpgradeAndMatchFrameworkRules() {
        String schema = compliantV4Fixture();
        JdbcTemplate jdbc = jdbc(schema);
        List<String> before = snapshot(jdbc);
        flyway(schema, null).migrate();

        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("6");
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
                    .hasStackTraceContaining("QMS I18n normalization precheck failed");
            assertThat(snapshot(jdbc)).isEqualTo(before);
            assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("4");
        }
    }

    @Test
    void caseCollisionMustRollbackIncludingDisabledAndDeletedRows() {
        for (String state : List.of("enabled", "disabled", "deleted")) {
            String schema = schema();
            flyway(schema, "4").migrate();
            JdbcTemplate jdbc = jdbc(schema);
            insert(jdbc, "101", "qms.common.nodata");
            jdbc.update("UPDATE qms_i18n_message_definition SET enabled = ?, deleted = ? WHERE id = '101'",
                    state.equals("enabled"), state.equals("deleted"));
            assertNormalizationFailsWithoutChanges(schema, "normalized key collision");
        }
    }

    @Test
    void malformedHistoricalKeyMustFailBeforeNormalization() {
        String schema = schema();
        flyway(schema, "4").migrate();
        // 模拟历史约束曾被外部工具移除的脏数据，正式迁移必须仍主动预检。
        JdbcTemplate jdbc = jdbc(schema);
        jdbc.execute("ALTER TABLE qms_i18n_message_definition DROP CONSTRAINT ck_qms_i18n_message_key");
        insert(jdbc, "101", "qms.bad..key");
        assertNormalizationFailsWithoutChanges(schema, "invalid normalized key");
    }

    @Test
    void keyBasedForeignReferenceMustFailWithoutCascadingChanges() {
        String schema = schema();
        flyway(schema, "4").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        jdbc.execute("""
                CREATE TABLE legacy_key_reference (
                    message_key varchar(160) REFERENCES qms_i18n_message_definition(message_key) ON UPDATE CASCADE)
                """);
        jdbc.update("INSERT INTO legacy_key_reference VALUES ('qms.common.noData')");
        assertNormalizationFailsWithoutChanges(schema, "incompatible message_key reference");
        assertThat(jdbc.queryForObject("SELECT message_key FROM legacy_key_reference", String.class))
                .isEqualTo("qms.common.noData");
    }

    @Test
    void previouslyAppliedV5MustExposeEarlierMigrationValidationRisk(@TempDir Path migrations) throws IOException {
        // 使用原样 V1～V5 文件重现上一版发布包，不通过 outOfOrder 或忽略验证伪造环境。
        for (String file : List.of("V1__create_quality_definition.sql", "V2__create_qms_i18n.sql",
                "V3__seed_quality_definition_web_copy.sql", "V4__deprecate_legacy_quality_definition_tables.sql",
                "V5__align_i18n_message_key_constraint.sql")) {
            try (var source = getClass().getResourceAsStream("/db/migration/qms/" + file)) {
                assertThat(source).isNotNull();
                Files.copy(source, migrations.resolve(file));
            }
        }
        String schema = compliantV4Fixture();
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).locations("filesystem:" + migrations).load().migrate();
        List<String> before = snapshot(jdbc(schema));
        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Detected resolved migration not applied to database: 4.1");
        assertThat(snapshot(jdbc(schema))).isEqualTo(before);
    }

    /** V6 只能改变名称和注释，不能重建约束、改写数据或改变既有约束定义。 */
    @Test
    void metadataRepairMustPreserveRowsConstraintIdentityAndDefinitions() {
        String schema = schema();
        flyway(schema, "5").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        var definitions = snapshot(jdbc);
        var translations = translationSnapshot(jdbc);
        var constraints = constraintSnapshot(jdbc, schema);
        var history = jdbc.queryForList("SELECT version, checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        flyway(schema, null).migrate();
        assertThat(snapshot(jdbc)).isEqualTo(definitions);
        assertThat(translationSnapshot(jdbc)).isEqualTo(translations);
        assertThat(constraintSnapshot(jdbc, schema)).isEqualTo(constraints);
        assertThat(jdbc.queryForList("SELECT version, checksum FROM flyway_schema_history WHERE version <> '6' ORDER BY installed_rank"))
                .isEqualTo(history);
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint WHERE connamespace = ?::regnamespace
                  AND conname IN ('uk_qms_specification_revision_no', 'pk_qms_i18n_message_definition',
                    'uk_qms_i18n_message_definition_key', 'ck_qms_i18n_message_definition_key',
                    'ck_qms_i18n_message_definition_version')
                """, String.class, schema)).hasSize(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
                WHERE c.relnamespace = ?::regnamespace AND c.relname LIKE 'qms_%'
                  AND (a.attname = 'id' OR a.attname LIKE '%\\_id' ESCAPE '\\'
                       OR a.attname IN ('status', 'version'))
                  AND col_description(c.oid, a.attnum) IS NULL
                """, Integer.class, schema)).isZero();
        assertThat(jdbc.queryForObject("SELECT obj_description('qms_quality_specification'::regclass)", String.class))
                .startsWith("LEGACY READ-ONLY DATA:");
        assertThatThrownBy(() -> insert(jdbc, "201", "BAD KEY"))
                .isInstanceOf(DataIntegrityViolationException.class).isNotInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> insert(jdbc, "202", "qms.invalid_reference"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private List<String> constraintSnapshot(JdbcTemplate jdbc, String schema) {
        return jdbc.queryForList("""
                SELECT oid::text || ':' || conrelid::text || ':' || contype::text || ':' || convalidated::text
                       || ':' || pg_get_constraintdef(oid)
                FROM pg_constraint WHERE connamespace = ?::regnamespace ORDER BY oid
                """, String.class, schema);
    }

    /** 后段改名失败时，前面已经执行的改名也必须随 Flyway 事务回滚。 */
    @Test
    void failedMetadataRepairMustRollbackEarlierRenames() {
        String schema = schema();
        flyway(schema, "5").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        jdbc.execute("ALTER TABLE qms_i18n_message_definition DROP CONSTRAINT ck_qms_i18n_message_version");
        var constraints = constraintSnapshot(jdbc, schema);
        String namesSql = "SELECT oid, conname FROM pg_constraint WHERE connamespace = ?::regnamespace ORDER BY oid";
        var names = jdbc.queryForList(namesSql, schema);
        var definitions = snapshot(jdbc);
        var translations = translationSnapshot(jdbc);
        assertThatThrownBy(() -> flyway(schema, null).migrate()).isInstanceOf(FlywayException.class);
        assertThat(constraintSnapshot(jdbc, schema)).isEqualTo(constraints);
        assertThat(jdbc.queryForList(namesSql, schema)).isEqualTo(names);
        assertThat(snapshot(jdbc)).isEqualTo(definitions);
        assertThat(translationSnapshot(jdbc)).isEqualTo(translations);
        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("5");
    }

    private void assertNormalizationFailsWithoutChanges(String schema, String reason) {
        JdbcTemplate jdbc = jdbc(schema);
        List<String> before = snapshot(jdbc);
        List<String> translations = translationSnapshot(jdbc);
        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("QMS I18n normalization precheck failed: " + reason);
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertThat(translationSnapshot(jdbc)).isEqualTo(translations);
        assertThat(flyway(schema, null).info().current().getVersion().getVersion()).isEqualTo("4");
    }

    private String compliantV4Fixture() {
        String schema = schema();
        flyway(schema, "4").migrate();
        // 模拟已经人工修复的 V4 数据，用于验证合规升级及上一版 V5 已应用环境。
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
                SELECT to_jsonb(m)::text FROM qms_i18n_message_definition m ORDER BY id
                """, String.class);
    }

    private List<String> normalizedSnapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT (to_jsonb(m) || jsonb_build_object('message_key', lower(message_key COLLATE "C")))::text
                FROM qms_i18n_message_definition m ORDER BY id
                """, String.class);
    }

    private List<String> translationSnapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT to_jsonb(t)::text FROM qms_i18n_translation t ORDER BY id", String.class);
    }
}
