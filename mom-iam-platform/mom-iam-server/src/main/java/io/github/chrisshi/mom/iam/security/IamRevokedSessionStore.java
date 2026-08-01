package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.security.revocation.MomRevocationStoreUnavailableException;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionKeys;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * IAM revoked SID 安全状态的 Redis 写入与权威快速检查。
 *
 * <p>该类型属于 IAM Security 边界，不是普通 Cache：状态不可重建且 Redis 不可用时必须 Fail Closed。
 * Key 组合和不可用异常复用 {@code mom-security}，保证 IAM、Gateway 与 Resource Server 使用同一语义。
 * StringRedisTemplate 可并发复用；每个撤销 Key 都有覆盖 Access Token 剩余寿命的 TTL。</p>
 */
public final class IamRevokedSessionStore {
    private final StringRedisTemplate redis;
    private final IamSessionProperties properties;
    private final Clock clock;

    /**
     * 创建 revoked SID Store。
     *
     * @param redis IAM 权威 Redis 客户端
     * @param properties Session TTL 与 Key 前缀配置
     * @param clock UTC 时钟
     */
    public IamRevokedSessionStore(
            StringRedisTemplate redis,
            IamSessionProperties properties,
            Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 写入 revoked SID，TTL 至少覆盖已签发 Access Token 的剩余有效期。
     *
     * @param sessionId IAM 生成的非空 SID
     * @param latestAccessTokenExpiresAt 最晚 Access Token 过期时间；为空时使用配置 TTL
     * @throws MomRevocationStoreUnavailableException Redis 写入失败时抛出，调用方必须回滚/失败关闭
     */
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
            throw new MomRevocationStoreUnavailableException(exception);
        }
    }

    /**
     * 检查 SID 是否已撤销。
     *
     * @param sessionId IAM 生成的非空 SID
     * @return true 表示已撤销；false 只表示 Redis 权威查询明确不存在
     * @throws MomRevocationStoreUnavailableException Redis 结果不确定时抛出，调用方不得按有效放行
     */
    public boolean isRevoked(String sessionId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
        }
        catch (DataAccessException exception) {
            throw new MomRevocationStoreUnavailableException(exception);
        }
    }

    private String key(String sessionId) {
        return MomRevokedSessionKeys.key(properties.getRevokedKeyPrefix(), sessionId);
    }
}
