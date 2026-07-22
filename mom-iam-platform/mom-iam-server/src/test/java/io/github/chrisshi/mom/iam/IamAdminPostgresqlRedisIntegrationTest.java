package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S07 IAM 管理 API、管理员安全约束、Session 撤销和审计验收。 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.authorization.key.allow-test-key=true",
                "mom.iam.session.hmac-pepper=s07-integration-refresh-hmac-pepper-2026-secure",
                "mom.iam.session.allow-local-pepper=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class IamAdminPostgresqlRedisIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(760_000_000_000_000_000L);
    private static final String ACTOR_ID = "760000000000000001";
    private static final String INITIAL_CREDENTIAL = "S07-Initial-Secret-123!";
    private static final List<String> ALL_IAM_PERMISSIONS = List.of(
            "iam:user:read", "iam:user:create", "iam:user:update", "iam:user:enable",
            "iam:user:disable", "iam:user:delete", "iam:user:unlock",
            "iam:user:password-reset", "iam:user:party-rebind", "iam:user:role-assign",
            "iam:user:role-unassign", "iam:user:factory-scope-assign",
            "iam:user:factory-scope-remove", "iam:user:mobile-access-manage",
            "iam:role:read", "iam:role:create", "iam:role:update", "iam:role:enable",
            "iam:role:disable", "iam:role:permission-manage", "iam:permission:read",
            "iam:session:read", "iam:session:revoke", "iam:session:revoke-all",
            "iam:audit:read", "iam:client:read", "iam:client:enable", "iam:client:disable");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            AbstractIamPostgresqlIntegrationTest.newPostgresqlContainer();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.4.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        AbstractIamPostgresqlIntegrationTest.registerDatabaseProperties(registry, POSTGRESQL);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired WebApplicationContext applicationContext;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM iam_security_audit_event");
        jdbc.update("DELETE FROM iam_refresh_token");
        jdbc.update("DELETE FROM iam_user_session");
        jdbc.update("DELETE FROM iam_user_factory_scope");
        jdbc.update("DELETE FROM iam_user_application");
        jdbc.update("DELETE FROM iam_user_role");
        jdbc.update("DELETE FROM iam_external_user_binding");
        jdbc.update("DELETE FROM iam_internal_user_profile");
        jdbc.update("DELETE FROM iam_role_permission WHERE role_id LIKE '76%'");
        jdbc.update("DELETE FROM iam_role WHERE id LIKE '76%'");
        jdbc.update("DELETE FROM iam_user WHERE id LIKE '76%'");
        jdbc.update("UPDATE iam_oauth_client_policy SET status='ENABLED',version=0");
    }

    @Test
    void userCrudAndPermissionCatalogMustNeverExposeCredentialMaterial() throws Exception {
        String body = """
                {
                  "username":"s07.user",
                  "displayName":"S07 User",
                  "userType":"INTERNAL",
                  "initialPassword":"S07-Initial-Secret-123!"
                }
                """;
        String response = mockMvc.perform(post("/api/iam/admin/users")
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("s07.user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(INITIAL_CREDENTIAL))))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.toLowerCase().contains("credential"));

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM iam_user WHERE username='s07.user'", String.class);
        assertTrue(passwordEncoder.matches(INITIAL_CREDENTIAL, hash));

        mockMvc.perform(get("/api/iam/admin/permissions")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("iam:user:read")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password_hash"))));

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM iam_security_audit_event WHERE event_type='iam.user.created'",
                Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM iam_security_audit_event
                 WHERE lower(coalesce(reason_detail,'')) ~ '(password|token|authorization)'
                    OR lower(change_summary::text) ~ '(password|token|authorization)'
                """, Integer.class));
    }

    @Test
    void lastPlatformAdminAndSelfProtectionMustFailClosed() throws Exception {
        TestUser onlyAdmin = insertUser(UserType.INTERNAL, "only-admin");
        assignRole(onlyAdmin.id(), "PLATFORM_ADMIN");

        mockMvc.perform(put("/api/iam/admin/users/{id}/status", onlyAdmin.id())
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","version":0,"reason":"security maintenance"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        assertEquals("ENABLED", jdbc.queryForObject(
                "SELECT status FROM iam_user WHERE id=?", String.class, onlyAdmin.id()));

        mockMvc.perform(put("/api/iam/admin/users/{id}/status", ACTOR_ID)
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","version":0,"reason":"security maintenance"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossUserTypeRoleAndExternalFactoryScopeMustBeRejected() throws Exception {
        TestUser supplier = insertUser(UserType.SUPPLIER, "supplier");
        bindParty(supplier.id(), "SUPPLIER", nextId());
        String internalRoleId = jdbc.queryForObject(
                "SELECT id FROM iam_role WHERE code='IAM_ADMIN'", String.class);

        mockMvc.perform(put("/api/iam/admin/users/{id}/roles", supplier.id())
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + internalRoleId
                                + "\"],\"reason\":\"role review\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/iam/admin/users/{id}/factory-scopes", supplier.id())
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factoryIds\":[\"" + nextId()
                                + "\"],\"reason\":\"scope review\"}"))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM iam_user_factory_scope WHERE user_id=?",
                Integer.class, supplier.id()));
    }

    @Test
    void disablingUserMustRevokeSessionAndWriteRedisAndAudit() throws Exception {
        TestUser user = insertUser(UserType.INTERNAL, "disable-target");
        String sessionId = insertSession(user.id(), "mom-admin-web", "WEB");

        mockMvc.perform(put("/api/iam/admin/users/{id}/status", user.id())
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","version":0,"reason":"account review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertEquals("REVOKED", jdbc.queryForObject(
                "SELECT status FROM iam_user_session WHERE id=?", String.class, sessionId));
        assertEquals("REVOKED", jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token WHERE session_id=?", String.class, sessionId));
        assertEquals("1", redis.opsForValue().get("mom:iam:revoked:sid:" + sessionId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM iam_security_audit_event
                 WHERE event_type='iam.user.status-changed' AND target_id=?
                """, Integer.class, user.id()));
    }

    @Test
    void clientDisableMustRevokeAllClientSessions() throws Exception {
        TestUser first = insertUser(UserType.INTERNAL, "client-first");
        TestUser second = insertUser(UserType.INTERNAL, "client-second");
        String firstSession = insertSession(first.id(), "mom-admin-web", "WEB");
        String secondSession = insertSession(second.id(), "mom-admin-web", "WEB");

        mockMvc.perform(put("/api/iam/admin/oauth-clients/mom-admin-web/status")
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","version":0,"reason":"client incident"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM iam_user_session
                 WHERE id IN (?,?) AND status='REVOKED'
                """, Integer.class, firstSession, secondSession));
        assertEquals("1", redis.opsForValue().get("mom:iam:revoked:sid:" + firstSession));
        assertEquals("1", redis.opsForValue().get("mom:iam:revoked:sid:" + secondSession));
    }

    @Test
    void permissionDenialMustReturnForbiddenWithoutMutation() throws Exception {
        mockMvc.perform(post("/api/iam/admin/users")
                        .with(readOnlyJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"denied.user",
                                  "displayName":"Denied User",
                                  "userType":"INTERNAL",
                                  "initialPassword":"S07-Initial-Secret-123!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM iam_user WHERE username='denied.user'", Integer.class));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject(ACTOR_ID)
                        .audience(List.of("mom-admin-web"))
                        .claim("sid", "760000000000000002")
                        .claim("client_id", "mom-admin-web")
                        .claim("user_type", "INTERNAL")
                        .claim("roles", List.of("PLATFORM_ADMIN"))
                        .claim("permissions", ALL_IAM_PERMISSIONS)
                        .claim("factory_ids", List.of()))
                .authorities(ALL_IAM_PERMISSIONS.stream()
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList());
    }

    private RequestPostProcessor readOnlyJwt() {
        return jwt().jwt(token -> token
                        .subject(ACTOR_ID)
                        .audience(List.of("mom-admin-web"))
                        .claim("sid", "760000000000000002")
                        .claim("client_id", "mom-admin-web")
                        .claim("user_type", "INTERNAL")
                        .claim("roles", List.of("SECURITY_AUDITOR"))
                        .claim("permissions", List.of("iam:user:read"))
                        .claim("factory_ids", List.of()))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "iam:user:read"));
    }

    private TestUser insertUser(UserType type, String suffix) {
        String id = nextId();
        String username = suffix + "-" + id.substring(id.length() - 5);
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?, ?,?,'ENABLED',0,false,
                    now(),'test-s07',now(),'test-s07',0,false)
                """, id, username, passwordEncoder.encode(INITIAL_CREDENTIAL),
                "S07 " + suffix, type.name());
        return new TestUser(id, username);
    }

    private void assignRole(String userId, String roleCode) {
        String roleId = jdbc.queryForObject(
                "SELECT id FROM iam_role WHERE code=?", String.class, roleCode);
        jdbc.update("""
                INSERT INTO iam_user_role (
                    id,user_id,role_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',NULL,NULL,now(),'test-s07',now(),'test-s07',0)
                """, nextId(), userId, roleId);
    }

    private void bindParty(String userId, String partyType, String partyId) {
        jdbc.update("""
                INSERT INTO iam_external_user_binding (
                    id,user_id,party_type,party_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?,'ENABLED',now()-interval '1 minute',NULL,
                    now(),'test-s07',now(),'test-s07',0)
                """, nextId(), userId, partyType, partyId);
    }

    private String insertSession(String userId, String clientId, String channel) {
        String sessionId = nextId();
        Instant now = Instant.now();
        Instant accessExpires = now.plusSeconds(600);
        Instant absoluteExpires = now.plusSeconds("MOBILE".equals(channel) ? 43200 : 28800);
        jdbc.update("""
                INSERT INTO iam_user_session (
                    id,user_id,client_id,channel,status,login_at,last_refresh_at,
                    absolute_expires_at,latest_access_token_expires_at,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?,'ACTIVE',?,NULL,?,?,?,'test-s07',?,'test-s07',0)
                """, sessionId, userId, clientId, channel, Timestamp.from(now),
                Timestamp.from(absoluteExpires), Timestamp.from(accessExpires),
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO iam_refresh_token (
                    id,session_id,token_digest,sequence_no,status,issued_at,expires_at,created_at)
                VALUES (?,?,?,1,'ACTIVE',?,?,?)
                """, nextId(), sessionId, digestFor(sessionId), Timestamp.from(now),
                Timestamp.from(absoluteExpires), Timestamp.from(now));
        return sessionId;
    }

    private static String digestFor(String value) {
        return (value + "0".repeat(64)).substring(0, 64);
    }

    private static String nextId() {
        return Long.toString(IDS.incrementAndGet());
    }

    private record TestUser(String id, String username) { }
}
