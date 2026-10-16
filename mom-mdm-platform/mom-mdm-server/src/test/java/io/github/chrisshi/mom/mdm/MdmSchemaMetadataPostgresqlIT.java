package io.github.chrisshi.mom.mdm;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 V128 升级补注释时保留制造定义数据和已发布迁移校验和。 */
@Testcontainers
class MdmSchemaMetadataPostgresqlIT {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"));

    @Test
    void metadataUpgradeMustPreserveDataAndMatchFreshSchemaComments() {
        flyway("upgrade_metadata", "128").migrate();
        JdbcTemplate jdbc = jdbc("upgrade_metadata");
        jdbc.update("""
                INSERT INTO mdm_formula(id, code, name, plant_id, output_material_id,
                  base_batch_quantity, base_uom_id, created_at, created_by, updated_at, updated_by)
                VALUES ('1', 'UPGRADE', '升级保留数据', '2', '3', 12.500, '4',
                  CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST')
                """);
        var before = jdbc.queryForList("SELECT to_jsonb(f)::text FROM mdm_formula f", String.class);
        var history = jdbc.queryForList("SELECT version, checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var constraints = jdbc.queryForList("""
                SELECT oid, conname, pg_get_constraintdef(oid) FROM pg_constraint
                 WHERE connamespace = 'upgrade_metadata'::regnamespace ORDER BY oid
                """);
        flyway("upgrade_metadata", null).migrate();
        assertThat(jdbc.queryForList("SELECT to_jsonb(f)::text FROM mdm_formula f", String.class)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT version, checksum FROM flyway_schema_history WHERE version <> '129' ORDER BY installed_rank"))
                .isEqualTo(history);
        assertThat(jdbc.queryForList("""
                SELECT oid, conname, pg_get_constraintdef(oid) FROM pg_constraint
                 WHERE connamespace = 'upgrade_metadata'::regnamespace ORDER BY oid
                """)).isEqualTo(constraints);
        assertThat(flyway("upgrade_metadata", null).info().current().getVersion().getVersion()).isEqualTo("129");
        flyway("fresh_metadata", null).migrate();
        var comments = comments(jdbc);
        assertThat(comments).hasSize(25).allSatisfy(row -> assertThat(row.get("description")).isNotNull());
        assertThat(comments(jdbc("fresh_metadata"))).isEqualTo(comments);
    }

    private java.util.List<java.util.Map<String, Object>> comments(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT c.relname, a.attname, col_description(c.oid, a.attnum) AS description
                  FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
                 WHERE c.relnamespace = current_schema()::regnamespace
                   AND c.relname IN ('mdm_formula', 'mdm_formula_revision', 'mdm_formula_item',
                     'mdm_routing', 'mdm_routing_revision', 'mdm_routing_step', 'mdm_production_version')
                   AND (a.attname = 'id' OR a.attname LIKE '%\\_id' ESCAPE '\\' OR a.attname = 'status')
                 ORDER BY c.relname, a.attname
                """);
    }

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration/mdm");
        if (target != null) config.target(target);
        return config.load();
    }

    private JdbcTemplate jdbc(String schema) {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRESQL.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));
    }
}
