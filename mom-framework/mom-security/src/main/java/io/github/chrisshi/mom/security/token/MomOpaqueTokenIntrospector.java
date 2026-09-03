package io.github.chrisshi.mom.security.token;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * 基于 MOM 内部令牌存储的 Opaque Token 内省器。
 *
 * <p>从 {@link MomTokenStore} 中查找令牌对应的认证快照，
 * 校验有效期后转换为 Spring Authorization Server 所需的
 * {@link OAuth2AuthenticatedPrincipal}。</p>
 *
 * @author 史偕成
 * @since 2026-09-03
 */
@RequiredArgsConstructor
public class MomOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final MomTokenStore tokenStore;
    private final Clock clock;

    /**
     * 内省原始令牌，返回认证主体。
     *
     * @param token 待内省的原始令牌字符串
     * @return 令牌有效时返回认证主体；令牌无效或已过期时抛出异常
     * @throws BadOpaqueTokenException 令牌不存在、已过期或格式非法时抛出
     */
    @Override
    public @Nullable OAuth2AuthenticatedPrincipal introspect(@NonNull String token) {
        MomTokenPrincipal principal = tokenStore.find(token)
            .orElseThrow(() -> new BadOpaqueTokenException("Invalid token"));
        if (!principal.expiresAt().isAfter(clock.instant())) {
            throw new BadOpaqueTokenException("Invalid token");
        }
        // 显式指定泛型类型参数，确保 toList() 返回 List<GrantedAuthority> 而非 List<SimpleGrantedAuthority>
        List<GrantedAuthority> authorities =
            principal.authorities()
                .stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        Map<String, Object> attributes = Map.of(
            OAuth2TokenIntrospectionClaimNames.SUB,
            principal.userId(),
            OAuth2TokenIntrospectionClaimNames.EXP,
            principal.expiresAt()
        );

        return new DefaultOAuth2AuthenticatedPrincipal(
            principal.userId(),
            attributes,
            authorities
        );
    }
}
