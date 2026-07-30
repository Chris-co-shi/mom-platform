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

/**
 * IAM V9 无物理外键与迁移孤儿保护的真实 PostgreSQL 集成测试。
 *
 * <p>测试只验证 IAM 自有 Schema 的 Flyway DDL、索引和孤儿阻断，不启动 OAuth2/Redis/Nacos。V9 在删除
 * 已发布 V1～V3 的业务外键前检查全部本地关系；发现孤儿时迁移 fail closed，合法空库则升级到 V9。
 * Docker 不可用时 Testcontainers 明确跳过，不能描述为专项成功。</p>
 */
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

    /** 初始化独立数据源并从空库迁移完整 IAM Schema。 */
    @BeforeAll
    static void migrateFreshDatabase() {
        var dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway = flyway(dataSource, SCHEMA, null);
        flyway.migrate();
    }

    /** 清理测试创建的三个独立 Schema。 */
    @AfterAll
    static void cleanSchemas() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + ORPHAN_SCHEMA + " CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS " + UPGRADE_SCHEMA + " CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    /** 空库必须完整迁移到 V9，业务 FK 为零且原查询索引保留。 */
    @Test
    void freshMigrationMustRemoveBusinessForeignKeysAndKeepIndexes() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");
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
                SELECT
                  (SELECT count(*) FROM mom_iam.iam_user_role child LEFT JOIN mom_iam.iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL)
                  + (SELECT count(*) FROM mom_iam.iam_role_permission child LEFT JOIN mom_iam.iam_permission parent ON parent.id=child.permission_id WHERE parent.id IS NULL)
                  + (SELECT count(*) FROM mom_iam.iam_refresh_token child LEFT JOIN mom_iam.iam_user_session parent ON parent.id=child.session_id WHERE parent.id IS NULL)
                """, Long.class)).isZero();
    }

    /** 已发布 V1～V8 的合法数据库必须通过新增 V9 向前升级。 */
    @Test
    void existingV8DatabaseMustUpgradeToV9() {
        var dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        Flyway v8 = flyway(dataSource, UPGRADE_SCHEMA, "8");
        v8.migrate();
        Flyway latest = flyway(dataSource, UPGRADE_SCHEMA, null);
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("9");
    }

    /** V9 必须在删除约束前拒绝已经存在的孤儿，而不是把问题静默带入无 FK 结构。 */
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
