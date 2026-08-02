package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.authentication.IamFirstPartyLoginApplicationService;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.security.IamAccountAuthenticationService;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContext;
import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamAccessTokenIssuer;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 第一方 JSON 认证控制器的无容器契约测试。 */
@ExtendWith(MockitoExtension.class)
class IamDirectAuthenticationControllerTest {

    @Mock AuthenticationProvider authenticationProvider;
    @Mock IamAccountAuthenticationService accounts;
    @Mock IamClientAccessPolicyService clientAccess;
    @Mock IamSessionTokenService sessions;
    @Mock IamAccessTokenIssuer tokenIssuer;

    private IamDirectAuthenticationController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamFirstPartyLoginApplicationService loginService =
                new IamFirstPartyLoginApplicationService(
                authenticationProvider,
                accounts,
                clientAccess,
                sessions,
                tokenIssuer,
                new AuditContextExecutor());
        controller = new IamDirectAuthenticationController(loginService, sessions, tokenIssuer);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new IamDirectAuthenticationExceptionHandler())
                .build();
    }

    @Test
    void invalidCredentialsMustUseGenericFailureAndRecordAttempt() {
        when(authenticationProvider.authenticate(any()))
                .thenThrow(new BadCredentialsException("provider detail must not escape"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        IamFirstPartyLoginApplicationService.InvalidCredentialsException error = assertThrows(
                IamFirstPartyLoginApplicationService.InvalidCredentialsException.class,
                () -> controller.login(new IamDirectAuthenticationController.LoginRequest(
                        "admin", "wrong-password", "mom-admin-web", "browser"), request));

        assertEquals("账号或密码错误，或账号当前不可用", error.getMessage());
        verify(accounts).recordBadCredentials("admin");
        verify(clientAccess, never()).requireAuthorization(any(), any());
        verify(sessions, never()).issueInitial(any(), any(), any(), any(), any());
    }

    /** 账号锁定或禁用等 Provider 细节必须继续收敛为同一第一方错误，避免账号枚举。 */
    @Test
    void unavailableAccountMustNotExposeProviderReason() throws Exception {
        when(authenticationProvider.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.LockedException(
                        "locked_until=secret-internal-state"));

        mockMvc.perform(post("/api/iam/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"admin","password":"wrong",
                                "clientId":"mom-admin-web","deviceName":"browser"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("invalid_credentials"))
                .andExpect(jsonPath("$.message")
                        .value("账号或密码错误，或账号当前不可用"));

        verify(accounts, never()).recordBadCredentials("admin");
        verify(sessions, never()).issueInitial(any(), any(), any(), any(), any());
    }

    @Test
    void requiredPasswordChangeMustNotIssueSessionOrToken() {
        when(authenticationProvider.authenticate(any())).thenReturn(authenticated("admin"));
        when(accounts.requireAuditIdentity("admin")).thenReturn(auditIdentity());
        when(accounts.requiresPasswordChange("admin")).thenReturn(true);
        MockHttpServletRequest request = request();

        assertThrows(
                IamFirstPartyLoginApplicationService.PasswordChangeRequiredException.class,
                () -> controller.login(new IamDirectAuthenticationController.LoginRequest(
                        "admin", "TemporaryPass123!", "mom-admin-web", "browser"), request));

        verify(clientAccess).requireAuthorization("admin", "mom-admin-web");
        verify(accounts).recordSuccessfulLogin("admin");
        verify(sessions, never()).issueInitial(any(), any(), any(), any(), any());
        verify(tokenIssuer, never()).issue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void successfulLoginMustReuseAuthoritativeSessionAndJwtServices() {
        when(authenticationProvider.authenticate(any())).thenReturn(authenticated("admin"));
        when(accounts.requireAuditIdentity("admin")).thenReturn(auditIdentity());
        when(accounts.requiresPasswordChange("admin")).thenReturn(false);
        Instant issuedAt = Instant.parse("2026-07-26T10:00:00Z");
        Instant accessExpiresAt = issuedAt.plusSeconds(600);
        Instant sessionExpiresAt = issuedAt.plusSeconds(8 * 60 * 60);
        IamAuthorizationContext context = context("admin");
        when(sessions.issueInitial(
                "admin", "mom-admin-web", "127.0.0.1", "JUnit", "browser"))
                .thenReturn(new IamSessionTokenService.InitialIssue(
                        context,
                        "session-1",
                        "refresh-1",
                        issuedAt,
                        accessExpiresAt,
                        sessionExpiresAt));
        when(tokenIssuer.issue(
                context,
                "session-1",
                "mom-admin-web",
                issuedAt,
                accessExpiresAt,
                Set.of("openid", "profile")))
                .thenReturn(new IamAccessTokenIssuer.IssuedAccessToken(
                        "access-1", issuedAt, accessExpiresAt, Set.of("openid", "profile")));

        IamDirectAuthenticationController.TokenResponse response = controller.login(
                new IamDirectAuthenticationController.LoginRequest(
                        "admin", "ValidPassword123!", "mom-admin-web", "browser"),
                request());

        assertEquals("access-1", response.accessToken());
        assertEquals("refresh-1", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(600L, response.expiresIn());
        assertEquals("session-1", response.sessionId());
        assertEquals(accessExpiresAt, response.accessExpiresAt());
        assertEquals(sessionExpiresAt, response.sessionExpiresAt());
    }

    /** HTTP Method、Path、Content-Type、字段名称与 JSON 类型属于已发布第一方契约。 */
    @Test
    void successfulLoginHttpContractMustRemainStable() throws Exception {
        when(authenticationProvider.authenticate(any())).thenReturn(authenticated("admin"));
        when(accounts.requireAuditIdentity("admin")).thenReturn(auditIdentity());
        when(accounts.requiresPasswordChange("admin")).thenReturn(false);
        Instant issuedAt = Instant.parse("2026-07-26T10:00:00Z");
        Instant accessExpiresAt = issuedAt.plusSeconds(600);
        Instant sessionExpiresAt = issuedAt.plusSeconds(8 * 60 * 60);
        IamAuthorizationContext context = context("admin");
        when(sessions.issueInitial(
                "admin", "mom-admin-web", "127.0.0.1", null, "browser"))
                .thenReturn(new IamSessionTokenService.InitialIssue(
                        context, "session-1", "refresh-1", issuedAt,
                        accessExpiresAt, sessionExpiresAt));
        when(tokenIssuer.issue(
                context, "session-1", "mom-admin-web", issuedAt, accessExpiresAt,
                Set.of("openid", "profile")))
                .thenReturn(new IamAccessTokenIssuer.IssuedAccessToken(
                        "access-1", issuedAt, accessExpiresAt, Set.of("openid", "profile")));

        mockMvc.perform(post("/api/iam/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"admin","password":"ValidPassword123!",
                                "clientId":"mom-admin-web","deviceName":" browser "}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.accessExpiresAt").value("2026-07-26T10:10:00Z"))
                .andExpect(jsonPath("$.sessionExpiresAt").value("2026-07-26T18:00:00Z"));
    }

    /** 首次改密成功必须保持先修改凭据和记成功，再复用同一 Session/Token 签发路径。 */
    @Test
    void requiredPasswordChangeMustIssueThroughAuthoritativeServices() {
        when(authenticationProvider.authenticate(any())).thenReturn(authenticated("admin"));
        when(accounts.requireAuditIdentity("admin")).thenReturn(auditIdentity());
        when(accounts.requiresPasswordChange("admin")).thenReturn(true);
        Instant issuedAt = Instant.parse("2026-07-26T10:00:00Z");
        Instant accessExpiresAt = issuedAt.plusSeconds(600);
        IamAuthorizationContext context = context("admin");
        when(sessions.issueInitial(
                "admin", "mom-admin-web", "127.0.0.1", "JUnit", "browser"))
                .thenReturn(new IamSessionTokenService.InitialIssue(
                        context, "session-1", "refresh-1", issuedAt,
                        accessExpiresAt, issuedAt.plusSeconds(28800)));
        when(tokenIssuer.issue(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IamAccessTokenIssuer.IssuedAccessToken(
                        "access-1", issuedAt, accessExpiresAt, Set.of("openid", "profile")));

        IamDirectAuthenticationController.TokenResponse response =
                controller.changeRequiredPassword(
                        new IamDirectAuthenticationController.RequiredPasswordChangeRequest(
                                "admin", "TemporaryPass123!", "NewPassword123!",
                                "NewPassword123!", "mom-admin-web", "browser"),
                        request());

        assertEquals("access-1", response.accessToken());
        var order = org.mockito.Mockito.inOrder(accounts, sessions, tokenIssuer);
        order.verify(accounts).changeRequiredPassword(
                "admin", "NewPassword123!", "NewPassword123!");
        order.verify(accounts).recordSuccessfulLogin("admin");
        order.verify(sessions).issueInitial(
                "admin", "mom-admin-web", "127.0.0.1", "JUnit", "browser");
        order.verify(tokenIssuer).issue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void refreshMustRotateAndReturnOnlyTheSuccessorToken() {
        Instant issuedAt = Instant.parse("2026-07-26T10:10:00Z");
        Instant accessExpiresAt = issuedAt.plusSeconds(600);
        Instant sessionExpiresAt = issuedAt.plusSeconds(7 * 60 * 60);
        IamAuthorizationContext context = context("admin");
        when(sessions.rotate("refresh-1", "mom-admin-web"))
                .thenReturn(new IamSessionTokenService.Rotation(
                        context,
                        "session-1",
                        "refresh-2",
                        issuedAt,
                        accessExpiresAt,
                        sessionExpiresAt,
                        2L));
        when(tokenIssuer.issue(
                context,
                "session-1",
                "mom-admin-web",
                issuedAt,
                accessExpiresAt,
                Set.of("openid", "profile")))
                .thenReturn(new IamAccessTokenIssuer.IssuedAccessToken(
                        "access-2", issuedAt, accessExpiresAt, Set.of("openid", "profile")));

        IamDirectAuthenticationController.TokenResponse response = controller.refresh(
                new IamDirectAuthenticationController.RefreshRequest(
                        "mom-admin-web", "refresh-1"));

        assertEquals("access-2", response.accessToken());
        assertEquals("refresh-2", response.refreshToken());
        assertEquals("session-1", response.sessionId());
    }

    @Test
    void logoutMustRevokeSidFromVerifiedJwt() {
        Instant issuedAt = Instant.parse("2026-07-26T10:00:00Z");
        Jwt jwt = Jwt.withTokenValue("access-1")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(600))
                .claim("sid", "session-1")
                .build();

        IamDirectAuthenticationController.LogoutResponse response = controller.logout(
                new JwtAuthenticationToken(jwt));

        assertTrue(response.revoked());
        verify(sessions).revoke("session-1", "user-1", "self_logout");
    }

    private static UsernamePasswordAuthenticationToken authenticated(String username) {
        return UsernamePasswordAuthenticationToken.authenticated(username, "n/a", List.of());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        return request;
    }

    private static IamAuthorizationContext context(String username) {
        return new IamAuthorizationContext(
                "user-1",
                username,
                "Administrator",
                UserType.INTERNAL,
                List.of("MOM_ADMIN"),
                List.of("iam:user:read"),
                List.of("factory-1"),
                null,
                null);
    }

    private static IamAccountAuthenticationService.AuditIdentity auditIdentity() {
        return new IamAccountAuthenticationService.AuditIdentity("user-1", UserType.INTERNAL);
    }
}
