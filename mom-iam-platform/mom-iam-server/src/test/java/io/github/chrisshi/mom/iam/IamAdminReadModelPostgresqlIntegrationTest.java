package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S09 用户授权与角色 Permission 只读投影 PostgreSQL E2E。 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.admin.enabled=true",
                "mom.iam.authorization.key.allow-test-key=true",
                "mom.iam.session.hmac-pepper=s09-read-model-refresh-hmac-pepper-2026-secure",
                "mom.iam.session.allow-local-pepper=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class IamAdminReadModelPostgresqlIntegrationTest {
    private static final String USER_ID = "779000000000000001";
    private static final String SUPPLIER_ID = "779000000000000002";
    private static final String FACTORY_ID = "779000000000000003";
    private static final String PARTY_ID = "779000000000000004";

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
        jdbc.update("DELETE FROM iam_user_factory_scope WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user_application WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user_role WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_external_user_binding WHERE user_id LIKE '779%'");
        jdbc.update("DELETE FROM iam_user WHERE id LIKE '779%'");
        insertUser(USER_ID, "s09-internal", UserType.INTERNAL);
        insertUser(SUPPLIER_ID, "s09-supplier", UserType.SUPPLIER);
    }

    @Test
    void userAuthorizationAndRolePermissionSnapshotsMustReturnCurrentAssignments() throws Exception {
        String roleId = jdbc.queryForObject(
                "SELECT id FROM iam_role WHERE code='PLATFORM_ADMIN'", String.class);
        String permissionId = jdbc.queryForObject(
                "SELECT id FROM iam_permission WHERE code='iam:user:read'", String.class);
        jdbc.update("""
                INSERT INTO iam_user_role (
                    id,user_id,role_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES ('779000000000000011',?,?,'ENABLED',NULL,NULL,
                    now(),'test-s09',now(),'test-s09',0)
                """, USER_ID, roleId);
        jdbc.update("""
                INSERT INTO iam_user_factory_scope (
                    id,user_id,factory_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES ('779000000000000012',?,?,'ENABLED',NULL,NULL,
                    now(),'test-s09',now(),'test-s09',0)
                """, USER_ID, FACTORY_ID);
        jdbc.update("""
                INSERT INTO iam_user_application (
                    id,user_id,application_code,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES ('779000000000000013',?,'MOM_MOBILE_PDA','ENABLED',NULL,NULL,
                    now(),'test-s09',now(),'test-s09',0)
                """, USER_ID);

        mockMvc.perform(get("/api/iam/admin/users/{id}/authorizations", USER_ID)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleIds", hasItem(roleId)))
                .andExpect(jsonPath("$.factoryIds", hasItem(FACTORY_ID)))
                .andExpect(jsonPath("$.mobileAccessEnabled").value(true))
                .andExpect(jsonPath("$.partyBinding").doesNotExist());

        mockMvc.perform(get("/api/iam/admin/roles/{id}/permissions", roleId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionIds", hasItem(permissionId)));
    }

    @Test
    void externalPartySnapshotNotFoundAndPermissionDenialMustUseStableErrors() throws Exception {
        jdbc.update("""
                INSERT INTO iam_external_user_binding (
                    id,user_id,party_type,party_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES ('779000000000000021',?,'SUPPLIER',?,'ENABLED',now(),NULL,
                    now(),'test-s09',now(),'test-s09',0)
                """, SUPPLIER_ID, PARTY_ID);

        mockMvc.perform(get("/api/iam/admin/users/{id}/authorizations", SUPPLIER_ID)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyBinding.partyType").value("SUPPLIER"))
                .andExpect(jsonPath("$.partyBinding.partyId").value(PARTY_ID));

        mockMvc.perform(get("/api/iam/admin/users/{id}/authorizations", "779999999999999999")
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        String roleId = jdbc.queryForObject(
                "SELECT id FROM iam_role WHERE code='PLATFORM_ADMIN'", String.class);
        mockMvc.perform(get("/api/iam/admin/roles/{id}/permissions", roleId)
                        .with(userReadOnlyJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("779000000000000090")
                        .audience(List.of("mom-admin-web"))
                        .claim("sid", "779000000000000091")
                        .claim("client_id", "mom-admin-web")
                        .claim("user_type", "INTERNAL")
                        .claim("roles", List.of("PLATFORM_ADMIN"))
                        .claim("permissions", List.of("iam:user:read", "iam:role:read"))
                        .claim("factory_ids", List.of()))
                .authorities(
                        new SimpleGrantedAuthority("iam:user:read"),
                        new SimpleGrantedAuthority("iam:role:read"));
    }

    private RequestPostProcessor userReadOnlyJwt() {
        return jwt().jwt(token -> token
                        .subject("779000000000000090")
                        .audience(List.of("mom-admin-web"))
                        .claim("sid", "779000000000000091")
                        .claim("client_id", "mom-admin-web")
                        .claim("user_type", "INTERNAL")
                        .claim("roles", List.of("SECURITY_AUDITOR"))
                        .claim("permissions", List.of("iam:user:read"))
                        .claim("factory_ids", List.of()))
                .authorities(new SimpleGrantedAuthority("iam:user:read"));
    }

    private void insertUser(String id, String username, UserType type) {
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?, ?,?,'ENABLED',0,false,
                    now(),'test-s09',now(),'test-s09',0,false)
                """, id, username, passwordEncoder.encode("S09-Initial-Secret-123!"),
                "S09 " + username, type.name());
    }
}
