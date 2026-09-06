package io.github.chrisshi.mom.auth;

import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import io.github.chrisshi.mom.auth.application.PermissionApplication;
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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Flyway flyway;

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
                           auth_user, auth_role, auth_permission
            """);
    }

    @Test
    void flywayAndDatabaseConstraintsMustMatchMiniAuthModel() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
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
                   'ck_auth_permission_version_non_negative'
               )
            """, Long.class, SCHEMA)).isEqualTo(3L);
    }

    @Test
    void userLifecycleMustEncodePasswordProtectVersionAndReferences() {
        var user = userApplication.create(" Test.User ", "Password@123", " Test User ", true);
        assertThat(user.username()).isEqualTo("test.user");
        assertThat(userApplication.get(user.id()).displayName()).isEqualTo("Test User");
        assertThat(userApplication.list(1, 20).records()).extracting("id").contains(user.id());

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

        var disabledPermission = permissionApplication.create("auth:test:read", "Test Read", null, false);
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
        var original = permissionApplication.create("auth:original:read", "Original", null, true);
        var rejected = permissionApplication.create("auth:rejected:read", "Rejected", null, true);
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
