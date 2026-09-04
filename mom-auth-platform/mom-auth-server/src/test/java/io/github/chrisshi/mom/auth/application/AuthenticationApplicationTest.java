package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.auth.application.model.LoginView;
import io.github.chrisshi.mom.auth.infrastructure.configuration.AuthProperties;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserMapper;
import io.github.chrisshi.mom.auth.infrastructure.query.AuthenticationQueryMapper;
import io.github.chrisshi.mom.security.token.MomTokenPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationApplicationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    private UserMapper userMapper;
    private AuthenticationQueryMapper authenticationQueryMapper;
    private PasswordEncoder passwordEncoder;
    private MomTokenStore tokenStore;
    private AuthenticationApplication application;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        authenticationQueryMapper = mock(AuthenticationQueryMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenStore = mock(MomTokenStore.class);
        AuthProperties properties = new AuthProperties();
        properties.setAccessTokenTtl(Duration.ofHours(8));
        application = new AuthenticationApplication(
            userMapper,
            authenticationQueryMapper,
            passwordEncoder,
            tokenStore,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldIssueOpaqueTokenForValidCredentials() {
        UserEntity user = user("1001", true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("Admin@123456", "{bcrypt}hash")).thenReturn(true);
        when(authenticationQueryMapper.selectAuthoritiesByUserId("1001"))
            .thenReturn(List.of("ROLE_PLATFORM_ADMIN", "auth:user:read"));

        LoginView result = application.login(" Admin ", "Admin@123456");

        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.accessToken()).hasSize(43);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MomTokenPrincipal> principalCaptor = ArgumentCaptor.forClass(MomTokenPrincipal.class);
        verify(tokenStore).store(tokenCaptor.capture(), principalCaptor.capture());
        assertThat(tokenCaptor.getValue()).isEqualTo(result.accessToken());
        assertThat(principalCaptor.getValue().userId()).isEqualTo("1001");
        assertThat(principalCaptor.getValue().authorities())
            .containsExactly("ROLE_PLATFORM_ADMIN", "auth:user:read");
        assertThat(principalCaptor.getValue().expiresAt()).isEqualTo(result.expiresAt());
    }

    @Test
    void shouldHideUnknownUserAndWrongPasswordBehindSameError() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AuthException exception = expectAuthException(() -> application.login("missing", "wrong-password"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(tokenStore, never()).store(any(), any());
    }

    @Test
    void shouldRejectDisabledAccountAfterPasswordVerification() {
        UserEntity user = user("1001", false);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(eq("Admin@123456"), eq("{bcrypt}hash"))).thenReturn(true);

        AuthException exception = expectAuthException(() -> application.login("admin", "Admin@123456"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.ACCOUNT_DISABLED);
        verify(tokenStore, never()).store(any(), any());
    }

    @Test
    void shouldFailLoginWhenTokenStoreIsUnavailable() {
        UserEntity user = user("1001", true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("Admin@123456", "{bcrypt}hash")).thenReturn(true);
        when(authenticationQueryMapper.selectAuthoritiesByUserId("1001")).thenReturn(List.of());
        doThrow(new IllegalStateException("redis unavailable")).when(tokenStore).store(any(), any());

        AuthException exception = expectAuthException(() -> application.login("admin", "Admin@123456"));

        assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.TOKEN_STORE_UNAVAILABLE);
    }

    @Test
    void shouldRemoveCurrentTokenOnLogout() {
        application.logout("opaque-token");
        verify(tokenStore).remove("opaque-token");
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

    private static UserEntity user(String id, boolean enabled) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("admin");
        user.setPasswordHash("{bcrypt}hash");
        user.setDisplayName("平台管理员");
        user.setEnabled(enabled);
        return user;
    }
}
