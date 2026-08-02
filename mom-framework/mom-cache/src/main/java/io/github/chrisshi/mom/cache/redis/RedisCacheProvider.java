package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheLayer;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.core.CacheMetrics;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * 基于 Spring Data Redis 的 L2 Cache Adapter。
 *
 * <p>Adapter 使用 Core 已构建的完整 Environment/Scope Key，不自行增加 Namespace。读取只按 Region 的精确
 * CacheValueType 恢复；损坏或不兼容数据会删除该精确 Key 并返回 Miss。Redis 连接/超时错误也按 fail-open
 * Cache Miss 处理并记录指标，不向业务伪造旧值。StringRedisTemplate 和 Serializer 均支持并发复用。</p>
 */
public class RedisCacheProvider implements CacheProvider {

    private final StringRedisTemplate redisTemplate;
    private final CacheSerializer serializer;
    private final CacheMetrics metrics;

    /**
     * 创建无指标的兼容 Adapter。
     *
     * @param redisTemplate Redis String 客户端
     * @param serializer 版本化 JSON Serializer
     */
    public RedisCacheProvider(StringRedisTemplate redisTemplate, CacheSerializer serializer) {
        this(redisTemplate, serializer, new CacheMetrics(null));
    }

    /**
     * 创建生产 Redis Adapter。
     *
     * @param redisTemplate Redis String 客户端
     * @param serializer 版本化 JSON Serializer
     * @param metrics Cache 指标记录器
     */
    public RedisCacheProvider(
            StringRedisTemplate redisTemplate,
            CacheSerializer serializer,
            CacheMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        this.metrics = metrics;
    }

    @Override
    public CacheLayer layer() {
        return CacheLayer.REMOTE;
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public boolean supports(CachePolicy policy) {
        return policy != null && policy.redisEnabled();
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public Object get(CacheKey key) {
        // 旧 Provider SPI 没有携带恢复类型，安全实现不能使用 Object.class；旧 CacheService 已走 typed 桥。
        metrics.legacyUsage();
        return null;
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public void put(CacheKey key, Object value, Duration ttl) {
        metrics.legacyUsage();
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public void delete(CacheKey key) {
        metrics.legacyUsage();
        try {
            redisTemplate.delete(namespace(key));
        } catch (RuntimeException ex) {
            recordInfrastructureFailure(ex, "delete");
        }
    }

    private String namespace(CacheKey key) {
        return "mom:cache:v1:" + key.value();
    }

    @Override
    public <T> T get(CacheEntryKey<T> key, String storageKey) {
        try {
            String value = redisTemplate.opsForValue().get(storageKey);
            if (value == null) {
                return null;
            }
            return serializer.deserialize(value, key.region().valueType());
        } catch (CacheSerializationException | IncompatibleCacheEntryException ex) {
            metrics.error("deserialize");
            deleteCorruptedKey(storageKey);
            return null;
        } catch (RuntimeException ex) {
            recordInfrastructureFailure(ex, "get");
            return null;
        }
    }

    @Override
    public <T> void put(CacheEntryKey<T> key, String storageKey, T value, Duration ttl) {
        Objects.requireNonNull(ttl, "Redis TTL 不能为空");
        try {
            redisTemplate.opsForValue().set(
                    storageKey,
                    serializer.serialize(value, key.region().valueType()),
                    ttl
            );
        } catch (RuntimeException ex) {
            recordInfrastructureFailure(ex, "put");
        }
    }

    @Override
    public void delete(CacheEntryKey<?> key, String storageKey) {
        try {
            redisTemplate.delete(storageKey);
        } catch (RuntimeException ex) {
            recordInfrastructureFailure(ex, "delete");
        }
    }

    private void deleteCorruptedKey(String storageKey) {
        try {
            redisTemplate.delete(storageKey);
        } catch (RuntimeException ex) {
            recordInfrastructureFailure(ex, "delete-corrupted");
        }
    }

    private void recordInfrastructureFailure(RuntimeException exception, String operation) {
        if (isTimeout(exception)) {
            metrics.redisTimeout();
        } else {
            metrics.error(operation);
        }
    }

    private static boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof QueryTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
