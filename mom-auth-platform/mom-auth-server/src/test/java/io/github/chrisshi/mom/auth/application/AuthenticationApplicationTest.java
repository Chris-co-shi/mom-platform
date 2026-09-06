package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.auth.application.model.LoginView;
import io.github.chrisshi.mom.auth.infrastructure.configuration.AuthProperties;
import io.github.chrisshi.mom.auth.infrastructure.security.AuthUserPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationApplicationTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    private AuthenticationManager authenticationManager;
    private MomTokenStore tokenStore;
    private AuthenticationApplication application;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        tokenStore = mock(MomTokenStore.class);
        AuthProperties properties = new AuthProperties();
        properties.setAccessTokenTtl(Duration.ofHours(8));
        application = new AuthenticationApplication(
            authenticationManager,
            tokenStore,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldDelegateCredentialAuthenticationAndIssueOpaqueToken() {
        AuthUserPrincipal principal = principal(true);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated(principal));

        LoginView result = application.login(" Admin ", "Admin@123456");

        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue().getPrincipal()).isEqualTo("admin");
        assertThat(authenticationCaptor.getValue().getCredentials()).isEqualTo("Admin@123456");

        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.accessToken()).hasSize(43);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));

        ArgumentCaptor<MomTokenPrincipal> principalCaptor = ArgumentCaptor.forClass(MomTokenPrincipal.class);
        verify(tokenStore).store(any(String.class), principalCaptor.capture());
        assertThat(principalCaptor.getValue().userId()).isEqualTo("1001");
        assertThat(principalCaptor.getValue().authorities())
            .containsExactly("ROLE_PLATFORM_ADMIN", "auth:user:read");
        assertThat(principalCaptor.getValue().expiresAt()).isEqualTo(result.expiresAt());
    }

    @Test
    void shouldMapBadCredentialsToStableAuthError() {
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(new BadCredentialsException("bad credentials"));

        AuthException exception = expectAuthException(() -> application.login("missing", "wrong-password"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(tokenStore, never()).store(any(), any());
    }

    @Test
    void shouldMapDisabledAccountFromSpringSecurity() {
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(new DisabledException("disabled"));

        AuthException exception = expectAuthException(() -> application.login("admin", "Admin@123456"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.ACCOUNT_DISABLED);
        verify(tokenStore, never()).store(any(), any());
    }

    @Test
    void shouldFailClosedWhenAuthenticationInfrastructureIsUnavailable() {
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(new InternalAuthenticationServiceException("database unavailable"));

        AuthException exception = expectAuthException(() -> application.login("admin", "Admin@123456"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE);
        verify(tokenStore, never()).store(any(), any());
    }

    @Test
    void shouldFailLoginWhenTokenStoreIsUnavailable() {
        AuthUserPrincipal principal = principal(true);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated(principal));
        doThrow(new IllegalStateException("redis unavailable")).when(tokenStore).store(any(), any());

        AuthException exception = expectAuthException(() -> application.login("admin", "Admin@123456"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.TOKEN_STORE_UNAVAILABLE);
    }

    @Test
    void shouldRemoveCurrentTokenOnLogout() {
        application.logout("opaque-token");
        verify(tokenStore).remove("opaque-token");
    }

    private static UsernamePasswordAuthenticationToken authenticated(AuthUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static AuthUserPrincipal principal(boolean enabled) {
        return new AuthUserPrincipal(
            "1001",
            "admin",
            "{bcrypt}hash",
            enabled,
            List.of("ROLE_PLATFORM_ADMIN", "auth:user:read")
        );
    }

    private static AuthException expectAuthException(Runnable action) {
        try {
            action.run();
            fail("expected AuthException");
            throw new AssertionError("unreachable");
        } catch (AuthException exception) {
            return exception;
        }
    }
}
