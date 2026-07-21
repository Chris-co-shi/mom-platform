package io.github.chrisshi.mom.iam;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditActorMissingException;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.domain.type.*;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.*;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.*;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** IAM 领域关系、Session、Refresh、安全审计和 Slice 01 审计的真实 PostgreSQL 验证。 */
@Testcontainers(disabledWithoutDocker = true)
class IamPersistencePostgresqlIntegrationTest extends AbstractIamPostgresqlIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(900_000_000_000_000_000L);

    @Container
    private static final PostgreSQLContainer POSTGRESQL = newPostgresqlContainer();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registerDatabaseProperties(registry, POSTGRESQL);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AuditContextExecutor actors;
    @Autowired IamUserMapper users;
    @Autowired IamRoleMapper roles;
    @Autowired IamUserSessionMapper sessions;
    @Autowired IamRefreshTokenMapper refreshTokens;
    @Autowired IamSecurityAuditEventAppender auditAppender;

    @Test
    void identityRbacScopeAndValidityConstraintsMustRejectInvalidData() {
        IamUserEntity internal = createUser("internal", UserType.INTERNAL);
        IamUserEntity supplier = createUser("supplier", UserType.SUPPLIER);
        assertThrows(DataAccessException.class, () -> sqlUser(nextId(), internal.getUsername(), "INTERNAL", "ENABLED", 0));
        assertThrows(DataAccessException.class, () -> sqlUser(nextId(), unique("bad-type"), "PDA", "ENABLED", 0));
        assertThrows(DataAccessException.class, () -> sqlUser(nextId(), unique("bad-status"), "INTERNAL", "LOCKED", 0));
        assertThrows(DataAccessException.class, () -> sqlUser(nextId(), unique("bad-count"), "INTERNAL", "ENABLED", -1));

        jdbc.update("""
                INSERT INTO iam_internal_user_profile
                (id,user_id,employee_no,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,now(),'test',now(),'test',0)
                """, nextId(), internal.getId(), unique("emp"));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_internal_user_profile
                (id,user_id,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,now(),'test',now(),'test',0)
                """, nextId(), internal.getId()));
        jdbc.update("""
                INSERT INTO iam_external_user_binding
                (id,user_id,party_type,party_id,status,valid_from,valid_until,
                 created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'SUPPLIER',?,'ENABLED',now(),now()+interval '1 day',now(),'test',now(),'test',0)
                """, nextId(), supplier.getId(), "8000000000000000001");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_external_user_binding
                (id,user_id,party_type,party_id,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'SUPPLIER',?,'ENABLED',now(),'test',now(),'test',0)
                """, nextId(), supplier.getId(), "8000000000000000002"));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_external_user_binding
                (id,user_id,party_type,party_id,status,valid_from,valid_until,
                 created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'SUPPLIER',?,'ENABLED',now(),now()-interval '1 second',now(),'test',now(),'test',0)
                """, nextId(), internal.getId(), "8000000000000000003"));

        IamRoleEntity role = createRole("TEST_ROLE_" + IDS.incrementAndGet());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_role
                (id,code,name,applicable_user_type,status,built_in,created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,'重复角色','INTERNAL','ENABLED',false,now(),'test',now(),'test',0,false)
                """, nextId(), role.getCode()));
        jdbc.update("""
                INSERT INTO iam_user_role
                (id,user_id,role_id,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',now(),'test',now(),'test',0)
                """, nextId(), internal.getId(), role.getId());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_user_role
                (id,user_id,role_id,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',now(),'test',now(),'test',0)
                """, nextId(), internal.getId(), role.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_permission
                (id,code,name,domain_code,resource_code,action_code,risk_level,status,description,built_in,
                 created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,'IAM_USER_READ','非法','iam','user','read','LOW','ENABLED','非法格式',false,
                        now(),'test',now(),'test',0,false)
                """, nextId()));

        jdbc.update("""
                INSERT INTO iam_user_factory_scope
                (id,user_id,factory_id,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',now(),'test',now(),'test',0)
                """, nextId(), internal.getId(), "7000000000000000001");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_user_factory_scope
                (id,user_id,factory_id,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',now(),'test',now(),'test',0)
                """, nextId(), internal.getId(), "7000000000000000001"));
        jdbc.update("""
                INSERT INTO iam_user_application
                (id,user_id,application_code,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'MOM_MOBILE_PDA','ENABLED',now(),'test',now(),'test',0)
                """, nextId(), internal.getId());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_user_application
                (id,user_id,application_code,status,created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'MOM_ADMIN','ENABLED',now(),'test',now(),'test',0)
                """, nextId(), internal.getId()));
    }

    @Test
    void sessionAndRefreshConstraintsMustEnforceSecurityState() {
        IamUserEntity user = createUser("session", UserType.INTERNAL);
        IamUserSessionEntity session = createSession(user.getId());
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_user_session
                (id,user_id,client_id,channel,status,login_at,absolute_expires_at,
                 created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?, 'mom-admin-web','WEB','UNKNOWN',now(),now()+interval '1 hour',
                        now(),'test',now(),'test',0)
                """, nextId(), user.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO iam_user_session
                (id,user_id,client_id,channel,status,login_at,absolute_expires_at,
                 created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?, 'mom-admin-web','WEB','ACTIVE',now(),now()-interval '1 second',
                        now(),'test',now(),'test',0)
                """, nextId(), user.getId()));

        insertRefresh(session.getId(), 1, RefreshTokenStatus.ACTIVE, "digest-a");
        assertThrows(DataAccessException.class,
                () -> insertRefresh(session.getId(), 2, RefreshTokenStatus.ACTIVE, "digest-b"));
        insertRefresh(session.getId(), 2, RefreshTokenStatus.ROTATED, "digest-c");
        assertThrows(DataAccessException.class,
                () -> insertRefresh(session.getId(), 2, RefreshTokenStatus.REVOKED, "digest-d"));
        assertThrows(DataAccessException.class,
                () -> insertRefresh(session.getId(), 3, RefreshTokenStatus.REVOKED, "digest-a"));
        assertThrows(DataAccessException.class,
                () -> insertRefresh(session.getId(), 0, RefreshTokenStatus.REVOKED, "digest-e"));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema=? AND table_name='iam_refresh_token'
                   AND column_name IN ('token_value','refresh_token','token_plaintext')
                """, Long.class, SCHEMA));
        IamRefreshTokenEntity sensitive = new IamRefreshTokenEntity();
        sensitive.setTokenDigest("must-not-appear");
        assertFalse(sensitive.toString().contains("must-not-appear"));
    }

    @Test
    void slice01AuditOptimisticLockSystemActorAndWrapperGuardMustApply() {
        IamRoleEntity role = new IamRoleEntity();
        role.setCode("AUDIT_ROLE_" + IDS.incrementAndGet());
        role.setName("审计测试角色");
        role.setApplicableUserType(UserType.INTERNAL);
        role.setStatus(IamRecordStatus.ENABLED);
        role.setBuiltIn(false);
        role.setCreatedBy("forged");
        role.setUpdatedBy("forged");
        asActor("iam-user-writer", () -> roles.insert(role));
        IamRoleEntity saved = roles.selectById(role.getId());
        assertEquals("iam-user-writer", saved.getCreatedBy());
        assertEquals("iam-user-writer", saved.getUpdatedBy());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());

        IamRoleEntity first = roles.selectById(role.getId());
        IamRoleEntity stale = roles.selectById(role.getId());
        first.setName("第一次更新"); stale.setName("过期更新");
        assertEquals(1, asActor("iam-role-updater", () -> roles.updateById(first)));
        assertEquals(0, asActor("iam-stale-updater", () -> roles.updateById(stale)));
        assertEquals(1L, roles.selectById(role.getId()).getVersion());

        IamRoleEntity noActor = new IamRoleEntity();
        noActor.setCode("NO_ACTOR_" + IDS.incrementAndGet());
        noActor.setName("缺少 Actor"); noActor.setApplicableUserType(UserType.INTERNAL);
        noActor.setStatus(IamRecordStatus.ENABLED); noActor.setBuiltIn(false);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> roles.insert(noActor));
        assertTrue(hasCause(failure, AuditActorMissingException.class));

        IamRoleEntity system = actors.runAsSystem("mom-iam-test-writer", () -> {
            IamRoleEntity value = new IamRoleEntity();
            value.setCode("SYSTEM_ROLE_" + IDS.incrementAndGet());
            value.setName("系统写入角色"); value.setApplicableUserType(UserType.INTERNAL);
            value.setStatus(IamRecordStatus.ENABLED); value.setBuiltIn(false);
            roles.insert(value); return roles.selectById(value.getId());
        });
        assertEquals("mom-iam-test-writer", system.getCreatedBy());
        assertThrows(UnsupportedOperationException.class, () -> roles.update(
                Wrappers.<IamRoleEntity>lambdaUpdate().set(IamRoleEntity::getName, "禁止路径")
                        .eq(IamRoleEntity::getId, role.getId())));
    }

    @Test
    void securityAuditMustBeAppendOnlyJsonbIndexedAndSensitiveSafe() {
        IamSecurityAuditEventEntity event = new IamSecurityAuditEventEntity();
        event.setId(nextId()); event.setEventType("iam.account.test-created");
        event.setEventCategory(SecurityEventCategory.ACCOUNT);
        event.setRiskLevel(PermissionRiskLevel.LOW); event.setResult(SecurityAuditResult.SUCCESS);
        event.setActorType(SecurityAuditActorType.SYSTEM); event.setTargetType("USER");
        event.setTargetId("test-user"); event.setReasonCode("S02_TEST");
        event.setReasonDetail("只包含非敏感测试说明");
        event.setChangeSummary("{\"fields\":[\"status\"]}");
        event.setCorrelationId("s02-correlation");
        event.setOccurredAt(Instant.parse("2026-07-21T06:00:00Z"));
        event.setCreatedAt(Instant.parse("2026-07-21T06:00:01Z"));
        auditAppender.append(event);
        assertEquals("object", jdbc.queryForObject(
                "select jsonb_typeof(change_summary) from iam_security_audit_event where id=?",
                String.class, event.getId()));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema=? AND table_name='iam_security_audit_event'
                   AND column_name IN ('deleted','version','updated_at','updated_by')
                """, Long.class, SCHEMA));
        assertTrue(jdbc.queryForObject("""
                SELECT count(*)>=6 FROM pg_indexes
                 WHERE schemaname=? AND tablename='iam_security_audit_event'
                """, Boolean.class, SCHEMA));
        assertEquals(Set.of("append"), Set.copyOf(Arrays.stream(
                IamSecurityAuditEventAppender.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList()));
        IamSecurityAuditEventEntity unsafe = new IamSecurityAuditEventEntity();
        unsafe.setReasonDetail("refresh_token=secret"); unsafe.setChangeSummary("{}");
        assertThrows(IllegalArgumentException.class, () -> auditAppender.append(unsafe));
        assertFalse(event.toString().contains(event.getReasonDetail()));
    }

    private void sqlUser(String id, String username, String type, String status, int failures) {
        jdbc.update("""
                INSERT INTO iam_user
                (id,username,password_hash,display_name,user_type,status,failed_login_count,
                 password_change_required,created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,'hash','测试',?,?,?,true,now(),'test',now(),'test',0,false)
                """, id, username, type, status, failures);
    }

    private IamUserEntity createUser(String prefix, UserType type) {
        return asActor("mom-iam-test-user-writer", () -> {
            IamUserEntity user = new IamUserEntity();
            user.setUsername(unique(prefix)); user.setPasswordHash("$argon2id$test-only");
            user.setDisplayName("S02 测试用户"); user.setUserType(type);
            user.setStatus(IamRecordStatus.ENABLED); user.setFailedLoginCount(0);
            user.setPasswordChangeRequired(true); users.insert(user);
            return users.selectById(user.getId());
        });
    }

    private IamRoleEntity createRole(String code) {
        return asActor("mom-iam-test-role-writer", () -> {
            IamRoleEntity role = new IamRoleEntity(); role.setCode(code); role.setName("S02 测试角色");
            role.setApplicableUserType(UserType.INTERNAL); role.setStatus(IamRecordStatus.ENABLED);
            role.setBuiltIn(false); roles.insert(role); return roles.selectById(role.getId());
        });
    }

    private IamUserSessionEntity createSession(String userId) {
        return asActor("mom-iam-test-session-writer", () -> {
            Instant now = Instant.now(); IamUserSessionEntity s = new IamUserSessionEntity();
            s.setUserId(userId); s.setClientId("mom-admin-web"); s.setChannel(ClientChannel.WEB);
            s.setStatus(UserSessionStatus.ACTIVE); s.setLoginAt(now);
            s.setAbsoluteExpiresAt(now.plus(Duration.ofHours(8))); sessions.insert(s);
            return sessions.selectById(s.getId());
        });
    }

    private void insertRefresh(String sessionId, long sequence, RefreshTokenStatus status, String digest) {
        IamRefreshTokenEntity token = new IamRefreshTokenEntity(); token.setSessionId(sessionId);
        token.setTokenDigest(digest); token.setSequenceNo(sequence); token.setStatus(status);
        token.setIssuedAt(Instant.now()); token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setCreatedAt(Instant.now()); refreshTokens.insert(token);
    }

    private <T> T asActor(String id, Supplier<T> action) {
        return actors.runAsActor(new AuditActor(id, ActorType.USER, "INTERNAL",
                "mom-admin-web", "s02-test-session", "s02-test-correlation"), action);
    }

    private static boolean hasCause(Throwable value, Class<? extends Throwable> type) {
        for (Throwable current=value; current!=null; current=current.getCause())
            if (type.isInstance(current)) return true;
        return false;
    }

    private static String nextId() { return String.valueOf(IDS.incrementAndGet()); }
    private static String unique(String prefix) { return prefix + "-" + IDS.incrementAndGet(); }
}
