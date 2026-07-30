package io.github.chrisshi.mom.system;

import com.zaxxer.hikari.HikariDataSource;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.CreateCommand;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageQuery;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.StatusCommand;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.UpdateCommand;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationService;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System Parameter 的真实 PostgreSQL、Flyway V1～V4、MyBatis-Plus 与并发集成测试。
 *
 * <p>测试固定 PostgreSQL 17.7 官方镜像、动态端口和容器 Wait Strategy，不使用本机数据库或 H2。每个
 * 测试前清理参数表；验证独立 mom_system Schema、BaseEntity 列、约束、审计、String ID、乐观锁、分页及
 * 无跨 Schema FK。Docker 不可用时由 Testcontainers 明确标记跳过，不能描述为专项成功。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomSystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=false"
        })
@Import(SystemParameterPostgresqlIT.TestActorConfiguration.class)
class SystemParameterPostgresqlIT {
    private static final String SCHEMA = "mom_system";
    private static final String APPLICATION_NAME = "mom-system-server";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Flyway flyway;
    @Autowired
    private SystemParameterApplicationService service;

    /** 注入动态端口并保留生产 PgJDBC 治理参数。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true"
                + "&ApplicationName=" + APPLICATION_NAME);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("TRUNCATE TABLE system_parameter");
    }

    @Test
    void dataSourceAndFlywayMustUseIndependentSystemSchema() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).hasSize(1);
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getPoolName()).isEqualTo("mom-system-hikari");
        assertThat(hikari.getMinimumIdle()).isEqualTo(1);
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(3000L);
        assertThat(hikari.getValidationTimeout()).isEqualTo(2000L);
        assertThat(hikari.getConnectionInitSql()).isEqualTo("SET TIME ZONE 'UTC'");
        assertThat(jdbcTemplate.queryForObject("select current_schema()", String.class)).isEqualTo(SCHEMA);
        assertThat(jdbcTemplate.queryForObject("show timezone", String.class)).isEqualTo("UTC");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success and version in ('1','2','3','4')",
                Long.class)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_schema=? and table_name='system_parameter' and column_name='id'",
                Integer.class, SCHEMA)).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema=? and table_name='system_parameter'
                   and column_name='deleted' and data_type='boolean' and is_nullable='NO'
                """, Long.class, SCHEMA)).isEqualTo(1L);
    }

    @Test
    void databaseMustEnforceScopeValueTypeAndUniqueConstraints() {
        assertInvalidRow("bad-global", "GLOBAL", "mom-web", "feature.a", "STRING", "value");
        assertInvalidRow("bad-application", "APPLICATION", "", "feature.b", "STRING", "value");
        assertInvalidRow("bad-type", "GLOBAL", "", "feature.c", "SECRET", "value");
        assertInvalidRow("bad-value", "GLOBAL", "", "feature.d", "STRING", "");

        insertRaw("unique-1", "GLOBAL", "", "feature.unique", "INTEGER", "1");
        assertThatThrownBy(() -> insertRaw(
                "unique-2", "GLOBAL", "", "feature.unique", "INTEGER", "2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mybatisPlusMustFillAuditGenerateStringIdAndResolveOverrides() {
        var global = service.create(command(ParameterScopeType.GLOBAL, null, "feature.timeout", "00012"));
        var override = service.create(command(ParameterScopeType.APPLICATION, "MOM-WEB", "feature.timeout", "20"));

        assertThat(global.id()).matches("[0-9]{1,19}");
        assertThat(global.createdBy()).isEqualTo("s13-test-actor");
        assertThat(global.updatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select deleted from system_parameter where id=?", Boolean.class, global.id())).isFalse();
        assertThat(service.resolve("feature.timeout", "mom-web").parameterValue()).isEqualTo("20");

        var disabled = service.changeStatus(override.id(), new StatusCommand(false, override.version()));
        assertThat(disabled.version()).isEqualTo(1L);
        assertThat(service.resolve("feature.timeout", "mom-web").parameterValue()).isEqualTo("12");
    }

    @Test
    void optimisticLockAndParameterizedPageMustWork() {
        var first = service.create(command(ParameterScopeType.GLOBAL, null, "feature.a", "1"));
        service.create(command(ParameterScopeType.GLOBAL, null, "feature.b", "2"));
        var updated = service.update(first.id(),
                new UpdateCommand(first.version(), ParameterValueType.INTEGER, "0003", "changed"));
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.parameterValue()).isEqualTo("3");
        assertThatThrownBy(() -> service.update(first.id(),
                new UpdateCommand(first.version(), ParameterValueType.INTEGER, "4", null)))
                .isInstanceOf(SystemParameterException.StaleVersion.class);

        var page0 = service.page(new PageQuery(ParameterScopeType.GLOBAL, null, null, true, 0, 1));
        var page1 = service.page(new PageQuery(ParameterScopeType.GLOBAL, null, null, true, 1, 1));
        assertThat(page0.total()).isEqualTo(2L);
        assertThat(page0.items()).hasSize(1);
        assertThat(page1.items()).hasSize(1);
        assertThat(page0.items().getFirst().parameterKey()).isEqualTo("feature.a");
        assertThat(page1.items().getFirst().parameterKey()).isEqualTo("feature.b");
    }

    @Test
    void systemSchemaMustNotHaveCrossSchemaForeignKeys() {
        Long crossSchemaForeignKeys = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name
                   AND ccu.constraint_schema = tc.constraint_schema
                 WHERE tc.table_schema = ?
                   AND tc.constraint_type = 'FOREIGN KEY'
                   AND ccu.table_schema <> ?
                """, Long.class, SCHEMA, SCHEMA);
        assertThat(crossSchemaForeignKeys).isZero();
    }

    private void assertInvalidRow(
            String id, String scopeType, String scopeCode, String key, String valueType, String value) {
        assertThatThrownBy(() -> insertRaw(id, scopeType, scopeCode, key, valueType, value))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRaw(
            String id, String scopeType, String scopeCode, String key, String valueType, String value) {
        jdbcTemplate.update("""
                INSERT INTO system_parameter (
                    id, scope_type, scope_code, parameter_key, value_type, parameter_value,
                    enabled, version, created_by, created_at, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, true, 0, 'test', now(), 'test', now())
                """, id, scopeType, scopeCode, key, valueType, value);
    }

    private static CreateCommand command(
            ParameterScopeType scopeType, String scopeCode, String key, String value) {
        return new CreateCommand(scopeType, scopeCode, key, ParameterValueType.INTEGER, value, null, true);
    }

    /** 测试环境提供稳定认证 Actor，生产仍由 SecurityCurrentActorProvider 解析 JWT。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestActorConfiguration {
        @Bean
        CurrentActorProvider testCurrentActorProvider() {
            return () -> Optional.of(new AuditActor(
                    "s13-test-actor", ActorType.USER, "INTERNAL", "mom-admin-web",
                    "s13-session", "s13-correlation"));
        }
    }
}
