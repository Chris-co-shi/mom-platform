package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.bootstrap.IamAdministratorBootstrapProperties;
import io.github.chrisshi.mom.iam.bootstrap.IamBuiltInAdministratorBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置管理员 Bootstrap 的真实 PostgreSQL 事务与幂等验收。
 *
 * <p>测试默认不开启启动 Runner，而是调用同一个 Spring 事务 Bean，以便在单一容器中构造角色缺失、
 * 用户名冲突和重复启动场景；每个用例由测试事务回滚，不污染其他 IAM 集成测试。</p>
 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.bootstrap.enabled=false",
                "mom.iam.authorization.key.allow-test-key=true",
                "mom.iam.session.hmac-pepper=bootstrap-integration-refresh-hmac-pepper-2026",
                "mom.iam.session.allow-local-pepper=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class IamAdministratorBootstrapPostgresqlIntegrationTest {
    private static final String TEMPORARY_PASSWORD = "Bootstrap-Temporary-Secret-2026!";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            AbstractIamPostgresqlIntegrationTest.newPostgresqlContainer();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        AbstractIamPostgresqlIntegrationTest.registerDatabaseProperties(registry, POSTGRESQL);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IamAdministratorBootstrapProperties properties;
    @Autowired IamBuiltInAdministratorBootstrap bootstrap;

    @BeforeEach
    void resetBootstrapState() {
        properties.setEnabled(false);
        properties.setUsername(IamAdministratorBootstrapProperties.BUILT_IN_USERNAME);
        properties.setPassword("");
        properties.setDisplayName("Platform Administrator");
        jdbc.update("DELETE FROM iam_user_role");
        jdbc.update("DELETE FROM iam_user WHERE system_account=false");
    }

    @Test
    void defaultDisabledMustNotCreateAdministrator() {
        assertEquals(0, adminCount());
    }

    @Test
    void firstInitializationMustCreateProtectedAdministratorAndRoleInOneTransaction() {
        enableBootstrap();
        bootstrap.initialize();

        Map<String, Object> user = jdbc.queryForMap("""
                SELECT username,password_hash,display_name,user_type,status,
                       password_change_required,system_account,failed_login_count,version
                  FROM iam_user
                 WHERE username='admin'
                """);
        String passwordHash = (String) user.get("password_hash");
        assertEquals("admin", user.get("username"));
        assertEquals("INTERNAL", user.get("user_type"));
        assertEquals("ENABLED", user.get("status"));
        assertEquals(true, user.get("password_change_required"));
        assertEquals(true, user.get("system_account"));
        assertEquals(0, user.get("failed_login_count"));
        assertEquals(0L, user.get("version"));
        assertNotEquals(TEMPORARY_PASSWORD, passwordHash);
        assertTrue(passwordEncoder.matches(TEMPORARY_PASSWORD, passwordHash));
        assertEquals(1, platformAdminRelationCount());
    }

    @Test
    void repeatedInitializationMustNotResetCredentialStateLockVersionOrRoles() {
        enableBootstrap();
        bootstrap.initialize();
        String originalHash = passwordHash();
        String userId = adminId();
        Instant lockedUntil = Instant.now().plusSeconds(600);
        jdbc.update("""
                UPDATE iam_user
                   SET status='DISABLED',failed_login_count=4,locked_until=?,
                       password_change_required=false,version=7
                 WHERE id=?
                """, Timestamp.from(lockedUntil), userId);

        properties.setPassword("Different-Temporary-Secret-2026!");
        bootstrap.initialize();

        Map<String, Object> user = jdbc.queryForMap("""
                SELECT password_hash,status,failed_login_count,locked_until,
                       password_change_required,version
                  FROM iam_user WHERE id=?
                """, userId);
        assertEquals(originalHash, user.get("password_hash"));
        assertEquals("DISABLED", user.get("status"));
        assertEquals(4, user.get("failed_login_count"));
        assertTrue(((Timestamp) user.get("locked_until")).toInstant().isAfter(Instant.now()));
        assertEquals(false, user.get("password_change_required"));
        assertEquals(7L, user.get("version"));
        assertEquals(1, adminCount());
        assertEquals(1, platformAdminRelationCount());
    }

    @Test
    void missingPasswordMustFailBeforeDatabaseMutation() {
        properties.setEnabled(true);
        properties.setPassword("");
        assertThrows(IllegalStateException.class, bootstrap::initialize);
        assertEquals(0, adminCount());
    }

    @Test
    void missingPlatformAdminRoleMustFailWithoutPartialUser() {
        jdbc.update("""
                DELETE FROM iam_role_permission
                 WHERE role_id=(SELECT id FROM iam_role WHERE code='PLATFORM_ADMIN')
                """);
        jdbc.update("DELETE FROM iam_role WHERE code='PLATFORM_ADMIN'");
        enableBootstrap();

        assertThrows(IllegalStateException.class, bootstrap::initialize);
        assertEquals(0, adminCount());
    }

    @Test
    void ordinaryAdminUsernameConflictMustNotEscalateOrChangeCredential() {
        String originalHash = passwordEncoder.encode("Ordinary-Account-Secret-2026!");
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,system_account,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES ('790000000000000001','admin',?,'Ordinary Admin','INTERNAL','ENABLED',
                    0,false,false,now(),'test',now(),'test',0,false)
                """, originalHash);
        enableBootstrap();

        assertThrows(IllegalStateException.class, bootstrap::initialize);
        Map<String, Object> user = jdbc.queryForMap("""
                SELECT password_hash,system_account,version FROM iam_user WHERE username='admin'
                """);
        assertEquals(originalHash, user.get("password_hash"));
        assertEquals(false, user.get("system_account"));
        assertEquals(0L, user.get("version"));
        assertEquals(0, platformAdminRelationCount());
    }

    @Test
    void databaseMustRejectPhysicalDeletionOfSystemAccount() {
        enableBootstrap();
        bootstrap.initialize();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM iam_user WHERE username='admin'"));
    }

    @Test
    void databaseMustRejectSystemUsernameChange() {
        enableBootstrap();
        bootstrap.initialize();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE iam_user SET username='renamed' WHERE username='admin'"));
    }

    @Test
    void databaseMustRejectSystemUserTypeChange() {
        enableBootstrap();
        bootstrap.initialize();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE iam_user SET user_type='CUSTOMER' WHERE username='admin'"));
    }

    @Test
    void databaseMustRejectOrdinaryAccountEscalationToSystemAccount() {
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,system_account,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES ('790000000000000002','ordinary',?,'Ordinary','INTERNAL','ENABLED',
                    0,false,false,now(),'test',now(),'test',0,false)
                """, passwordEncoder.encode("Ordinary-Account-Secret-2026!"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE iam_user SET username='admin',system_account=true WHERE username='ordinary'
                """));
    }

    private void enableBootstrap() {
        properties.setEnabled(true);
        properties.setPassword(TEMPORARY_PASSWORD);
    }

    private int adminCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM iam_user WHERE username='admin'", Integer.class);
    }

    private String adminId() {
        return jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE username='admin'", String.class);
    }

    private String passwordHash() {
        return jdbc.queryForObject(
                "SELECT password_hash FROM iam_user WHERE username='admin'", String.class);
    }

    private int platformAdminRelationCount() {
        return jdbc.queryForObject("""
                SELECT count(*)
                  FROM iam_user_role ur
                  JOIN iam_role r ON r.id=ur.role_id
                 WHERE r.code='PLATFORM_ADMIN'
                """, Integer.class);
    }
}
