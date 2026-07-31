package io.github.chrisshi.mom.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V8 Catalog 三表、零 FK、唯一性、JSONB 与不可变 Release 的真实 PostgreSQL 测试。 */
@Testcontainers(disabledWithoutDocker = true)
class SystemCatalogPostgresqlIT {
    private static final String SCHEMA = "mom_system_catalog";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform").withUsername("mom").withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");

    private static JdbcTemplate jdbc;
    private static Flyway flyway;

    @BeforeAll
    static void migrate() {
        var dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(dataSource).createSchemas(true)
                .schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration/system").load();
        flyway.migrate();
    }

    @AfterAll
    static void clean() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void migrationMustCreateThreeCatalogTablesWithoutForeignKeys() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema=? AND table_name IN (
                   'system_application','system_navigation_item','system_catalog_release')
                """, Long.class, SCHEMA)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_schema=? AND constraint_type='FOREIGN KEY'
                """, Long.class, SCHEMA)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema=? AND table_name='system_catalog_release'
                   AND column_name='snapshot_json' AND data_type='jsonb'
                """, Long.class, SCHEMA)).isEqualTo(1L);
    }

    @Test
    void routeKeyMustBeUniqueInsideApplicationAndChannel() {
        insertApplication();
        insertNavigation("1", "iam.users");
        assertThatThrownBy(() -> insertNavigation("2", "iam.users"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void releaseMustBeAppendOnlyAndImmutable() {
        insertApplication();
        jdbc.update("""
                INSERT INTO mom_system_catalog.system_catalog_release (
                  id,application_id,application_code,release_version,snapshot_schema_version,
                  route_contract_version,source_application_version,source_release_version,
                  snapshot_json,node_count,checksum,change_note,
                  created_by,created_at,updated_by,updated_at)
                VALUES ('10','app','iam',1,1,1,0,NULL,
                  '{"snapshotSchemaVersion":1}'::jsonb,0,?, 'initial',
                  'actor',now(),'actor',now())
                """, "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE mom_system_catalog.system_catalog_release
                   SET change_note='changed' WHERE id='10'
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM mom_system_catalog.system_catalog_release WHERE id='10'
                """)).isInstanceOf(DataAccessException.class);
    }

    private static void insertApplication() {
        jdbc.update("""
                INSERT INTO mom_system_catalog.system_application (
                  id,application_code,application_type,i18n_resource_code,i18n_message_key,
                  route_contract_version,sort_order,enabled,published_version,version,
                  created_by,created_at,updated_by,updated_at)
                VALUES ('app','iam','PLATFORM','mom-web','mom.menu.iam',1,10,true,0,0,
                  'actor',now(),'actor',now())
                ON CONFLICT (id) DO NOTHING
                """);
    }

    private static void insertNavigation(String id, String routeKey) {
        jdbc.update("""
                INSERT INTO mom_system_catalog.system_navigation_item (
                  id,application_id,parent_id,client_channel,navigation_type,route_key,
                  i18n_resource_code,i18n_message_key,permission_code,visible_in_menu,
                  visible_in_breadcrumb,visible_in_tab,keep_alive,sort_order,enabled,version,
                  created_by,created_at,updated_by,updated_at)
                VALUES (?, 'app', NULL, 'WEB', 'ROUTE', ?, 'mom-web','mom.menu.users',
                  'iam:user:read',true,true,true,false,10,true,0,'actor',now(),'actor',now())
                """, id, routeKey);
    }
}
