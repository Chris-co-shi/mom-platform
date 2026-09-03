package io.github.chrisshi.mom.security.token;

import java.time.Instant;
import java.util.List;

/**
 * MOM 令牌 Principal。
 *
 * @param userId      用户Id
 * @param authorities 权限集合
 * @param expiresAt   过期时间
 */
public record MomTokenPrincipal(
    String userId,
    List<String> authorities,
    Instant expiresAt
) {
    /**
     * 创建令牌前校验关键参数
     *
     * @param userId
     * @param authorities
     * @param expiresAt
     */
    public MomTokenPrincipal {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        authorities = authorities == null
            ? List.of()
            : List.copyOf(authorities);
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt cannot be null");
        }
    }
}
