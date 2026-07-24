package io.github.chrisshi.mom.iam;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.security.IamRefreshTokenCodec;
import io.github.chrisshi.mom.iam.security.IamRevokedSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** S05 PostgreSQL + Redis 用户授权 Session、Opaque Refresh Rotation 与重放检测验收。 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.iam.authorization.key.allow-test-key=true",
                "mom.iam.session.hmac-pepper=s05-integration-refresh-hmac-pepper-2026-secure",
                "mom.iam.session.allow-local-pepper=true",
                "server.servlet.session.cookie.secure=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class IamSessionRefreshPostgresqlRedisIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(750_000_000_000_000_000L);
    private static final String PASSWORD = "S05-Password-123!";
    private static final String VERIFIER =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~s05";

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
    @Autowired JwtDecoder jwtDecoder;
    @Autowired IamRefreshTokenCodec tokenCodec;
    @Autowired IamRevokedSessionStore revokedSessions;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        RedisConnection connection = Objects.requireNonNull(
                redis.getConnectionFactory()).getConnection();
        try {
            connection.serverCommands().flushDb();
        }
        finally {
            connection.close();
        }
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM iam_refresh_token");
        jdbc.update("DELETE FROM iam_user_session");
        jdbc.update("DELETE FROM iam_user_application");
        jdbc.update("DELETE FROM iam_external_user_binding");
        jdbc.update("DELETE FROM iam_user_factory_scope");
        jdbc.update("DELETE FROM iam_user_role");
        jdbc.update("DELETE FROM iam_user WHERE id LIKE '75%'");
    }

    @Test
    void webRefreshMustRotateOnceAndReplayMustCompromiseSession() throws Exception {
        TestUser user = insertUser(UserType.INTERNAL, "web");
        TokenResponse initial = issueAuthorizationCodeTokens(
                "mom-admin-web", user.username(), "http://127.0.0.1:5173/auth/callback");
        Jwt initialJwt = jwtDecoder.decode(initial.accessToken());
        String sessionId = initialJwt.getClaimAsString("sid");
        assertNotNull(sessionId);

        Map<String, Object> session = jdbc.queryForMap("""
                SELECT status,channel,login_at,absolute_expires_at,latest_access_token_expires_at
                  FROM iam_user_session WHERE id=?
                """, sessionId);
        assertEquals("ACTIVE", session.get("status"));
        assertEquals("WEB", session.get("channel"));
        assertDurationSeconds(session, "login_at", "absolute_expires_at", 8 * 60 * 60);
        assertDurationSeconds(session, "login_at", "latest_access_token_expires_at", 10 * 60);

        Map<String, Object> firstRefresh = jdbc.queryForMap("""
                SELECT token_digest,sequence_no,status,expires_at
                  FROM iam_refresh_token WHERE session_id=?
                """, sessionId);
        assertEquals(1L, ((Number) firstRefresh.get("sequence_no")).longValue());
        assertEquals("ACTIVE", firstRefresh.get("status"));
        assertEquals(64, firstRefresh.get("token_digest").toString().length());
        assertEquals(tokenCodec.digest(initial.refreshToken()), firstRefresh.get("token_digest"));
        assertNotEquals(initial.refreshToken(), firstRefresh.get("token_digest"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE refresh_token_value IS NOT NULL",
                Integer.class));

        MvcResult rotatedResult = refresh("mom-admin-web", initial.refreshToken())
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse rotated = tokenResponse(rotatedResult.getResponse().getContentAsString());
        assertNotEquals(initial.refreshToken(), rotated.refreshToken());
        Jwt rotatedJwt = jwtDecoder.decode(rotated.accessToken());
        assertEquals(sessionId, rotatedJwt.getClaimAsString("sid"));
        assertEquals(600, Duration.between(
                rotatedJwt.getIssuedAt(), rotatedJwt.getExpiresAt()).toSeconds());

        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM iam_refresh_token
                 WHERE session_id=? AND status='ACTIVE'
                """, Integer.class, sessionId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM iam_refresh_token
                 WHERE session_id=? AND status='ROTATED' AND consumed_at IS NOT NULL
                """, Integer.class, sessionId));
        assertEquals(2L, jdbc.queryForObject("""
                SELECT sequence_no FROM iam_refresh_token
                 WHERE session_id=? AND status='ACTIVE'
                """, Long.class, sessionId));
        Timestamp absoluteAfterRotation = jdbc.queryForObject("""
                SELECT absolute_expires_at FROM iam_user_session WHERE id=?
                """, Timestamp.class, sessionId);
        assertEquals(((Timestamp) session.get("absolute_expires_at")).toInstant(),
                absoluteAfterRotation.toInstant());

        refresh("mom-admin-web", initial.refreshToken())
                .andExpect(status().isBadRequest());
        assertEquals("COMPROMISED", jdbc.queryForObject(
                "SELECT status FROM iam_user_session WHERE id=?", String.class, sessionId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM iam_refresh_token
                 WHERE session_id=? AND status='ACTIVE'
                """, Integer.class, sessionId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM iam_refresh_token
                 WHERE session_id=? AND status='REVOKED' AND revoked_at IS NOT NULL
                """, Integer.class, sessionId));
        assertTrue(revokedSessions.isRevoked(sessionId));

        refresh("mom-admin-web", rotated.refreshToken())
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentRefreshMustAllowExactlyOneRotation() throws Exception {
        TestUser user = insertUser(UserType.INTERNAL, "concurrent");
        TokenResponse initial = issueAuthorizationCodeTokens(
                "mom-admin-web", user.username(), "http://127.0.0.1:5173/auth/callback");
        String sessionId = jwtDecoder.decode(initial.accessToken()).getClaimAsString("sid");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(
                    () -> concurrentRefreshStatus(initial.refreshToken(), ready, start));
            Future<Integer> second = executor.submit(
                    () -> concurrentRefreshStatus(initial.refreshToken(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "两个刷新请求未能同时就绪");
            start.countDown();

            int firstStatus = first.get(30, TimeUnit.SECONDS);
            int secondStatus = second.get(30, TimeUnit.SECONDS);
            assertEquals(1, (firstStatus == 200 ? 1 : 0) + (secondStatus == 200 ? 1 : 0));
            assertEquals(1, (firstStatus == 400 ? 1 : 0) + (secondStatus == 400 ? 1 : 0));
        }
        finally {
            executor.shutdownNow();
        }

        assertEquals("COMPROMISED", jdbc.queryForObject(
                "SELECT status FROM iam_user_session WHERE id=?", String.class, sessionId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM iam_refresh_token
                 WHERE session_id=? AND status='ACTIVE'
                """, Integer.class, sessionId));
        assertTrue(revokedSessions.isRevoked(sessionId));
    }

    @Test
    void mobileSessionMustUseTwelveHourAbsoluteExpiry() throws Exception {
        TestUser user = insertUser(UserType.INTERNAL, "mobile");
        jdbc.update("""
                INSERT INTO iam_user_application (
                    id,user_id,application_code,status,valid_from,valid_until,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,'MOM_MOBILE_PDA','ENABLED',now()-interval '1 minute',now()+interval '1 day',
                    now(),'test-s05',now(),'test-s05',0)
                """, nextId(), user.id());

        TokenResponse initial = issueAuthorizationCodeTokens(
                "mom-mobile-pda", user.username(), "com.mom.mobile:/oauth2/callback");
        Jwt jwt = jwtDecoder.decode(initial.accessToken());
        Map<String, Object> session = jdbc.queryForMap("""
                SELECT channel,login_at,absolute_expires_at
                  FROM iam_user_session WHERE id=?
                """, jwt.getClaimAsString("sid"));
        assertEquals("MOBILE", session.get("channel"));
        assertDurationSeconds(session, "login_at", "absolute_expires_at", 12 * 60 * 60);
        mockMvc.perform(get("/api/iam/me")
                        .header("Authorization", "Bearer " + initial.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("mom-mobile-pda"))
                .andExpect(jsonPath("$.sid").value(jwt.getClaimAsString("sid")))
                .andExpect(jsonPath("$.mobileAccessEnabled").value(true))
                .andExpect(jsonPath("$.partyType").doesNotExist())
                .andExpect(jsonPath("$.partyId").doesNotExist());

        jdbc.update("""
                UPDATE iam_user_application SET status='DISABLED',updated_at=now()
                 WHERE user_id=? AND application_code='MOM_MOBILE_PDA'
                """, user.id());
        mockMvc.perform(get("/api/iam/me")
                        .header("Authorization", "Bearer " + initial.accessToken()))
                .andExpect(status().isForbidden());
    }

    private TokenResponse issueAuthorizationCodeTokens(
            String clientId, String username, String redirectUri) throws Exception {
        String code = authorize(clientId, username, redirectUri);
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("code", code)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse response = tokenResponse(result.getResponse().getContentAsString());
        assertNotNull(response.idToken());
        return response;
    }

    private org.springframework.test.web.servlet.ResultActions refresh(
            String clientId, String refreshToken) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("client_id", clientId)
                .param("refresh_token", refreshToken));
    }

    private int concurrentRefreshStatus(
            String refreshToken, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS), "刷新请求未收到并发开始信号");
        return refresh("mom-admin-web", refreshToken)
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String authorize(String clientId, String username, String redirectUri) throws Exception {
        String uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid")
                .queryParam("state", "s05-state")
                .queryParam("nonce", "s05-nonce")
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

    private TestUser insertUser(UserType userType, String suffix) {
        String id = nextId();
        String username = suffix + "-" + id.substring(id.length() - 6);
        jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,
                    failed_login_count,password_change_required,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?, ?,?,'ENABLED',0,false,
                    now(),'test-s05',now(),'test-s05',0,false)
                """, id, username, passwordEncoder.encode(PASSWORD),
                "S05 " + suffix, userType.name());
        return new TestUser(id, username);
    }

    private static void assertDurationSeconds(
            Map<String, Object> values, String startName, String endName, long expected) {
        Instant start = ((Timestamp) values.get(startName)).toInstant();
        Instant end = ((Timestamp) values.get(endName)).toInstant();
        assertEquals(expected, Duration.between(start, end).toSeconds());
    }

    private static TokenResponse tokenResponse(String json) {
        String accessToken = jsonString(json, "access_token");
        String refreshToken = jsonString(json, "refresh_token");
        assertNotNull(accessToken, json);
        assertNotNull(refreshToken, json);
        return new TokenResponse(accessToken, refreshToken, jsonString(json, "id_token"));
    }

    private static String jsonString(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field)
                        + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String nextId() {
        return Long.toString(IDS.incrementAndGet());
    }

    private record TestUser(String id, String username) {
    }

    private record TokenResponse(String accessToken, String refreshToken, String idToken) {
    }
}
