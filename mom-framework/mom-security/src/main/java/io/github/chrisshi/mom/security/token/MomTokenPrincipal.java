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
}
