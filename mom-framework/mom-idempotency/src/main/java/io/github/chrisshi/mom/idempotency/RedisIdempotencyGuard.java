package io.github.chrisshi.mom.idempotency;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 Redis 的幂等占位实现。
 *
 * <p>实现依赖 {@link StringRedisTemplate#opsForValue()} 的 {@code setIfAbsent(key, value, ttl)}，
 * 由单条 Redis SET NX PX/EX 语义同时完成“不存在才写入”和“设置 TTL”，不存在先查询再写入的竞态窗口。</p>
 *
 * <p>该实现不是分布式锁：占位成功后不会主动删除 Key，避免业务执行完成后立即删除导致迟到重试再次执行。
 * Key 只能由 TTL 自动淘汰。调用方需要根据业务最长重试窗口选择 TTL。</p>
 */
public final class RedisIdempotencyGuard implements IdempotencyGuard {

    private final StringRedisTemplate redisTemplate;
    private final RedisIdempotencyKeyFactory keyFactory;
    private final RedisIdempotencyProperties properties;

    /**
     * 创建 Redis 幂等保护器。
     *
     * @param redisTemplate 仅使用字符串序列化的 Redis 操作模板
     * @param keyFactory 统一 Key 命名工厂
     * @param properties Redis 故障策略和默认 TTL
     */
    public RedisIdempotencyGuard(
            StringRedisTemplate redisTemplate,
            RedisIdempotencyKeyFactory keyFactory,
            RedisIdempotencyProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate 不能为空");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IdempotencyAcquireResult tryAcquire(
            String scope,
            String requestKey,
            String ownerToken,
            Duration ttl) {
        String protectedKey = keyFactory.create(scope, requestKey);
        String normalizedOwnerToken = requireText(ownerToken, "ownerToken");
        Duration effectiveTtl = ttl == null ? properties.getDefaultTtl() : ttl;
        if (effectiveTtl == null || effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new IllegalArgumentException("幂等 TTL 必须大于零");
        }

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(protectedKey, normalizedOwnerToken, effectiveTtl);
            IdempotencyAcquireStatus status = Boolean.TRUE.equals(acquired)
                    ? IdempotencyAcquireStatus.ACQUIRED
                    : IdempotencyAcquireStatus.DUPLICATE;
            return new IdempotencyAcquireResult(status, protectedKey, effectiveTtl, null);
        } catch (DataAccessException exception) {
            return handleRedisFailure(protectedKey, effectiveTtl, exception);
        }
    }

    /**
     * 根据配置执行 Redis 故障策略。fail-open 必须显式返回 BYPASSED，避免上层误以为取得了正常占位。
     */
    private IdempotencyAcquireResult handleRedisFailure(
            String protectedKey,
            Duration ttl,
            DataAccessException exception) {
        if (properties.getFailureMode() == RedisFailureMode.FAIL_OPEN) {
            return new IdempotencyAcquireResult(
                    IdempotencyAcquireStatus.BYPASSED,
                    protectedKey,
                    ttl,
                    exception.getClass().getSimpleName());
        }
        throw new IdempotencyUnavailableException(
                "Redis 幂等保护不可用，已按 fail-closed 阻止业务继续执行",
                exception);
    }

    /**
     * 校验必须存在的文本参数，并去除首尾空白。
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
