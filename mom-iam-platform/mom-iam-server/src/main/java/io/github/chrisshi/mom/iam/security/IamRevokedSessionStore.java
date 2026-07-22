package io.github.chrisshi.mom.iam.security;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Redis revoked sid 权威快速检查；Redis 不可用时受保护流程必须 Fail Closed。 */
public final class IamRevokedSessionStore {
    private final StringRedisTemplate redis;
    private final IamSessionProperties properties;
    private final Clock clock;

    public IamRevokedSessionStore(
            StringRedisTemplate redis,
            IamSessionProperties properties,
            Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /** 写入 revoked sid，TTL 至少覆盖已签发 Access Token 的剩余有效期。 */
    public void revoke(String sessionId, Instant latestAccessTokenExpiresAt) {
        Instant now = clock.instant();
        Instant expiresAt = latestAccessTokenExpiresAt == null
                ? now.plus(properties.getAccessTokenTtl()) : latestAccessTokenExpiresAt;
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        try {
            redis.opsForValue().set(key(sessionId), "1", ttl);
        }
        catch (DataAccessException exception) {
            throw new RevocationStoreUnavailableException(exception);
        }
    }

    /**
     * @return true 表示 sid 已撤销；Redis 故障时抛出异常，调用方不得把故障解释为未撤销。
     */
    public boolean isRevoked(String sessionId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
        }
        catch (DataAccessException exception) {
            throw new RevocationStoreUnavailableException(exception);
        }
    }

    private String key(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sid 不能为空");
        }
        return properties.getRevokedKeyPrefix() + sessionId;
    }

    /** Redis 撤销状态不可用；受保护 API 必须 Fail Closed。 */
    public static final class RevocationStoreUnavailableException extends RuntimeException {
        public RevocationStoreUnavailableException(Throwable cause) {
            super("revoked sid store unavailable", cause);
        }
    }
}
