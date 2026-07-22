package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S03 真实 PostgreSQL JDBC Store、账号状态、PKCE、OIDC、JWT 与 CSRF 集成测试。 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.authorization.security.max-failed-attempts=3",
                "mom.iam.authorization.security.lock-duration=5m",
                "mom.iam.authorization.security.minimum-password-length=12",
                "mom.iam.authorization.key.allow-test-key=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class IamAuthorizationServerPostgresqlIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(720_000_000_000_000_000L);
    private static final String INITIAL_PASSWORD = "InitialPass123!";
    private static final String NEW_PASSWORD = "ChangedPass456!";
    private static final String VERIFIER =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~abc";

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
    @Autowired RegisteredClientRepository registeredClients;
    @Autowired JwtDecoder jwtDecoder;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM iam_user_application");
        jdbc.update("DELETE FROM iam_external_user_binding");
        jdbc.update("DELETE FROM iam_user WHERE deleted = false");
    }

    @Test
    void fourPublicClientsDiscoveryAndJwkMustMatchFrozenProtocol() throws Exception {
        for (String clientId : List.of(
                "mom-admin-web", "mom-supplier-web", "mom-customer-web", "mom-mobile-pda")) {
            RegisteredClient client = registeredClients.findByClientId(clientId);
            assertNotNull(client);
            assertNull(client.getClientSecret());
            assertEquals(Set.of(ClientAuthenticationMethod.NONE), client.getClientAuthenticationMethods());
            assertEquals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), client.getAuthorizationGrantTypes());
            assertTrue(client.getClientSettings().isRequireProofKey());
            assertFalse(client.getClientSettings().isRequireAuthorizationConsent());
            assertEquals(Duration.ofMinutes(10), client.getTokenSettings().getAccessTokenTimeToLive());
            assertEquals(Set.of("openid", "profile"), client.getScopes());
        }

        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("oauth2/authorize")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("oauth2/token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("oauth2/jwks")));

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mom-iam-local-2026")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"d\""))));
    }

    @Test
    void authorizationEndpointMustRequireS256AndExactRedirectAndUserType() throws Exception {
        String internal = insertUser(UserType.INTERNAL, "internal", false, "ENABLED", 0, null);

        mockMvc.perform(get("/oauth2/authorize")
                        .with(user(internal).roles("IAM_USER"))
                        .param("response_type", "code")
                        .param("client_id", "mom-admin-web")
                        .param("redirect_uri", "http://127.0.0.1:5173/auth/callback")
                        .param("scope", "openid profile"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/oauth2/authorize")
                        .with(user(internal).roles("IAM_USER"))
                        .param("response_type", "code")
                        .param("client_id", "mom-admin-web")
                        .param("redirect_uri", "http://127.0.0.1:5173/auth/callback")
                        .param("scope", "openid profile")
                        .param("code_challenge", VERIFIER)
                        .param("code_challenge_method", "plain"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(validAuthorizationRequest("mom-admin-web", internal,
                        "http://attacker.invalid/callback"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(validAuthorizationRequest("mom-supplier-web", internal,
                        "http://127.0.0.1:5174/auth/callback"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fourClientsMustIssueCodesOnlyForMatchingUsersAndMobileAccess() throws Exception {
        String internal = insertUser(UserType.INTERNAL, "admin", false, "ENABLED", 0, null);
        String supplier = insertUser(UserType.SUPPLIER, "supplier", false, "ENABLED", 0, null);
        String customer = insertUser(UserType.CUSTOMER, "customer", false, "ENABLED", 0, null);
        String mobile = insertUser(UserType.INTERNAL, "mobile", false, "ENABLED", 0, null);
        insertExternalBinding(supplier, "SUPPLIER");
        insertExternalBinding(customer, "CUSTOMER");
        insertMobileAccess(mobile);

        assertNotNull(authorize("mom-admin-web", internal,
                "http://127.0.0.1:5173/auth/callback"));
        assertNotNull(authorize("mom-supplier-web", supplier,
                "http://127.0.0.1:5174/auth/callback"));
        assertNotNull(authorize("mom-customer-web", customer,
                "http://127.0.0.1:5175/auth/callback"));
        assertNotNull(authorize("mom-mobile-pda", mobile,
                "com.mom.mobile:/oauth2/callback"));

        String noMobileAccess = insertUser(
                UserType.INTERNAL, "no-mobile", false, "ENABLED", 0, null);
        mockMvc.perform(validAuthorizationRequest("mom-mobile-pda", noMobileAccess,
                        "com.mom.mobile:/oauth2/callback"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountPasswordFailuresMustLockAndSuccessMustClearState() throws Exception {
        String success = insertUser(UserType.INTERNAL, "success", false, "ENABLED", 2, null);
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", success)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection());
        Map<String, Object> successfulState = accountState(success);
        assertEquals(0, successfulState.get("failed_login_count"));
        assertNull(successfulState.get("locked_until"));
        assertNotNull(successfulState.get("last_login_at"));

        String wrong = insertUser(UserType.INTERNAL, "wrong", false, "ENABLED", 0, null);
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/login").with(csrf())
                            .param("username", wrong)
                            .param("password", "WrongPassword!"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }
        Map<String, Object> lockedState = accountState(wrong);
        assertEquals(3, lockedState.get("failed_login_count"));
        assertNotNull(lockedState.get("locked_until"));

        String disabled = insertUser(UserType.INTERNAL, "disabled", false, "DISABLED", 0, null);
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", disabled)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        assertEquals(0, accountState(disabled).get("failed_login_count"));

        String preLocked = insertUser(UserType.INTERNAL, "locked", false, "ENABLED", 1,
                Instant.now().plusSeconds(300));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", preLocked)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        assertEquals(1, accountState(preLocked).get("failed_login_count"));
    }

    @Test
    void firstPasswordChangeMustBeMandatoryAndCsrfProtected() throws Exception {
        String username = insertUser(UserType.INTERNAL, "first-change", true, "ENABLED", 0, null);

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/password/change"));

        mockMvc.perform(post("/password/change")
                        .with(user(username).roles("IAM_USER"))
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmation", NEW_PASSWORD))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/password/change")
                        .with(user(username).roles("IAM_USER"))
                        .with(csrf())
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmation", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection());

        Map<String, Object> state = jdbc.queryForMap(
                "SELECT password_hash,password_change_required FROM iam_user WHERE username = ?", username);
        assertEquals(false, state.get("password_change_required"));
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, state.get("password_hash").toString()));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }

    @Test
    void authorizationCodeMustUseCorrectVerifierOnlyOnceAndIssueJwtAndIdToken() throws Exception {
        String username = insertUser(UserType.INTERNAL, "token-user", false, "ENABLED", 0, null);
        String redirectUri = "http://127.0.0.1:5173/auth/callback";
        String code = authorize("mom-admin-web", username, redirectUri);

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "mom-admin-web")
                        .param("redirect_uri", redirectUri)
                        .param("code", code)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn();
        String body = tokenResult.getResponse().getContentAsString();
        String accessToken = jsonString(body, "access_token");
        assertNotNull(jsonString(body, "id_token"));
        assertEquals("Bearer", jsonString(body, "token_type"));
        assertEquals(600, jsonNumber(body, "expires_in"));

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertEquals(usernameId(username), jwt.getSubject());
        assertEquals("mom-admin-web", jwt.getClaimAsString("client_id"));
        assertEquals("INTERNAL", jwt.getClaimAsString("user_type"));
        assertEquals(600, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        assertEquals("mom-iam-local-2026", jwt.getHeaders().get("kid"));
        assertNull(jwt.getClaim("roles"));
        assertNull(jwt.getClaim("permissions"));
        assertNull(jwt.getClaim("factory_ids"));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "mom-admin-web")
                        .param("redirect_uri", redirectUri)
                        .param("code", code)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest());

        String secondCode = authorize("mom-admin-web", username, redirectUri);
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "mom-admin-web")
                        .param("redirect_uri", redirectUri)
                        .param("code", secondCode)
                        .param("code_verifier", "incorrect-verifier-value-abcdefghijklmnopqrstuvwxyz"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validAuthorizationRequest(
            String clientId, String username, String redirectUri) throws Exception {
        return get("/oauth2/authorize")
                .with(user(username).roles("IAM_USER"))
                .param("response_type", "code")
                .param("client_id", clientId)
                .param("redirect_uri", redirectUri)
                .param("scope", "openid profile")
                .param("state", "state-value")
                .param("nonce", "nonce-value")
                .param("code_challenge", challenge(VERIFIER))
                .param("code_challenge_method", "S256");
    }

    private String authorize(String clientId, String username, String redirectUri) throws Exception {
        MvcResult result = mockMvc.perform(validAuthorizationRequest(clientId, username, redirectUri))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertNotNull(location);
        String code = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("code");
        assertNotNull(code, location);
        return code;
    }

    private String insertUser(
            UserType userType,
            String suffix,
            boolean passwordChangeRequired,
            String status,
            int failedCount,
            Instant lockedUntil) {
        String id = nextId();
        String username = suffix + "-" + id.substring(id.length() - 5);
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,locked_until,password_change_required,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?,?,?,?,?,?,?,now(),'test-s03',now(),'test-s03',0,false)
                """, id, username, passwordEncoder.encode(INITIAL_PASSWORD),
                "S03 Test User", userType.name(), status, failedCount, lockedUntil,
                passwordChangeRequired);
        return username;
    }

    private void insertExternalBinding(String username, String partyType) {
        jdbc.update("""
                INSERT INTO iam_external_user_binding (
                    id,user_id,party_type,party_id,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?, 'ENABLED',now()-interval '1 minute',now()+interval '1 day',
                    now(),'test-s03',now(),'test-s03',0)
                """, nextId(), usernameId(username), partyType, nextId());
    }

    private void insertMobileAccess(String username) {
        jdbc.update("""
                INSERT INTO iam_user_application (
                    id,user_id,application_code,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'MOM_MOBILE_PDA','ENABLED',now()-interval '1 minute',now()+interval '1 day',
                    now(),'test-s03',now(),'test-s03',0)
                """, nextId(), usernameId(username));
    }

    private Map<String, Object> accountState(String username) {
        return jdbc.queryForMap("""
                SELECT failed_login_count,locked_until,last_login_at
                FROM iam_user WHERE username = ?
                """, username);
    }

    private String usernameId(String username) {
        return jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE username = ?", String.class, username);
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

    private static long jsonNumber(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field)
                        + "\\\"\\s*:\\s*(\\d+)")
                .matcher(json);
        assertTrue(matcher.find(), json);
        return Long.parseLong(matcher.group(1));
    }

    private static String nextId() {
        return Long.toString(IDS.incrementAndGet());
    }
}
