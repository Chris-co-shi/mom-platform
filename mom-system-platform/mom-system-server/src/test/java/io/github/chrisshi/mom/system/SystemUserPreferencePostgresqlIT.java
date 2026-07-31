package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.system.application.preference.CurrentPreferenceUserProvider;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ColumnCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.FilterCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ResetCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveDisplayPreferenceCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveViewSettingCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SortCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationService;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S16 Flyway V7、PostgreSQL 17.7、JSONB、审计、Version、Reset、并发创建和用户隔离集成测试。
 *
 * <p>测试使用真实 mom_system Schema、MyBatis-Plus 和事务；不依赖 Redis/Nacos/OTLP。V1～V6 升级测试
 * 继续验证旧数据在最新 V8 Schema 下保持不变；Docker 不可用时只能标记 Testcontainers skipped，不能描述为成功。</p>
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
@Import({SystemParameterPostgresqlIT.TestActorConfiguration.class,
        SystemUserPreferencePostgresqlIT.TestCurrentUserConfiguration.class})
class SystemUserPreferencePostgresqlIT {
    private static final String SCHEMA = "mom_system";
    private static final String UPGRADE_SCHEMA = "mom_system_preference_upgrade";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Flyway flyway;
    @Autowired
    private SystemUserPreferenceApplicationService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA + "&tcpKeepAlive=true&ApplicationName=mom-system-server");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @BeforeEach
    void cleanPreferenceTables() {
        jdbcTemplate.update("TRUNCATE TABLE system_user_view_setting, system_user_preference");
    }

    @AfterEach
    void cleanUpgradeSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + UPGRADE_SCHEMA + " CASCADE");
    }

    @Test
    void freshV1ThroughV7MustCreateTwoPreferenceTablesWithJsonbAndNoForeignKeys() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success and version between '1' and '7'",
                Long.class)).isEqualTo(7L);
        assertThat(jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                 where table_schema=? and table_name in ('system_user_preference','system_user_view_setting')
                 order by table_name
                """, String.class, SCHEMA)).containsExactly("system_user_preference", "system_user_view_setting");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema=? and table_name='system_user_view_setting'
                   and column_name in ('columns_json','sort_json','filters_json') and data_type='jsonb'
                """, Long.class, SCHEMA)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                 where table_schema=? and table_name in ('system_user_preference','system_user_view_setting')
                   and constraint_type='FOREIGN KEY'
                """, Long.class, SCHEMA)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema=? and table_name in ('system_user_preference','system_user_view_setting')
                   and column_name='id' and data_type='character varying' and character_maximum_length=19
                """, Long.class, SCHEMA)).isEqualTo(2L);
    }

    @Test
    void existingV1ThroughV6MustUpgradeToV8WithoutChangingExistingTables() {
        Flyway old = Flyway.configure().dataSource(dataSource).createSchemas(true)
                .schemas(UPGRADE_SCHEMA).defaultSchema(UPGRADE_SCHEMA)
                .locations("classpath:db/migration/system").target("6").load();
        old.migrate();
        jdbcTemplate.update("""
                insert into mom_system_preference_upgrade.system_parameter (
                    id, scope_type, scope_code, parameter_key, value_type, parameter_value,
                    enabled, version, created_by, created_at, updated_by, updated_at)
                values ('upgrade-param', 'GLOBAL', '', 'upgrade.key', 'STRING', 'kept',
                    true, 0, 'test', now(), 'test', now())
                """);

        Flyway latest = Flyway.configure().dataSource(dataSource).createSchemas(true)
                .schemas(UPGRADE_SCHEMA).defaultSchema(UPGRADE_SCHEMA)
                .locations("classpath:db/migration/system").load();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(jdbcTemplate.queryForObject(
                "select parameter_value from mom_system_preference_upgrade.system_parameter where id='upgrade-param'",
                String.class)).isEqualTo("kept");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema=? and table_name in ('system_user_preference','system_user_view_setting')
                """, Long.class, UPGRADE_SCHEMA)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema=? and table_name in (
                   'system_application','system_navigation_item','system_catalog_release')
                """, Long.class, UPGRADE_SCHEMA)).isEqualTo(3L);
    }

    @Test
    void databaseMustEnforceUniqueChecksAndJsonRoot() {
        insertPreference("1", "101", "zh-CN", 20, 0);
        assertThatThrownBy(() -> insertPreference("2", "101", "en-US", 20, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPreference("3", "102", "zh_CN", 20, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPreference("4", "103", "zh-CN", 25, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPreference("5", "104", "zh-CN", 20, -1))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertView("10", "101", "mom-admin", "iam.users.list", "[]", 0);
        assertThatThrownBy(() -> insertView("11", "101", "mom-admin", "iam.users.list", "[]", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertView("12", "102", "MomAdmin", "iam.users.list", "[]", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertView("13", "103", "mom-admin", "iam.users.list", "{}", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void applicationMustFillAuditIsolateUsersVersionAndResetWithoutDelete() {
        var created = service.saveMyPreference(
                new SaveDisplayPreferenceCommand("en-US", "Asia/Tokyo", "DARK", "COMPACT", 50, 0L));
        assertThat(created.persisted()).isTrue();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select updated_by from system_user_preference where user_id='101'", String.class))
                .isEqualTo("s13-test-actor");

        var updated = service.saveMyPreference(
                new SaveDisplayPreferenceCommand(null, null, null, null, null, 0L));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.locale()).isEqualTo("zh-CN");
        assertThatThrownBy(() -> service.saveMyPreference(
                new SaveDisplayPreferenceCommand("zh-CN", null, null, null, null, 0L)))
                .isInstanceOf(SystemUserPreferenceException.StaleVersion.class);

        var reset = service.resetMyPreference(new ResetCommand(1L));
        assertThat(reset.version()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from system_user_preference where user_id='101'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from system_user_preference where user_id='other-user'", Long.class)).isZero();
    }

    @Test
    void viewJsonVersionListAndResetMustRemainTypedAndUserScoped() {
        var created = service.saveMyView("mom-admin", "iam.users.list", viewCommand(0));
        assertThat(created.version()).isZero();
        assertThat(created.columns()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("""
                select jsonb_typeof(columns_json) from system_user_view_setting
                 where user_id='101' and application_code='mom-admin' and view_key='iam.users.list'
                """, String.class)).isEqualTo("array");
        assertThat(service.listMyViews("mom-admin")).hasSize(1);

        var reset = service.resetMyView("mom-admin", "iam.users.list", new ResetCommand(0L));
        assertThat(reset.enabled()).isFalse();
        assertThat(reset.version()).isEqualTo(1);
        assertThat(service.listMyViews("mom-admin")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from system_user_view_setting
                 where user_id='101' and enabled=false and columns_json='[]'::jsonb
                """, Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentFirstWriteMustYieldOneSuccessAndOneStaleVersion() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> call = () -> {
                try {
                    service.saveMyPreference(
                            new SaveDisplayPreferenceCommand("zh-CN", null, null, null, null, 0L));
                    return "success";
                } catch (SystemUserPreferenceException.StaleVersion exception) {
                    return "stale";
                }
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("success", "stale");
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from system_user_preference where user_id='101'", Long.class)).isEqualTo(1L);
    }

    private void insertPreference(String id, String userId, String locale, int pageSize, long version) {
        jdbcTemplate.update("""
                insert into system_user_preference (
                    id,user_id,locale,page_size,version,created_by,created_at,updated_by,updated_at)
                values (?,?,?,?,?,'test',now(),'test',now())
                """, id, userId, locale, pageSize, version);
    }

    private void insertView(
            String id, String userId, String applicationCode, String viewKey, String columnsJson, long version) {
        jdbcTemplate.update("""
                insert into system_user_view_setting (
                    id,user_id,application_code,view_key,schema_version,columns_json,sort_json,filters_json,
                    enabled,version,created_by,created_at,updated_by,updated_at)
                values (?,?,?,?,1,?::jsonb,'[]'::jsonb,'[]'::jsonb,true,?,'test',now(),'test',now())
                """, id, userId, applicationCode, viewKey, columnsJson, version);
    }

    private static SaveViewSettingCommand viewCommand(long version) {
        return new SaveViewSettingCommand(1,
                List.of(new ColumnCommand("display-name", true, 0, 200, "LEFT")),
                List.of(new SortCommand("display-name", "ASC", 0)),
                List.of(new FilterCommand("enabled", "EQ", "BOOLEAN", List.of("true"))),
                20, version);
    }

    /** PostgreSQL IT 的固定 JWT sub 替身；只覆盖当前用户 Port，不改变生产 SecurityFilterChain。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestCurrentUserConfiguration {
        @Bean
        @Primary
        CurrentPreferenceUserProvider testCurrentPreferenceUserProvider() {
            return () -> "101";
        }
    }
}
