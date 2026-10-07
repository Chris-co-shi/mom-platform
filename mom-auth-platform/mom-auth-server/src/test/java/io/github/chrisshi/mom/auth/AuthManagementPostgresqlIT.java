package io.github.chrisshi.mom.auth;

import io.github.chrisshi.mom.auth.application.model.AuthPageParams.UserPageParams;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionPageParams;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionResourcePageParams;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import io.github.chrisshi.mom.auth.application.PermissionApplication;
import io.github.chrisshi.mom.auth.application.PermissionResourceApplication;
import io.github.chrisshi.mom.auth.application.RoleApplication;
import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import org.apache.ibatis.exceptions.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mini Auth 账号与 RBAC 管理的真实 PostgreSQL 集成验收。
 *
 * <p>该测试使用隔离的 PostgreSQL 17 容器执行真实 Flyway、MyBatis-Plus、审计填充、
 * 乐观锁和本地事务。它不启动 Redis/Nacos，不验证 Token 协议；Docker 不可用时由
 * Testcontainers 显式跳过，不得将跳过描述为通过。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = AuthApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.main.banner-mode=off",
        "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=false"
    }
)
@Import(AuthManagementPostgresqlIT.TestActorConfiguration.class)
class AuthManagementPostgresqlIT {

    private static final String SCHEMA = "mom_auth";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
        DockerImageName.parse("postgres:17.7-alpine")
    )
        .withDatabaseName("mom_platform")
        .withUsername("mom")
        .withPassword("mom")
        .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private UserApplication userApplication;
    @Autowired
    private RoleApplication roleApplication;
    @Autowired
    private PermissionApplication permissionApplication;
    @Autowired
    private PermissionResourceApplication resourceApplication;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Flyway flyway;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /** 通过真实 Spring 事务代理验证自助写入、版本推进、失败不改库及管理员重置冲突。 */
    @Test
    void selfServiceMustPersistOnlyAllowedFieldsAndRejectStaleCredentials() {
        var user = userApplication.create("self.service", " OldPassword@123 ", "Before", true);
        var other = userApplication.create("other.user", "OtherPassword@123", "Other", true);
        var profile = userApplication.updateOwnProfile(user.id(), " After ", user.version());
        assertThat(profile.version()).isEqualTo(user.version() + 1);
        assertThat(profile.displayName()).isEqualTo("After");
        assertThat(profile.username()).isEqualTo("self.service");
        assertThat(userApplication.get(other.id())).isEqualTo(other);
        assertError(() -> userApplication.changeOwnPassword(user.id(), "wrong", "NewPassword@123", profile.version()),
            AuthErrorCode.CURRENT_PASSWORD_INVALID);
        assertThat(userApplication.get(user.id()).version()).isEqualTo(profile.version());
        var changed = userApplication.changeOwnPassword(user.id(), " OldPassword@123 ", " NewPassword@123 ", profile.version());
        assertThat(changed.version()).isEqualTo(profile.version() + 1);
        String hash = jdbcTemplate.queryForObject("select password_hash from auth_user where id=?", String.class, user.id());
        assertThat(passwordEncoder.matches(" NewPassword@123 ", hash)).isTrue();
        assertThat(passwordEncoder.matches(" OldPassword@123 ", hash)).isFalse();
        var reset = userApplication.resetPassword(user.id(), "AdminReset@123", changed.version());
        assertError(() -> userApplication.changeOwnPassword(user.id(), " NewPassword@123 ", "StalePassword@123", changed.version()),
            AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        assertThat(userApplication.get(user.id()).version()).isEqualTo(reset.version());
        assertError(() -> userApplication.updateOwnProfile(user.id(), "Stale", profile.version()),
            AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        assertThat(userApplication.get(user.id()).displayName()).isEqualTo("After");
    }

    /** 为隔离 PostgreSQL 容器注入动态连接参数并保持生产 PgJDBC 治理项。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
            + "&currentSchema=" + SCHEMA
            + "&tcpKeepAlive=true"
            + "&ApplicationName=mom-auth-server-it");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("""
            TRUNCATE TABLE auth_user_role, auth_role_permission,
                           auth_user, auth_role, auth_permission, auth_permission_resource
            """);
    }

    @Test
    void flywayAndDatabaseConstraintsMustMatchMiniAuthModel() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
        assertThat(jdbcTemplate.queryForObject("select current_schema()", String.class)).isEqualTo(SCHEMA);
        assertThat(jdbcTemplate.queryForObject("show timezone", String.class)).isEqualTo("UTC");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT count(*)
              FROM information_schema.table_constraints
            WHERE table_schema = ? AND constraint_type = 'FOREIGN KEY'
            """, Long.class, SCHEMA)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT count(*)
              FROM information_schema.table_constraints
             WHERE table_schema = ?
               AND constraint_type = 'CHECK'
               AND constraint_name IN (
                   'ck_auth_user_version_non_negative',
                   'ck_auth_role_version_non_negative',
                   'ck_auth_permission_version_non_negative',
                   'ck_auth_permission_resource_version_non_negative'
               )
            """, Long.class, SCHEMA)).isEqualTo(4L);
    }

    /** 在隔离 Schema 上真实先执行 V1～V4，再升级 V5，验证历史六条授权及角色关系完整保留。 */
    @Test
    void v5MustUpgradeExistingSixPermissionsWithoutChangingRoleGrants() {
        String upgradeSchema = "mom_auth_upgrade";
        Flyway before = Flyway.configure()
            .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
            .schemas(upgradeSchema)
            .defaultSchema(upgradeSchema)
            .locations("classpath:db/migration/auth")
            .target("4")
            .load();
        before.migrate();
        long oldCount = jdbcTemplate.queryForObject(
            "select count(*) from mom_auth_upgrade.auth_permission", Long.class);
        long oldRelations = jdbcTemplate.queryForObject(
            "select count(*) from mom_auth_upgrade.auth_role_permission", Long.class);
        Flyway after = Flyway.configure()
            .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
            .schemas(upgradeSchema)
            .defaultSchema(upgradeSchema)
            .locations("classpath:db/migration/auth")
            .target("5")
            .load();
        after.migrate();
        assertThat(after.info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from mom_auth_upgrade.auth_permission", Long.class)).isEqualTo(oldCount);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from mom_auth_upgrade.auth_role_permission", Long.class)).isEqualTo(oldRelations);
        assertThat(jdbcTemplate.queryForObject("""
            select count(*) from mom_auth_upgrade.auth_permission p
            join mom_auth_upgrade.auth_permission_resource r on r.id = p.resource_id
            where p.code = lower(r.domain_code || ':' || r.resource_code || ':' || p.action_code)
            """, Long.class)).isEqualTo(6L);
    }

    /** 现行迁移在隔离 Schema 中保留四个 System authority，并增加 Owner 文案权限。 */
    @Test
    void v6MustSeedSystemPermissionsUnderExplicitResources() {
        String seedSchema = "mom_auth_system_seed";
        Flyway seeded = Flyway.configure()
            .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
            .schemas(seedSchema)
            .defaultSchema(seedSchema)
            .locations("classpath:db/migration/auth")
            .load();
        seeded.migrate();
        assertThat(seeded.info().current().getVersion().getVersion()).isEqualTo("10");
        assertThat(jdbcTemplate.queryForList("""
            SELECT permission.code
              FROM mom_auth_system_seed.auth_permission permission
              JOIN mom_auth_system_seed.auth_permission_resource resource
                ON resource.id = permission.resource_id
             WHERE resource.domain_code = 'SYSTEM'
               AND permission.code = lower(resource.domain_code || ':' || resource.resource_code || ':' || permission.action_code)
               AND permission.enabled = true AND permission.deleted = false
             ORDER BY permission.code
            """, String.class)).containsExactly(
            "system:dictionary:read", "system:dictionary:write",
            "system:i18n:read", "system:i18n:write"
        );
    }

    /** Auth Shell 导航独立于 IAM 正文，六个稳定 Key 均有双语译文。 */
    @Test
    void authNavigationMigrationMustSeedBilingualShellKeys() {
        assertThat(jdbcTemplate.queryForObject("""
            select count(*) from auth_i18n_message_definition
            where namespace = 'auth.navigation' and message_key like 'navigation.%'
              and enabled = true and deleted = false
            """, Long.class)).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject("""
            select count(*) from auth_i18n_translation t
              join auth_i18n_message_definition m on m.id = t.message_id
            where m.namespace = 'auth.navigation' and m.message_key like 'navigation.%'
              and t.locale_code in ('zh-CN', 'en-US') and not t.deleted
            """, Long.class)).isEqualTo(12L);
    }

    @Test
    void userLifecycleMustEncodePasswordProtectVersionAndReferences() {
        var user = userApplication.create(" Test.User ", "Password@123", " Test User ", true);
        assertThat(user.username()).isEqualTo("test.user");
        assertThat(userApplication.get(user.id()).displayName()).isEqualTo("Test User");
        assertThat(userApplication.list(new PageQuery<>(new UserPageParams(), 1, 20)).records())
            .extracting("id").contains(user.id());

        String passwordHash = jdbcTemplate.queryForObject(
            "select password_hash from auth_user where id=?", String.class, user.id());
        assertThat(passwordHash).startsWith("{bcrypt}$2").doesNotContain("Password@123");
        assertError(() -> userApplication.create("TEST.USER", "Password@123", "Duplicate", true),
            AuthErrorCode.USERNAME_CONFLICT);

        var updated = userApplication.update(user.id(), "Updated", true, user.version());
        assertThat(updated.version()).isEqualTo(1L);
        assertError(() -> userApplication.update(user.id(), "Stale", true, user.version()),
            AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);

        var passwordReset = userApplication.resetPassword(
            user.id(), "NewPassword@123", updated.version());
        var disabled = userApplication.disable(user.id(), passwordReset.version());
        assertThat(disabled.enabled()).isFalse();
        var enabled = userApplication.enable(user.id(), disabled.version());
        assertThat(enabled.enabled()).isTrue();
        assertThat(userApplication.enable(user.id(), enabled.version()).version()).isEqualTo(enabled.version());

        var role = roleApplication.create("OPERATOR", "Operator", null, true);
        userApplication.replaceRoles(user.id(), List.of(role.id()));
        assertError(() -> userApplication.delete(user.id()), AuthErrorCode.RESOURCE_REFERENCED);
        userApplication.replaceRoles(user.id(), List.of());
        userApplication.delete(user.id());
        assertError(() -> userApplication.get(user.id()), AuthErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void roleAndPermissionLifecycleMustRejectDisabledAssignmentsAndProtectReferences() {
        var user = userApplication.create("operator", "Password@123", "Operator", true);
        var disabledRole = roleApplication.create("OPERATOR", "Operator", null, false);
        assertError(() -> userApplication.replaceRoles(user.id(), List.of(disabledRole.id())),
            AuthErrorCode.ROLE_DISABLED);

        var enabledRole = roleApplication.enable(disabledRole.id(), disabledRole.version());
        assertThat(userApplication.replaceRoles(user.id(), List.of(enabledRole.id(), enabledRole.id())))
            .extracting("id").containsExactly(enabledRole.id());
        assertError(() -> roleApplication.delete(enabledRole.id()), AuthErrorCode.RESOURCE_REFERENCED);

        var testResource = resourceApplication.create("AUTH", "TEST", "测试资源", null, 10, true);
        var disabledPermission = permissionApplication.create(testResource.id(), "READ", "Test Read", null, false);
        assertError(() -> roleApplication.replacePermissions(
                enabledRole.id(), List.of(disabledPermission.id())),
            AuthErrorCode.PERMISSION_DISABLED);

        var enabledPermission = permissionApplication.enable(
            disabledPermission.id(), disabledPermission.version());
        assertThat(roleApplication.replacePermissions(
            enabledRole.id(), List.of(enabledPermission.id(), enabledPermission.id())))
            .extracting("id").containsExactly(enabledPermission.id());
        assertError(() -> permissionApplication.delete(enabledPermission.id()), AuthErrorCode.RESOURCE_REFERENCED);
        assertError(() -> roleApplication.delete(enabledRole.id()), AuthErrorCode.RESOURCE_REFERENCED);

        roleApplication.replacePermissions(enabledRole.id(), List.of());
        userApplication.replaceRoles(user.id(), List.of());
        permissionApplication.delete(enabledPermission.id());
        roleApplication.delete(enabledRole.id());
        assertError(() -> permissionApplication.get(enabledPermission.id()), AuthErrorCode.RESOURCE_NOT_FOUND);
        assertError(() -> roleApplication.get(enabledRole.id()), AuthErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void replaceRolesMustRollbackDeleteWhenRelationInsertFails() {
        var user = userApplication.create("rollback", "Password@123", "Rollback", true);
        var original = roleApplication.create("ORIGINAL", "Original", null, true);
        var rejected = roleApplication.create("REJECTED", "Rejected", null, true);
        userApplication.replaceRoles(user.id(), List.of(original.id()));

        jdbcTemplate.execute("ALTER TABLE auth_user_role ADD CONSTRAINT ck_test_rejected_role "
            + "CHECK (role_id <> '" + rejected.id() + "')");
        try {
            assertThatThrownBy(() -> userApplication.replaceRoles(user.id(), List.of(rejected.id())))
                .isInstanceOf(PersistenceException.class);
            assertThat(userApplication.roles(user.id())).extracting("id").containsExactly(original.id());
        } finally {
            jdbcTemplate.execute("ALTER TABLE auth_user_role DROP CONSTRAINT ck_test_rejected_role");
        }
    }

    @Test
    void replacePermissionsMustRollbackDeleteWhenRelationInsertFails() {
        var role = roleApplication.create("ROLLBACK", "Rollback", null, true);
        var originalResource = resourceApplication.create("AUTH", "ORIGINAL", "Original", null, 10, true);
        var rejectedResource = resourceApplication.create("AUTH", "REJECTED", "Rejected", null, 20, true);
        var original = permissionApplication.create(originalResource.id(), "READ", "Original", null, true);
        var rejected = permissionApplication.create(rejectedResource.id(), "READ", "Rejected", null, true);
        roleApplication.replacePermissions(role.id(), List.of(original.id()));

        jdbcTemplate.execute("ALTER TABLE auth_role_permission ADD CONSTRAINT ck_test_rejected_permission "
            + "CHECK (permission_id <> '" + rejected.id() + "')");
        try {
            assertThatThrownBy(() -> roleApplication.replacePermissions(role.id(), List.of(rejected.id())))
                .isInstanceOf(PersistenceException.class);
            assertThat(roleApplication.permissions(role.id())).extracting("id").containsExactly(original.id());
        } finally {
            jdbcTemplate.execute("ALTER TABLE auth_role_permission DROP CONSTRAINT ck_test_rejected_permission");
        }
    }

    /** 真实 PostgreSQL 验证资源归属、服务端 JOIN 筛选、稳定编码及停用/删除保护。 */
    @Test
    void permissionResourceCatalogMustFilterAcrossPagesAndProtectLifecycle() {
        var resource = resourceApplication.create(" auth ", " user ", "用户管理", "授权目录", 10, true);
        var other = resourceApplication.create("MDM", "MATERIAL", "物料主数据", null, 20, true);
        assertError(() -> resourceApplication.create("AUTH", "USER", "重复", null, 30, true),
            AuthErrorCode.RESOURCE_CODE_CONFLICT);

        var read = permissionApplication.create(resource.id(), " READ ", "用户读取", "可读取用户", true);
        var write = permissionApplication.create(resource.id(), "WRITE", "用户维护", null, true);
        permissionApplication.create(other.id(), "READ", "物料读取", null, true);
        assertThat(read.code()).isEqualTo("auth:user:read");
        assertThat(read.resourceName()).isEqualTo("用户管理");
        assertError(() -> permissionApplication.create(resource.id(), "read", "重复", null, true),
            AuthErrorCode.PERMISSION_CODE_CONFLICT);

        var byResource = permissionApplication.list(new PageQuery<>(
            new PermissionPageParams(null, resource.id(), null, null), 1, 1));
        assertThat(byResource.total()).isEqualTo(2);
        assertThat(byResource.records()).extracting("code").containsExactly("auth:user:read");
        assertThat(permissionApplication.list(new PageQuery<>(
            new PermissionPageParams(null, resource.id(), null, null), 2, 1)).records())
            .extracting("code").containsExactly("auth:user:write");
        assertThat(permissionApplication.list(new PageQuery<>(
            new PermissionPageParams("AUTH", null, "用户管理", true), 1, 20)).records())
            .extracting("id").containsExactly(read.id(), write.id());
        assertThat(permissionApplication.list(new PageQuery<>(
            new PermissionPageParams(null, null, "物料主数据", null), 1, 20)).total()).isEqualTo(1);
        assertThat(resourceApplication.list(new PageQuery<>(
            new PermissionResourcePageParams("MDM", "物料", true), 1, 20)).records())
            .extracting("id").containsExactly(other.id());

        var disabled = resourceApplication.disable(resource.id(), resource.version());
        assertError(() -> permissionApplication.create(resource.id(), "EXPORT", "导出", null, true),
            AuthErrorCode.RESOURCE_DISABLED);
        var stopped = permissionApplication.disable(read.id(), read.version());
        assertError(() -> permissionApplication.enable(read.id(), stopped.version()),
            AuthErrorCode.RESOURCE_DISABLED);
        assertThat(permissionApplication.get(write.id()).enabled()).isTrue();
        assertError(() -> resourceApplication.delete(resource.id()), AuthErrorCode.RESOURCE_REFERENCED);
        assertThat(resourceApplication.enable(resource.id(), disabled.version()).enabled()).isTrue();
    }

    private static void assertError(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(AuthException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    /** 集成测试为审计字段提供稳定 Actor，生产环境仍从已认证 SecurityContext 解析。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestActorConfiguration {

        @Bean
        CurrentActorProvider testCurrentActorProvider() {
            return () -> Optional.of(new AuditActor(
                "auth-it-actor", ActorType.USER
            ));
        }
    }
}
