package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContext;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContextService;
import io.github.chrisshi.mom.iam.security.IamScopeGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S04 真实 PostgreSQL RBAC、Factory/Party Scope、JWT Claims、Me API 与 404 契约测试。 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.authorization.key.allow-test-key=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class IamAuthorizationContextPostgresqlIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(740_000_000_000_000_000L);
    private static final String PASSWORD = "S04-Password-123!";
    private static final String VERIFIER =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~s04";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            AbstractIamPostgresqlIntegrationTest.newPostgresqlContainer();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        AbstractIamPostgresqlIntegrationTest.registerDatabaseProperties(registry, POSTGRESQL);
    }

    @Autowired WebApplicationContext applicationContext;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired IamAuthorizationContextService contexts;
    @Autowired IamScopeGuard scopeGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM iam_refresh_token");
        jdbc.update("DELETE FROM iam_user_session");
        jdbc.update("DELETE FROM iam_user_factory_scope");
        jdbc.update("DELETE FROM iam_external_user_binding");
        jdbc.update("DELETE FROM iam_user_role");
        jdbc.update("DELETE FROM iam_role_permission WHERE role_id LIKE '74%'");
        jdbc.update("DELETE FROM iam_role WHERE id LIKE '74%'");
        jdbc.update("DELETE FROM iam_user WHERE id LIKE '74%'");
    }

    @Test
    void internalContextMustDriveJwtMeCurrentFactoryAndNotFoundContract() throws Exception {
        TestUser user = insertUser(UserType.INTERNAL, "internal");
        assignBuiltInRole(user.id(), "IAM_ADMIN", "ENABLED", null, null);
        assignBuiltInRole(user.id(), "PLATFORM_ADMIN", "ENABLED",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(60));

        String allowedFactory = nextId();
        grantFactory(user.id(), allowedFactory, "ENABLED", null, null);
        grantFactory(user.id(), nextId(), "DISABLED", null, null);
        grantFactory(user.id(), nextId(), "ENABLED",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(60));

        String accessToken = issueAccessToken(
                "mom-admin-web", user.username(), "http://127.0.0.1:5173/auth/callback");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertEquals(List.of("IAM_ADMIN"), jwt.getClaimAsStringList("roles"));
        assertTrue(jwt.getClaimAsStringList("permissions").contains("iam:user:read"));
        assertEquals(List.of(allowedFactory), jwt.getClaimAsStringList("factory_ids"));
        assertNull(jwt.getClaim("party_type"));
        assertNull(jwt.getClaim("party_id"));

        mockMvc.perform(get("/api/iam/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Factory-Id", allowedFactory))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.id()))
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.userType").value("INTERNAL"))
                .andExpect(jsonPath("$.clientId").value("mom-admin-web"))
                .andExpect(jsonPath("$.roles[0]").value("IAM_ADMIN"))
                .andExpect(jsonPath("$.factoryIds[0]").value(allowedFactory))
                .andExpect(jsonPath("$.currentFactoryId").value(allowedFactory));

        mockMvc.perform(get("/api/iam/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Factory-Id", nextId()))
                .andExpect(status().isForbidden());

        IamAuthorizationContext context = contexts.loadByUserId(user.id());
        assertDoesNotThrow(() -> scopeGuard.requireObjectVisible(
                context, allowedFactory, null, null));
        assertThrows(IamScopeGuard.ScopedResourceNotFoundException.class,
                () -> scopeGuard.requireObjectVisible(context, nextId(), null, null));
    }

    @Test
    void externalContextMustFixPartyAndHideMismatchedObjects() throws Exception {
        TestUser user = insertUser(UserType.SUPPLIER, "supplier");
        String roleId = createRole("SUPPLIER_OPERATOR", UserType.SUPPLIER);
        grantPermission(roleId, "iam:user:read");
        assignRole(user.id(), roleId, "ENABLED", null, null);

        String partyId = nextId();
        bindParty(user.id(), PartyType.SUPPLIER, partyId);
        String factoryId = nextId();
        grantFactory(user.id(), factoryId, "ENABLED", null, null);

        String accessToken = issueAccessToken(
                "mom-supplier-web", user.username(), "http://127.0.0.1:5174/auth/callback");
        Jwt jwt = jwtDecoder.decode(accessToken);
        List<String> roles = jwt.getClaimAsStringList("roles");
        assertEquals(1, roles.size());
        assertTrue(roles.getFirst().startsWith("SUPPLIER_OPERATOR_"));
        assertEquals("SUPPLIER", jwt.getClaimAsString("party_type"));
        assertEquals(partyId, jwt.getClaimAsString("party_id"));
        assertEquals(List.of(factoryId), jwt.getClaimAsStringList("factory_ids"));

        mockMvc.perform(get("/api/iam/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyType").value("SUPPLIER"))
                .andExpect(jsonPath("$.partyId").value(partyId));

        IamAuthorizationContext context = contexts.loadByUsername(user.username());
        assertDoesNotThrow(() -> scopeGuard.requireObjectVisible(
                context, factoryId, PartyType.SUPPLIER, partyId));
        assertThrows(IamScopeGuard.ScopedResourceNotFoundException.class,
                () -> scopeGuard.requireObjectVisible(
                        context, factoryId, PartyType.SUPPLIER, nextId()));
        assertThrows(IamScopeGuard.ScopedResourceNotFoundException.class,
                () -> scopeGuard.requireObjectVisible(
                        context, factoryId, PartyType.CUSTOMER, partyId));
    }

    private String issueAccessToken(String clientId, String username, String redirectUri) throws Exception {
        String code = authorize(clientId, username, redirectUri);
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("code", code)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn();
        String body = tokenResult.getResponse().getContentAsString();
        String token = jsonString(body, "access_token");
        assertNotNull(token, body);
        assertNotNull(jsonString(body, "refresh_token"), body);
        return token;
    }

    private String authorize(String clientId, String username, String redirectUri) throws Exception {
        String uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid")
                .queryParam("state", "s04-state")
                .queryParam("nonce", "s04-nonce")
                .queryParam("code_challenge", challenge(VERIFIER))
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
        MvcResult result = mockMvc.perform(get(uri).with(authenticatedPasswordUser(username)))
                .andReturn();
        assertTrue(result.getResponse().getStatus() >= 300
                        && result.getResponse().getStatus() < 400,
                "status=" + result.getResponse().getStatus()
                        + ", error=" + result.getResponse().getErrorMessage()
                        + ", body=" + result.getResponse().getContentAsString());
        String location = result.getResponse().getRedirectedUrl();
        assertNotNull(location);
        String code = UriComponentsBuilder.fromUriString(location)
                .build().getQueryParams().getFirst("code");
        assertNotNull(code, location);
        return code;
    }

    private static RequestPostProcessor authenticatedPasswordUser(String username) {
        return user(username).authorities(
                new SimpleGrantedAuthority("ROLE_IAM_USER"),
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                        .issuedAt(Instant.parse("2026-07-22T00:00:00Z"))
                        .build());
    }

    private TestUser insertUser(UserType type, String suffix) {
        String id = nextId();
        String username = suffix + "-" + id.substring(id.length() - 6);
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?, ?,?,'ENABLED',0,false,
                    now(),'test-s04',now(),'test-s04',0,false)
                """, id, username, passwordEncoder.encode(PASSWORD),
                "S04 " + suffix, type.name());
        return new TestUser(id, username);
    }

    private void assignBuiltInRole(
            String userId, String roleCode, String status, Instant validFrom, Instant validUntil) {
        String roleId = jdbc.queryForObject(
                "SELECT id FROM iam_role WHERE code = ?", String.class, roleCode);
        assignRole(userId, roleId, status, validFrom, validUntil);
    }

    private void assignRole(
            String userId, String roleId, String status, Instant validFrom, Instant validUntil) {
        jdbc.update("""
                INSERT INTO iam_user_role (
                    id,user_id,role_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?,?,?,now(),'test-s04',now(),'test-s04',0)
                """, nextId(), userId, roleId, status,
                timestamp(validFrom), timestamp(validUntil));
    }

    private String createRole(String code, UserType userType) {
        String id = nextId();
        jdbc.update("""
                INSERT INTO iam_role (
                    id,code,name,applicable_user_type,status,built_in,description,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?,?,'ENABLED',false,'S04 测试角色',
                    now(),'test-s04',now(),'test-s04',0,false)
                """, id, code + "_" + id.substring(id.length() - 4),
                "S04 Test Role", userType.name());
        return id;
    }

    private void grantPermission(String roleId, String permissionCode) {
        String permissionId = jdbc.queryForObject(
                "SELECT id FROM iam_permission WHERE code = ?", String.class, permissionCode);
        jdbc.update("""
                INSERT INTO iam_role_permission (
                    id,role_id,permission_id,created_at,created_by)
                VALUES (?,?,?,now(),'test-s04')
                """, nextId(), roleId, permissionId);
    }

    private void grantFactory(
            String userId, String factoryId, String status, Instant validFrom, Instant validUntil) {
        jdbc.update("""
                INSERT INTO iam_user_factory_scope (
                    id,user_id,factory_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?,?,?,now(),'test-s04',now(),'test-s04',0)
                """, nextId(), userId, factoryId, status,
                timestamp(validFrom), timestamp(validUntil));
    }

    private void bindParty(String userId, PartyType type, String partyId) {
        jdbc.update("""
                INSERT INTO iam_external_user_binding (
                    id,user_id,party_type,party_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?,'ENABLED',now()-interval '1 minute',now()+interval '1 day',
                    now(),'test-s04',now(),'test-s04',0)
                """, nextId(), userId, type.name(), partyId);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String jsonString(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field)
                        + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String nextId() {
        return Long.toString(IDS.incrementAndGet());
    }

    private record TestUser(String id, String username) {
    }
}
