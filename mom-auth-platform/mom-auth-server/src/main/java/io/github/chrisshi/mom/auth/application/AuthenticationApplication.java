package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.auth.application.model.LoginView;
import io.github.chrisshi.mom.auth.infrastructure.configuration.AuthProperties;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserMapper;
import io.github.chrisshi.mom.auth.infrastructure.query.AuthenticationQueryMapper;
import io.github.chrisshi.mom.security.token.MomTokenPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
public class AuthenticationApplication {

    private static final int TOKEN_BYTES = 32;

    private final UserMapper userMapper;
    private final AuthenticationQueryMapper authenticationQueryMapper;
    private final PasswordEncoder passwordEncoder;
    private final MomTokenStore tokenStore;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationApplication(
        UserMapper userMapper,
        AuthenticationQueryMapper authenticationQueryMapper,
        PasswordEncoder passwordEncoder,
        MomTokenStore tokenStore,
        AuthProperties properties,
        Clock clock
    ) {
        this.userMapper = userMapper;
        this.authenticationQueryMapper = authenticationQueryMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.properties = properties;
        this.clock = clock;
    }

    public LoginView login(String username, String password) {
        String normalizedUsername = username.strip().toLowerCase(Locale.ROOT);
        UserEntity user = userMapper.selectOne(
            new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalizedUsername)
        );
        if (user == null || !matches(password, user.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        List<String> authorities = authenticationQueryMapper.selectAuthoritiesByUserId(user.getId());
        Instant expiresAt = clock.instant().plus(properties.getAccessTokenTtl());
        String token = generateToken();
        MomTokenPrincipal principal = new MomTokenPrincipal(user.getId(), authorities, expiresAt);
        try {
            tokenStore.store(token, principal);
        } catch (RuntimeException exception) {
            throw new AuthException(
                AuthErrorCode.TOKEN_STORE_UNAVAILABLE,
                AuthErrorCode.TOKEN_STORE_UNAVAILABLE.defaultMessage(),
                exception
            );
        }
        return new LoginView(token, "Bearer", expiresAt);
    }

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

    private boolean matches(String password, String encodedPassword) {
        try {
            return encodedPassword != null && passwordEncoder.matches(password, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
