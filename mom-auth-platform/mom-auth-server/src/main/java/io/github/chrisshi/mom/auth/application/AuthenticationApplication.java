package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.auth.application.model.LoginView;
import io.github.chrisshi.mom.auth.infrastructure.configuration.AuthProperties;
import io.github.chrisshi.mom.auth.infrastructure.security.AuthUserPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenStore;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * 登录与 Token 生命周期用例。
 *
 * <p>用户名密码认证委托 Spring Security {@link AuthenticationManager}；该类不自行读取用户、
 * 不自行比较密码。认证成功后才进入 MOM 自有的 Opaque Token 生成与 Redis TokenStore。</p>
 */
@Component
public class AuthenticationApplication {

    private static final int TOKEN_BYTES = 32;

    private final AuthenticationManager authenticationManager;
    private final MomTokenStore tokenStore;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationApplication(
        AuthenticationManager authenticationManager,
        MomTokenStore tokenStore,
        AuthProperties properties,
        Clock clock
    ) {
        this.authenticationManager = authenticationManager;
        this.tokenStore = tokenStore;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 完成 Credential Authentication，并基于认证结果签发 MOM Opaque Token。
     */
    public LoginView login(String username, String password) {
        String normalizedUsername = username.strip().toLowerCase(Locale.ROOT);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, password)
            );
        } catch (DisabledException exception) {
            throw new AuthException(
                AuthErrorCode.ACCOUNT_DISABLED,
                AuthErrorCode.ACCOUNT_DISABLED.defaultMessage(),
                exception
            );
        } catch (InternalAuthenticationServiceException exception) {
            throw new AuthException(
                AuthErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE,
                AuthErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE.defaultMessage(),
                exception
            );
        } catch (AuthenticationException exception) {
            throw new AuthException(
                AuthErrorCode.INVALID_CREDENTIALS,
                AuthErrorCode.INVALID_CREDENTIALS.defaultMessage(),
                exception
            );
        }

        if (!(authentication.getPrincipal() instanceof AuthUserPrincipal principal)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE);
        }

        Instant expiresAt = clock.instant().plus(properties.getAccessTokenTtl());
        String token = generateToken();
        MomTokenPrincipal tokenPrincipal = new MomTokenPrincipal(
            principal.userId(),
            principal.authorityValues(),
            expiresAt
        );
        try {
            tokenStore.store(token, tokenPrincipal);
        } catch (RuntimeException exception) {
            throw new AuthException(
                AuthErrorCode.TOKEN_STORE_UNAVAILABLE,
                AuthErrorCode.TOKEN_STORE_UNAVAILABLE.defaultMessage(),
                exception
            );
        }
        return new LoginView(token, "Bearer", expiresAt);
    }

    /**
     * 只注销当前已经通过 Resource Server 验证的 Opaque Token。
     */
    public void logout(String rawToken) {
        try {
            tokenStore.remove(rawToken);
        } catch (RuntimeException exception) {
            throw new AuthException(
                AuthErrorCode.TOKEN_STORE_UNAVAILABLE,
                AuthErrorCode.TOKEN_STORE_UNAVAILABLE.defaultMessage(),
                exception
            );
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
