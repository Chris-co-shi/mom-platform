package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S07 管理 API 契约加固 PostgreSQL E2E。
 *
 * <p>测试使用真实 PostgreSQL 验证父聚合行锁、客户端读取版本、关系替换、父版本推进与追加型安全
 * 审计的单事务语义。测试不新增数据库迁移；故障注入触发器只存在于单个测试用例中并在用例结束时
 * 删除。Redis 指向不可连接端口，但这些用例不创建活动 Session，因此不会产生外部撤销副作用。</p>
 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.admin.enabled=true",
                "mom.iam.authorization.key.allow-test-key=true",
                "mom.iam.session.hmac-pepper=s07-contract-refresh-hmac-pepper-2026-secure",
                "mom.iam.session.allow-local-pepper=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class IamAdminReadModelPostgresqlIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(779_500_000_000_000_000L);
    private static final String INTERNAL_USER_ID = "779000000000000001";
    private static final String SUPPLIER_USER_ID = "779000000000000002";
    private static final String CUSTOM_ROLE_ID = "779000000000000003";
    private static final String ALTERNATE_ROLE_ID = "779000000000000004";
    private static final String FACTORY_ONE = "779000000000000005";
    private static final String FACTORY_TWO = "779000000000000006";
    private static final String PARTY_ONE = "779000000000000007";
    private static final String PARTY_TWO = "779000000000000008";
    private static final String ACTOR_ID = "779000000000000090";
    private static final String PASSWORD_DIGEST_MARKER = "SENSITIVE_PASSWORD_DIGEST_MARKER";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            AbstractIamPostgresqlIntegrationTest.newPostgresqlContainer();

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        AbstractIamPostgresqlIntegrationTest.registerDatabaseProperties(registry, POSTGRESQL);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired WebApplicationContext applicationContext;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        dropVersionFailureTrigger();
        jdbc.update("DELETE FROM iam_security_audit_event WHERE target_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user_role WHERE user_id LIKE '779%' OR role_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_role_permission WHERE role_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user_factory_scope WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user_application WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_external_user_binding WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user WHERE id LIKE '779%'");
        jdbc.update("DELETE FROM iam_role WHERE id LIKE '779%'");
        insertUser(INTERNAL_USER_ID, "s07-contract-internal", UserType.INTERNAL, PASSWORD_DIGEST_MARKER);
        insertUser(SUPPLIER_USER_ID, "s07-contract-supplier", UserType.SUPPLIER,
                passwordEncoder.encode("S07-Contract-Secret-123!"));
        insertRole(CUSTOM_ROLE_ID, "S07_CONTRACT_ROLE", "S07 Contract Role");
        insertRole(ALTERNATE_ROLE_ID, "S07_ALTERNATE_ROLE", "S07 Alternate Role");
    }

    @Test
    void readContractsMustExposeAggregateVersionsAndNoCredentialMaterial() throws Exception {
        String permissionId = permissionId("iam:user:read");
        assignUserRole(INTERNAL_USER_ID, CUSTOM_ROLE_ID);
        assignFactory(INTERNAL_USER_ID, FACTORY_ONE);
        setMobileRow(INTERNAL_USER_ID, true);
        bindParty(SUPPLIER_USER_ID, PARTY_ONE);
        assignRolePermission(CUSTOM_ROLE_ID, permissionId);

        mockMvc.perform(get("/api/iam/admin/users/{id}/authorizations", INTERNAL_USER_ID)
                        .with(userReadJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(INTERNAL_USER_ID))
                .andExpect(jsonPath("$.userVersion").value(0))
                .andExpect(jsonPath("$.roleIds", hasItem(CUSTOM_ROLE_ID)))
                .andExpect(jsonPath("$.factoryIds", hasItem(FACTORY_ONE)))
                .andExpect(jsonPath("$.mobileAccessEnabled").value(true))
                .andExpect(jsonPath("$.partyBinding").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD_DIGEST_MARKER))));

        mockMvc.perform(get("/api/iam/admin/users/{id}/authorizations", SUPPLIER_USER_ID)
                        .with(userReadJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(SUPPLIER_USER_ID))
                .andExpect(jsonPath("$.userVersion").value(0))
                .andExpect(jsonPath("$.partyBinding.partyType").value("SUPPLIER"))
                .andExpect(jsonPath("$.partyBinding.partyId").value(PARTY_ONE));

        mockMvc.perform(get("/api/iam/admin/roles/{id}/permissions", CUSTOM_ROLE_ID)
                        .with(roleReadJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(CUSTOM_ROLE_ID))
                .andExpect(jsonPath("$.roleVersion").value(0))
                .andExpect(jsonPath("$.permissionIds", hasItem(permissionId)))
                .andExpect(content().string(not(containsString("password_hash"))))
                .andExpect(content().string(not(containsString("token"))));
    }

    @Test
    void correctVersionsMustAdvanceAndReturnCompleteLatestSnapshots() throws Exception {
        String permissionId = permissionId("iam:user:read");

        putUserRoles(INTERNAL_USER_ID, CUSTOM_ROLE_ID, 0, adminJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVersion").value(1))
                .andExpect(jsonPath("$.roleIds", hasItem(CUSTOM_ROLE_ID)))
                .andExpect(jsonPath("$.factoryIds").isEmpty())
                .andExpect(jsonPath("$.mobileAccessEnabled").value(false));

        putFactory(INTERNAL_USER_ID, FACTORY_ONE, 1, adminJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVersion").value(2))
                .andExpect(jsonPath("$.roleIds", hasItem(CUSTOM_ROLE_ID)))
                .andExpect(jsonPath("$.factoryIds", hasItem(FACTORY_ONE)));

        putMobile(INTERNAL_USER_ID, true, 2, adminJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVersion").value(3))
                .andExpect(jsonPath("$.factoryIds", hasItem(FACTORY_ONE)))
                .andExpect(jsonPath("$.mobileAccessEnabled").value(true));

        putParty(SUPPLIER_USER_ID, PARTY_ONE, 0, adminJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVersion").value(1))
                .andExpect(jsonPath("$.partyBinding.partyType").value("SUPPLIER"))
                .andExpect(jsonPath("$.partyBinding.partyId").value(PARTY_ONE));

        putRolePermissions(CUSTOM_ROLE_ID, permissionId, 0, adminJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleVersion").value(1))
                .andExpect(jsonPath("$.permissionIds", hasItem(permissionId)));

        assertEquals(3L, userVersion(INTERNAL_USER_ID));
        assertEquals(1L, userVersion(SUPPLIER_USER_ID));
        assertEquals(1L, roleVersion(CUSTOM_ROLE_ID));
        assertEquals(5, successAuditCount());
    }

    @Test
    void twoClientsReadingSameVersionMustAllowOnlyFirstCommit() throws Exception {
        putUserRoles(INTERNAL_USER_ID, CUSTOM_ROLE_ID, 0, adminJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVersion").value(1));

        putFactory(INTERNAL_USER_ID, FACTORY_ONE, 0, adminJwt())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale_version"));

        assertEquals(1L, userVersion(INTERNAL_USER_ID));
        assertEquals(List.of(CUSTOM_ROLE_ID), roleIds(INTERNAL_USER_ID));
        assertTrue(factoryIds(INTERNAL_USER_ID).isEmpty());
        assertEquals(1, successAuditCount());
    }

    @Test
    void staleWritesMustHaveZeroRolePermissionFactoryMobileAndPartySideEffects() throws Exception {
        String oldPermission = permissionId("iam:user:read");
        String newPermission = permissionId("iam:role:read");
        assignUserRole(INTERNAL_USER_ID, ALTERNATE_ROLE_ID);
        assignFactory(INTERNAL_USER_ID, FACTORY_ONE);
        setMobileRow(INTERNAL_USER_ID, true);
        bindParty(SUPPLIER_USER_ID, PARTY_ONE);
        assignRolePermission(CUSTOM_ROLE_ID, oldPermission);
        jdbc.update("UPDATE iam_user SET version=5 WHERE id=?", INTERNAL_USER_ID);
        jdbc.update("UPDATE iam_user SET version=4 WHERE id=?", SUPPLIER_USER_ID);
        jdbc.update("UPDATE iam_role SET version=6 WHERE id=?", CUSTOM_ROLE_ID);

        putUserRoles(INTERNAL_USER_ID, CUSTOM_ROLE_ID, 4, adminJwt())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));
        putFactory(INTERNAL_USER_ID, FACTORY_TWO, 4, adminJwt())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));
        putMobile(INTERNAL_USER_ID, false, 4, adminJwt())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));
        putParty(SUPPLIER_USER_ID, PARTY_TWO, 3, adminJwt())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));
        putRolePermissions(CUSTOM_ROLE_ID, newPermission, 5, adminJwt())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));

        assertEquals(List.of(ALTERNATE_ROLE_ID), roleIds(INTERNAL_USER_ID));
        assertEquals(List.of(FACTORY_ONE), factoryIds(INTERNAL_USER_ID));
        assertTrue(mobileEnabled(INTERNAL_USER_ID));
        assertEquals(PARTY_ONE, partyId(SUPPLIER_USER_ID));
        assertEquals(List.of(oldPermission), permissionIds(CUSTOM_ROLE_ID));
        assertEquals(5L, userVersion(INTERNAL_USER_ID));
        assertEquals(4L, userVersion(SUPPLIER_USER_ID));
        assertEquals(6L, roleVersion(CUSTOM_ROLE_ID));
        assertEquals(0, successAuditCount());
    }

    @Test
    void relationReplacementAndParentVersionMustRollbackAsOneTransaction() throws Exception {
        assignFactory(INTERNAL_USER_ID, FACTORY_ONE);
        installVersionFailureTrigger(INTERNAL_USER_ID);
        try {
            putFactory(INTERNAL_USER_ID, FACTORY_TWO, 0, adminJwt())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("conflict"));
        }
        finally {
            dropVersionFailureTrigger();
        }

        assertEquals(List.of(FACTORY_ONE), factoryIds(INTERNAL_USER_ID));
        assertEquals(0L, userVersion(INTERNAL_USER_ID));
        assertEquals(0, successAuditCount());
    }

    @Test
    void lastPlatformAdminAndBuiltInRoleProtectionsMustRemainEffective() throws Exception {
        String platformAdminRole = roleId("PLATFORM_ADMIN");
        assignUserRole(INTERNAL_USER_ID, platformAdminRole);

        mockMvc.perform(put("/api/iam/admin/users/{id}/roles", INTERNAL_USER_ID)
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds":[],"version":0,"reason":"last admin protection"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        assertEquals(List.of(platformAdminRole), roleIds(INTERNAL_USER_ID));
        assertEquals(0L, userVersion(INTERNAL_USER_ID));

        long builtInVersion = roleVersion(platformAdminRole);
        int before = jdbc.queryForObject(
                "SELECT count(*) FROM iam_role_permission WHERE role_id=?", Integer.class, platformAdminRole);
        putRolePermissions(platformAdminRole, permissionId("iam:user:read"), builtInVersion, adminJwt())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        assertEquals(before, jdbc.queryForObject(
                "SELECT count(*) FROM iam_role_permission WHERE role_id=?", Integer.class, platformAdminRole));
        assertEquals(builtInVersion, roleVersion(platformAdminRole));
        assertEquals(0, successAuditCount());
    }

    @Test
    void readAndWritePermissionsMustBeCheckedIndependently() throws Exception {
        mockMvc.perform(get("/api/iam/admin/users/{id}/authorizations", INTERNAL_USER_ID)
                        .with(userWriteJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        putUserRoles(INTERNAL_USER_ID, CUSTOM_ROLE_ID, 0, userWriteJwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVersion").value(1));

        mockMvc.perform(get("/api/iam/admin/roles/{id}/permissions", CUSTOM_ROLE_ID)
                        .with(roleReadJwt()))
                .andExpect(status().isOk());
        putRolePermissions(CUSTOM_ROLE_ID, permissionId("iam:user:read"), 0, roleReadJwt())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void missingVersionMustBeRejectedBeforeAnyMutation() throws Exception {
        mockMvc.perform(put("/api/iam/admin/users/{id}/roles", INTERNAL_USER_ID)
                        .with(adminJwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + CUSTOM_ROLE_ID
                                + "\"],\"reason\":\"missing version\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        assertTrue(roleIds(INTERNAL_USER_ID).isEmpty());
        assertEquals(0L, userVersion(INTERNAL_USER_ID));
        assertEquals(0, successAuditCount());
    }

    @Test
    void responsesAuditsAndLogsMustRemainSensitiveSafe(CapturedOutput output) throws Exception {
        MvcResult result = putUserRoles(INTERNAL_USER_ID, CUSTOM_ROLE_ID, 0, adminJwt())
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(PASSWORD_DIGEST_MARKER))))
                .andExpect(content().string(not(containsString("refresh_token"))))
                .andExpect(content().string(not(containsString("authorization_code"))))
                .andExpect(content().string(not(containsString("private_key"))))
                .andReturn();

        String audit = jdbc.queryForObject("""
                SELECT coalesce(reason_detail,'') || coalesce(change_summary::text,'')
                  FROM iam_security_audit_event
                 WHERE event_type='iam.user.roles-replaced' AND target_id=?
                """, String.class, INTERNAL_USER_ID);
        assertFalse(result.getResponse().getContentAsString().contains(PASSWORD_DIGEST_MARKER));
        assertFalse(audit.contains(PASSWORD_DIGEST_MARKER));
        assertFalse(output.getAll().contains(PASSWORD_DIGEST_MARKER));
    }

    private org.springframework.test.web.servlet.ResultActions putUserRoles(
            String userId, String roleId, long version, RequestPostProcessor authentication) throws Exception {
        return mockMvc.perform(put("/api/iam/admin/users/{id}/roles", userId)
                .with(authentication).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + roleId + "\"],\"version\":" + version
                        + ",\"reason\":\"role contract update\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions putFactory(
            String userId, String factoryId, long version, RequestPostProcessor authentication) throws Exception {
        return mockMvc.perform(put("/api/iam/admin/users/{id}/factory-scopes", userId)
                .with(authentication).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factoryIds\":[\"" + factoryId + "\"],\"version\":" + version
                        + ",\"reason\":\"factory contract update\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions putMobile(
            String userId, boolean enabled, long version, RequestPostProcessor authentication) throws Exception {
        return mockMvc.perform(put("/api/iam/admin/users/{id}/mobile-access", userId)
                .with(authentication).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + ",\"version\":" + version
                        + ",\"reason\":\"mobile contract update\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions putParty(
            String userId, String partyId, long version, RequestPostProcessor authentication) throws Exception {
        return mockMvc.perform(put("/api/iam/admin/users/{id}/party-binding", userId)
                .with(authentication).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partyType\":\"SUPPLIER\",\"partyId\":\"" + partyId
                        + "\",\"version\":" + version + ",\"reason\":\"party contract update\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions putRolePermissions(
            String roleId, String permissionId, long version, RequestPostProcessor authentication) throws Exception {
        return mockMvc.perform(put("/api/iam/admin/roles/{id}/permissions", roleId)
                .with(authentication).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionIds\":[\"" + permissionId + "\"],\"version\":" + version
                        + ",\"reason\":\"permission contract update\"}"));
    }

    private RequestPostProcessor adminJwt() {
        return jwtWith(
                "iam:user:read", "iam:user:role-assign", "iam:user:factory-scope-assign",
                "iam:user:mobile-access-manage", "iam:user:party-rebind",
                "iam:role:read", "iam:role:permission-manage");
    }

    private RequestPostProcessor userReadJwt() {
        return jwtWith("iam:user:read");
    }

    private RequestPostProcessor userWriteJwt() {
        return jwtWith("iam:user:role-assign");
    }

    private RequestPostProcessor roleReadJwt() {
        return jwtWith("iam:role:read");
    }

    private RequestPostProcessor jwtWith(String... permissions) {
        List<String> permissionList = List.of(permissions);
        List<GrantedAuthority> authorities = permissionList.stream()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
                .toList();
        return jwt().jwt(token -> token
                        .subject(ACTOR_ID)
                        .audience(List.of("mom-admin-web"))
                        .claim("sid", "779000000000000091")
                        .claim("client_id", "mom-admin-web")
                        .claim("user_type", "INTERNAL")
                        .claim("roles", List.of("PLATFORM_ADMIN"))
                        .claim("permissions", permissionList)
                        .claim("factory_ids", List.of()))
                .authorities(authorities);
    }

    private void insertUser(String id, String username, UserType type, String passwordHash) {
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?, ?,?,'ENABLED',0,false,
                    now(),'test-s07-contract',now(),'test-s07-contract',0,false)
                """, id, username, passwordHash, "S07 " + username, type.name());
    }

    private void insertRole(String id, String code, String name) {
        jdbc.update("""
                INSERT INTO iam_role (
                    id,code,name,applicable_user_type,status,built_in,description,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?,'INTERNAL','ENABLED',false,'S07 contract test role',
                    now(),'test-s07-contract',now(),'test-s07-contract',0,false)
                """, id, code, name);
    }

    private void assignUserRole(String userId, String roleId) {
        jdbc.update("""
                INSERT INTO iam_user_role (
                    id,user_id,role_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',NULL,NULL,
                    now(),'test-s07-contract',now(),'test-s07-contract',0)
                """, nextId(), userId, roleId);
    }

    private void assignFactory(String userId, String factoryId) {
        jdbc.update("""
                INSERT INTO iam_user_factory_scope (
                    id,user_id,factory_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,'ENABLED',NULL,NULL,
                    now(),'test-s07-contract',now(),'test-s07-contract',0)
                """, nextId(), userId, factoryId);
    }

    private void setMobileRow(String userId, boolean enabled) {
        jdbc.update("""
                INSERT INTO iam_user_application (
                    id,user_id,application_code,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'MOM_MOBILE_PDA',?,NULL,NULL,
                    now(),'test-s07-contract',now(),'test-s07-contract',0)
                """, nextId(), userId, enabled ? "ENABLED" : "DISABLED");
    }

    private void bindParty(String userId, String partyId) {
        jdbc.update("""
                INSERT INTO iam_external_user_binding (
                    id,user_id,party_type,party_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'SUPPLIER',?,'ENABLED',now(),NULL,
                    now(),'test-s07-contract',now(),'test-s07-contract',0)
                """, nextId(), userId, partyId);
    }

    private void assignRolePermission(String roleId, String permissionId) {
        jdbc.update("""
                INSERT INTO iam_role_permission (id,role_id,permission_id,created_at,created_by)
                VALUES (?,?,?,now(),'test-s07-contract')
                """, nextId(), roleId, permissionId);
    }

    private String permissionId(String code) {
        return jdbc.queryForObject("SELECT id FROM iam_permission WHERE code=?", String.class, code);
    }

    private String roleId(String code) {
        return jdbc.queryForObject("SELECT id FROM iam_role WHERE code=?", String.class, code);
    }

    private long userVersion(String userId) {
        return jdbc.queryForObject("SELECT version FROM iam_user WHERE id=?", Long.class, userId);
    }

    private long roleVersion(String roleId) {
        return jdbc.queryForObject("SELECT version FROM iam_role WHERE id=?", Long.class, roleId);
    }

    private List<String> roleIds(String userId) {
        return jdbc.queryForList(
                "SELECT role_id FROM iam_user_role WHERE user_id=? ORDER BY role_id", String.class, userId);
    }

    private List<String> factoryIds(String userId) {
        return jdbc.queryForList(
                "SELECT factory_id FROM iam_user_factory_scope WHERE user_id=? ORDER BY factory_id",
                String.class, userId);
    }

    private List<String> permissionIds(String roleId) {
        return jdbc.queryForList(
                "SELECT permission_id FROM iam_role_permission WHERE role_id=? ORDER BY permission_id",
                String.class, roleId);
    }

    private boolean mobileEnabled(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT status='ENABLED' FROM iam_user_application
                 WHERE user_id=? AND application_code='MOM_MOBILE_PDA'
                """, Boolean.class, userId));
    }

    private String partyId(String userId) {
        return jdbc.queryForObject(
                "SELECT party_id FROM iam_external_user_binding WHERE user_id=?", String.class, userId);
    }

    private int successAuditCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM iam_security_audit_event
                 WHERE result='SUCCESS' AND target_id LIKE '779%'
                """, Integer.class);
    }

    private void installVersionFailureTrigger(String userId) {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_s07_user_version_update()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.id = '%s' AND NEW.version <> OLD.version THEN
                        RAISE EXCEPTION USING
                            ERRCODE = '23514',
                            MESSAGE = 'forced aggregate version failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(userId));
        jdbc.execute("""
                CREATE TRIGGER fail_s07_user_version_update_trigger
                BEFORE UPDATE OF version ON iam_user
                FOR EACH ROW EXECUTE FUNCTION fail_s07_user_version_update()
                """);
    }

    private void dropVersionFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_s07_user_version_update_trigger ON iam_user");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_s07_user_version_update()");
    }

    private static String nextId() {
        return Long.toString(IDS.incrementAndGet());
    }
}
