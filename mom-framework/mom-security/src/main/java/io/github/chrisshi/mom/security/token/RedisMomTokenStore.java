package io.github.chrisshi.mom.security.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Redis 的 MOM Opaque Token 存储。
 *
 * <p>Redis Key 只保存 Token 指纹，不保存原始 Token；值是认证主体快照，TTL 与主体过期时间保持一致。
 * 该类只负责令牌存取，不负责认证授权、用户状态查询或批量会话管理。</p>
 */
@RequiredArgsConstructor
public class RedisMomTokenStore implements MomTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    /**
     * Token 指纹 Key 的固定命名空间。
     *
     * <p>该前缀用于隔离认证令牌数据，不能与其他 Redis 业务 Key 共用。</p>
     */
    private static final String TOKEN_KEY_PREFIX = "mom:token:";

    /**
     * 根据原始 Token 计算不可逆的 Redis Key。
     *
     * @param token 原始 Opaque Token
     * @return 由固定前缀和 Token 指纹组成的 Redis Key
     */
    private String key(String token) {
        return TOKEN_KEY_PREFIX + MomTokenFingerprint.of(token);
    }

    /**
     * 保存认证主体快照，并把 Redis TTL 设置为主体剩余有效期。
     *
     * @param token 原始 Opaque Token，不写入 Redis Key 或日志
     * @param principal 认证主体快照及过期时间
     * @throws IllegalArgumentException 主体已经过期时抛出
     */
    @Override
    public void store(String token, MomTokenPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        Duration ttl = Duration.between(
            clock.instant(),
            principal.expiresAt()
        );
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Token 已过期");
        }
        String json = jsonMapper.writeValueAsString(principal);
        redisTemplate.opsForValue().set(
            key(token),
            json,
            ttl
        );
    }

    /**
     * 按 Token 指纹读取认证主体快照。
     *
     * @param token 原始 Opaque Token
     * @return Redis 中存在且可反序列化的主体；不存在时返回空
     */
    @Override
    public Optional<MomTokenPrincipal> find(String token) {
        String json = redisTemplate.opsForValue().get(key(token));
        if (json == null) {
            return Optional.empty();
        }
        MomTokenPrincipal momTokenPrincipal = jsonMapper.readValue(json, MomTokenPrincipal.class);
        return Optional.of(momTokenPrincipal);
    }

    /**
     * 删除当前 Token 的认证快照；重复删除保持幂等。
     *
     * @param token 原始 Opaque Token
     */
    @Override
    public void remove(String token) {
        MomTokenFingerprint.of(token);
        redisTemplate.delete(key(token));
    }
}
