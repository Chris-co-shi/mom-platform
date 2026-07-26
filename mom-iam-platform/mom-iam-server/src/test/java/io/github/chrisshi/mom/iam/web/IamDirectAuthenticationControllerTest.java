package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.security.IamAccountAuthenticationService;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContext;
import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamSessionJwtIssuer;
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
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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

/** 第一方 JSON 认证控制器的无容器契约测试。 */
@ExtendWith(MockitoExtension.class)
class IamDirectAuthenticationControllerTest {

    @Mock AuthenticationProvider authenticationProvider;
    @Mock IamAccountAuthenticationService accounts;
    @Mock IamClientAccessPolicyService clientAccess;
    @Mock IamSessionTokenService sessions;
    @Mock IamSessionJwtIssuer jwtIssuer;

    private IamDirectAuthenticationController controller;

    @BeforeEach
    void setUp() {
        controller = new IamDirectAuthenticationController(
                authenticationProvider, accounts, clientAccess, sessions, jwtIssuer);
    }

    @Test
    void invalidCredentialsMustUseGenericFailureAndRecordAttempt() {
        when(authenticationProvider.authenticate(any()))
                .thenThrow(new BadCredentialsException("provider detail must not escape"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        IamDirectAuthenticationController.InvalidCredentialsException error = assertThrows(
                IamDirectAuthenticationController.InvalidCredentialsException.class,
                () -> controller.login(new IamDirectAuthenticationController.LoginRequest(
                        "admin", "wrong-password", "mom-admin-web", "browser"), request));

        assertEquals("账号或密码错误，或账号当前不可用", error.getMessage());
        verify(accounts).recordBadCredentials("admin");
        verify(clientAccess, never()).requireAuthorization(any(), any());
        verify(sessions, never()).issueInitial(any(), any(), any(), any(), any());
    }

    @Test
    void requiredPasswordChangeMustNotIssueSessionOrToken() {
        when(authenticationProvider.authenticate(any())).thenReturn(authenticated("admin"));
        when(accounts.requiresPasswordChange("admin")).thenReturn(true);
        MockHttpServletRequest request = request();

        assertThrows(
                IamDirectAuthenticationController.PasswordChangeRequiredException.class,
                () -> controller.login(new IamDirectAuthenticationController.LoginRequest(
                        "admin", "TemporaryPass123!", "mom-admin-web", "browser"), request));

        verify(clientAccess).requireAuthorization("admin", "mom-admin-web");
        verify(accounts).recordSuccessfulLogin("admin");
        verify(sessions, never()).issueInitial(any(), any(), any(), any(), any());
        verify(jwtIssuer, never()).issue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void successfulLoginMustReuseAuthoritativeSessionAndJwtServices() {
        when(authenticationProvider.authenticate(any())).thenReturn(authenticated("admin"));
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
        when(jwtIssuer.issue(
                context,
                "session-1",
                "mom-admin-web",
                issuedAt,
                accessExpiresAt,
                Set.of("openid", "profile")))
                .thenReturn(new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-1",
                        issuedAt,
                        accessExpiresAt,
                        Set.of("openid", "profile")));

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
        when(jwtIssuer.issue(
                context,
                "session-1",
                "mom-admin-web",
                issuedAt,
                accessExpiresAt,
                Set.of("openid", "profile")))
                .thenReturn(new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-2",
                        issuedAt,
                        accessExpiresAt,
                        Set.of("openid", "profile")));

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
}
