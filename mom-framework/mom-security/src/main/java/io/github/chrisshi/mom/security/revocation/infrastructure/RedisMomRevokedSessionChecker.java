package io.github.chrisshi.mom.security.revocation.infrastructure;

import io.github.chrisshi.mom.security.revocation.MomRevocationStoreUnavailableException;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionChecker;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionKeys;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 使用 IAM 现有 Redis revoked sid 命名空间的只读 Infrastructure Adapter。
 *
 * <p>该实现只执行一次 {@code EXISTS} 语义查询，不写 Key、不修改 TTL、不缓存结果，也不维护第二份
 * Session 状态。Redis 连接、命令超时或返回不确定结果时统一抛出脱敏异常，业务 Resource Server
 * 必须 Fail Closed。连接超时预算继续由 Spring Boot Redis 配置管理。</p>
 */
public final class RedisMomRevokedSessionChecker implements MomRevokedSessionChecker {
    private final StringRedisTemplate redis;
    private final String keyPrefix;

    /** 创建复用应用唯一 Redis 连接工厂的撤销检查器。 */
    public RedisMomRevokedSessionChecker(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    /** 查询 IAM 写入的同一 revoked sid Key；任何不确定结果均失败关闭。 */
    @Override
    public boolean isRevoked(String sessionId) {
        try {
            Boolean result = redis.hasKey(MomRevokedSessionKeys.key(keyPrefix, sessionId));
            if (result == null) {
                throw new MomRevocationStoreUnavailableException("revoked sid store returned no result");
            }
            return result;
        }
        catch (DataAccessException exception) {
            throw new MomRevocationStoreUnavailableException(exception);
        }
    }
}
