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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V1-01 质量用途和发布范围快照在真实 PostgreSQL 上的升级兼容验证。 */
@Testcontainers
class MdmQualityV101MigrationPostgresqlIT {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"));

    @Test
    void oldReleasedRevisionMustSurviveUpgradeWithoutInventedScopeVersion() {
        flyway("quality_v101_upgrade", "132").migrate();
        JdbcTemplate jdbc = jdbc("quality_v101_upgrade");
        jdbc.update("""
                INSERT INTO mdm_quality_specification
                  (id,code,name,plant_id,material_id,inspection_usage,plant_version,
                   material_revision_no,material_version,created_at,created_by,updated_at,updated_by)
                VALUES ('1','OLD-IN','原来料标准','2','3','INCOMING',4,5,6,
                        CURRENT_TIMESTAMP,'TEST',CURRENT_TIMESTAMP,'TEST')
                """);
        jdbc.update("""
                INSERT INTO mdm_specification_revision
                  (id,specification_id,revision_no,status,lock_version,released_at,
                   created_at,created_by,updated_at,updated_by)
                VALUES ('10','1',1,'RELEASED',1,CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP,'TEST',CURRENT_TIMESTAMP,'TEST')
                """);
        var oldChecksums = jdbc.queryForList("""
                SELECT version, checksum FROM flyway_schema_history
                 WHERE version IS NOT NULL ORDER BY installed_rank
                """);

        flyway("quality_v101_upgrade", null).migrate();
        assertThat(jdbc.queryForList("""
                SELECT version, checksum FROM flyway_schema_history
                 WHERE version::integer <= 132 ORDER BY installed_rank
                """)).isEqualTo(oldChecksums);
        assertThat(jdbc.queryForObject("SELECT plant_version FROM mdm_specification_revision WHERE id='10'", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT inspection_usage FROM mdm_quality_specification WHERE id='1'", String.class))
                .isEqualTo("INCOMING");
        jdbc.update("""
                UPDATE mdm_quality_specification SET inspection_usage='FINISHED_GOODS' WHERE id='1'
                """);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mdm_i18n_message_definition
                 WHERE message_key IN ('mdm.quality.spec.v101description',
                                       'mdm.quality.spec.usage.incoming',
                                       'mdm.quality.spec.usage.finished_goods')
                """, Long.class)).isEqualTo(3L);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE mdm_quality_specification SET inspection_usage='OTHER' WHERE id='1'
                """)).hasMessageContaining("ck_mdm_quality_specification_usage");
        flyway("quality_v101_upgrade", null).validate();
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
