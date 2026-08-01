package io.github.chrisshi.mom.system.infrastructure.cache.redis;

import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Redis System Runtime Projection Cache Adapter。
 *
 * <p>Adapter 只保存不可变 Catalog Release Snapshot 的明确 JSON，所有 Key 有 TTL。Redis 不可用、Value 损坏或
 *删除失败时只记录低基数告警并回源 PostgreSQL，不反向改变业务事务结果。</p>
 */
@Component
public class RedisSystemRuntimeCacheAdapter implements SystemRuntimeCachePort {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSystemRuntimeCacheAdapter.class);
    private static final Duration BASE_TTL = Duration.ofHours(12);
    private static final Duration INDEX_EXTRA_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final SystemCatalogSnapshotCodec codec;
    private final boolean enabled;
    private final String environment;

    public RedisSystemRuntimeCacheAdapter(
            StringRedisTemplate redis,
            SystemCatalogSnapshotCodec codec,
            @Value("${mom.system.runtime-cache.enabled:false}") boolean enabled,
            @Value("${mom.system.runtime-cache.environment:local}") String environment) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enabled = enabled;
        this.environment = requireEnvironment(environment);
    }

    @Override
    public Optional<SystemCatalogSnapshot> findCatalog(
            String applicationCode, long releaseVersion, String checksum) {
        if (!enabled) {
            return Optional.empty();
        }
        String key = catalogKey(applicationCode, releaseVersion, checksum);
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(codec.decode(json));
        } catch (RuntimeException exception) {
            LOGGER.warn("System Runtime Catalog Cache 读取失败，回源 PostgreSQL。failureType={}",
                    exception.getClass().getSimpleName());
            bestEffortDelete(key);
            return Optional.empty();
        }
    }

    @Override
    public void putCatalog(
            String applicationCode, long releaseVersion, String checksum,
            SystemCatalogSnapshot snapshot) {
        if (!enabled) {
            return;
        }
        String key = catalogKey(applicationCode, releaseVersion, checksum);
        String index = catalogIndex(applicationCode);
        try {
            Duration ttl = ttl(checksum);
            redis.opsForValue().set(key, codec.encode(snapshot), ttl);
            redis.opsForSet().add(index, key);
            redis.expire(index, ttl.plus(INDEX_EXTRA_TTL));
        } catch (RuntimeException exception) {
            LOGGER.warn("System Runtime Catalog Cache 写入失败，权威 PostgreSQL 保持不变。failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Override
    public void evictCatalog(String applicationCode) {
        if (!enabled) {
            return;
        }
        String index = catalogIndex(applicationCode);
        try {
            Set<String> keys = redis.opsForSet().members(index);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete(index);
        } catch (RuntimeException exception) {
            LOGGER.warn("System Runtime Catalog Cache 失效失败，等待 TTL 修复。failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void bestEffortDelete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // 原始异常已经记录；删除失败由 TTL 修复，避免重复日志。
        }
    }

    private String catalogKey(String applicationCode, long releaseVersion, String checksum) {
        return "mom:" + environment + ":system:catalog-release:v1:"
                + applicationCode + ':' + releaseVersion + ':' + checksum;
    }

    private String catalogIndex(String applicationCode) {
        return "mom:" + environment + ":system:catalog-release-index:v1:" + applicationCode;
    }

    private static Duration ttl(String checksum) {
        int jitterMinutes = Math.floorMod(checksum.hashCode(), 31);
        return BASE_TTL.plusMinutes(jitterMinutes);
    }

    private static String requireEnvironment(String value) {
        if (value == null || value.isBlank()
                || !value.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("runtime-cache environment 格式非法");
        }
        return value;
    }
}
