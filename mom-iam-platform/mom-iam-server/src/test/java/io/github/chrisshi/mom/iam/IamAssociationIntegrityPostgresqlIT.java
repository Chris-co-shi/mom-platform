package io.github.chrisshi.mom.iam;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM V9 无物理外键与 V10/V11 System 治理 Permission Seed 的真实 PostgreSQL 集成测试。 */
@Testcontainers(disabledWithoutDocker = true)
class IamAssociationIntegrityPostgresqlIT {
    private static final String SCHEMA = "mom_iam";
    private static final String UPGRADE_SCHEMA = "mom_iam_upgrade";
    private static final String ORPHAN_SCHEMA = "mom_iam_orphan";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform").withUsername("mom").withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");

    private static JdbcTemplate jdbc;
    private static Flyway flyway;

    @BeforeAll
    static void migrateFreshDatabase() {
        var dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway = flyway(dataSource, SCHEMA, null);
        flyway.migrate();
    }

    @AfterAll
    static void cleanSchemas() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + ORPHAN_SCHEMA + " CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS " + UPGRADE_SCHEMA + " CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void freshMigrationMustRemoveBusinessForeignKeysAndSeedSystemGovernancePermissions() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_schema=? AND constraint_type='FOREIGN KEY'
                """, Long.class, SCHEMA)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                 WHERE schemaname=? AND indexname IN (
                   'idx_iam_user_role_user_status','idx_iam_user_role_role_status',
                   'idx_iam_role_permission_permission','idx_iam_user_application_user_status',
                   'idx_iam_user_factory_scope_user_status','idx_iam_user_session_user_status',
                   'idx_iam_user_session_client_status','uk_iam_refresh_token_one_active_per_session')
                """, Long.class, SCHEMA)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mom_iam.iam_permission
                 WHERE code IN ('system:catalog:read','system:catalog:write','system:catalog:publish')
                   AND status='ENABLED' AND built_in=true AND deleted=false
                """, Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                  FROM mom_iam.iam_role_permission assignment
                  JOIN mom_iam.iam_role role_row ON role_row.id=assignment.role_id
                  JOIN mom_iam.iam_permission permission_row ON permission_row.id=assignment.permission_id
                 WHERE role_row.code='PLATFORM_ADMIN'
                   AND permission_row.code IN (
                     'system:catalog:read','system:catalog:write','system:catalog:publish')
                """, Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mom_iam.iam_permission
                 WHERE (code, domain_code, resource_code, action_code, risk_level) IN (
                   ('system:i18n:read','system','i18n','read','LOW'),
                   ('system:i18n:write','system','i18n','write','MEDIUM'),
                   ('system:i18n:publish','system','i18n','publish','HIGH'))
                   AND status='ENABLED' AND built_in=true AND deleted=false
                """, Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                  FROM mom_iam.iam_role_permission assignment
                  JOIN mom_iam.iam_role role_row ON role_row.id=assignment.role_id
                  JOIN mom_iam.iam_permission permission_row ON permission_row.id=assignment.permission_id
                 WHERE role_row.code='PLATFORM_ADMIN'
                   AND permission_row.code IN (
                     'system:i18n:read','system:i18n:write','system:i18n:publish')
                """, Long.class)).isEqualTo(3L);
    }

    @Test
    void existingV10DatabaseMustUpgradeToV11() {
        var dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        Flyway v10 = flyway(dataSource, UPGRADE_SCHEMA, "10");
        v10.migrate();
        Flyway latest = flyway(dataSource, UPGRADE_SCHEMA, null);
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                  FROM mom_iam_upgrade.iam_role_permission assignment
                  JOIN mom_iam_upgrade.iam_role role_row ON role_row.id=assignment.role_id
                  JOIN mom_iam_upgrade.iam_permission permission_row ON permission_row.id=assignment.permission_id
                 WHERE role_row.code='PLATFORM_ADMIN'
                   AND permission_row.code IN (
                     'system:i18n:read','system:i18n:write','system:i18n:publish')
                """, Long.class)).isEqualTo(3L);
    }

    @Test
    void migrationMustFailClosedWhenHistoricalDataContainsOrphan() {
        var dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        Flyway v8 = flyway(dataSource, ORPHAN_SCHEMA, "8");
        v8.migrate();
        jdbc.execute("ALTER TABLE " + ORPHAN_SCHEMA
                + ".iam_internal_user_profile DROP CONSTRAINT fk_iam_internal_profile_user");
        jdbc.update("""
                INSERT INTO mom_iam_orphan.iam_internal_user_profile (
                    id,user_id,created_at,created_by,updated_at,updated_by,version)
                VALUES ('1','999',now(),'test',now(),'test',0)
                """);
        Flyway latest = flyway(dataSource, ORPHAN_SCHEMA, null);
        assertThatThrownBy(latest::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V9__remove_business_foreign_keys.sql");
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).createSchemas(true)
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration/iam");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
