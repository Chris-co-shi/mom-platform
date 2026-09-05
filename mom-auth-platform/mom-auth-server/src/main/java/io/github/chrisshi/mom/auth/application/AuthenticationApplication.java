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
 * 登录认证与当前 Opaque Token 生命周期用例。
 *
 * <p>职责边界刻意分成两段：用户名密码的 Credential Authentication 完全委托 Spring Security
 * {@link AuthenticationManager}；MOM 只在认证成功后生成并保存自己的 Redis-backed Opaque Token。
 * 该类不得退回到手工查用户、PasswordEncoder.matches 或自行解释 enabled 状态。</p>
 *
 * <p>TokenStore 或认证基础设施不可用时统一 Fail Closed，不签发“降级 Token”。</p>
 */
@Component
public class AuthenticationApplication {

    /** 256 bit 随机数，Base64URL 无填充编码后约 43 个字符。 */
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
     * 使用用户名密码完成 Spring Security 认证，并在成功后签发 MOM Opaque Token。
     *
     * <p>成功认证得到的 {@link AuthUserPrincipal} 只抽取稳定 userId 和 authority 快照写入 Token；
     * username 与密码摘要都不会进入 Token Principal。TokenStore 写入失败时本次登录整体失败。</p>
     *
     * @param username 登录名；进入认证前统一去除首尾空白并转为小写
     * @param password 明文密码，只交给 Spring Security 认证流程使用
     * @return Bearer Token、类型和过期时间
     * @throws AuthException 账号停用、凭据错误、认证基础设施异常或 TokenStore 不可用时抛出
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
            // Principal 类型不符合本模块约定说明认证链配置异常，不能尝试猜测身份继续签发 Token。
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
     * 注销当前已经通过 Resource Server 验证的 Opaque Token。
     *
     * <p>V1 只支持删除当前 Token，不维护 user → token 索引，也不实现“踢掉全部会话”。</p>
     *
     * @param rawToken 当前请求携带的原始 Bearer Token
     * @throws AuthException TokenStore 不可用时抛出并 Fail Closed
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

    /**
     * 生成不可预测的原始访问令牌。
     *
     * <p>这里只负责 raw token 随机性和传输编码；Redis Key 哈希、TTL 与存储故障策略由 MomTokenStore 负责。</p>
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
