package io.github.chrisshi.mom.system.infrastructure.cache.redis;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Redis System Runtime Projection Cache Adapter。
 *
 * <p>Adapter 只保存明确类型、可重建且有版本的 JSON Projection，所有 Key 有 TTL。Redis 不可用、Value 损坏
 * 或删除失败时只记录低基数告警并回源 PostgreSQL，不反向改变业务事务结果。</p>
 */
@Component
public class RedisSystemRuntimeCacheAdapter implements SystemRuntimeCachePort {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSystemRuntimeCacheAdapter.class);
    private static final Duration RELEASE_TTL = Duration.ofHours(12);
    private static final Duration REFERENCE_TTL = Duration.ofMinutes(5);
    private static final Duration INDEX_EXTRA_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final SystemCatalogSnapshotCodec catalogCodec;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String environment;

    public RedisSystemRuntimeCacheAdapter(
            StringRedisTemplate redis,
            SystemCatalogSnapshotCodec catalogCodec,
            ObjectMapper objectMapper,
            @Value("${mom.system.runtime-cache.enabled:false}") boolean enabled,
            @Value("${mom.system.runtime-cache.environment:local}") String environment) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.catalogCodec = Objects.requireNonNull(catalogCodec, "catalogCodec");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
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
            return json == null ? Optional.empty() : Optional.of(catalogCodec.decode(json));
        } catch (RuntimeException exception) {
            readFailure("Catalog", key, exception);
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
        putEncoded(
                key,
                catalogIndex(applicationCode),
                catalogCodec.encode(snapshot),
                ttl(RELEASE_TTL, checksum, 31),
                "Catalog");
    }

    @Override
    public void evictCatalog(String applicationCode) {
        evictIndex(catalogIndex(applicationCode), "Catalog");
    }

    @Override
    public Optional<ResolvedSystemParameter> findParameter(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version) {
        return readJson(
                parameterKey(
                        lookupScopeCode, parameterKey, resolvedScopeType, resolvedScopeCode, version),
                ResolvedSystemParameter.class,
                "Parameter");
    }

    @Override
    public void putParameter(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version,
            ResolvedSystemParameter value) {
        String key = parameterKey(
                lookupScopeCode, parameterKey, resolvedScopeType, resolvedScopeCode, version);
        putJson(
                key,
                parameterIndex(parameterKey),
                value,
                ttl(REFERENCE_TTL, parameterKey + ':' + version, 31),
                "Parameter");
    }

    @Override
    public void evictParameter(String parameterKey) {
        evictIndex(parameterIndex(parameterKey), "Parameter");
    }

    @Override
    public Optional<List<SystemDictionaryItemOption>> findDictionaryItems(
            String dictionaryCode, long dictionaryVersion) {
        return readJson(
                        dictionaryItemsKey(dictionaryCode, dictionaryVersion),
                        DictionaryItemsCacheValue.class,
                        "Dictionary")
                .map(DictionaryItemsCacheValue::items);
    }

    @Override
    public void putDictionaryItems(
            String dictionaryCode,
            long dictionaryVersion,
            List<SystemDictionaryItemOption> items) {
        String key = dictionaryItemsKey(dictionaryCode, dictionaryVersion);
        putJson(
                key,
                dictionaryIndex(dictionaryCode),
                new DictionaryItemsCacheValue(items),
                ttl(REFERENCE_TTL, dictionaryCode + ':' + dictionaryVersion, 31),
                "Dictionary");
    }

    @Override
    public Optional<ResolvedSystemDictionaryItem> findDictionaryItem(
            String dictionaryCode,
            long dictionaryVersion,
            String itemCode) {
        return readJson(
                dictionaryItemKey(dictionaryCode, dictionaryVersion, itemCode),
                ResolvedSystemDictionaryItem.class,
                "Dictionary");
    }

    @Override
    public void putDictionaryItem(
            String dictionaryCode,
            long dictionaryVersion,
            String itemCode,
            ResolvedSystemDictionaryItem item) {
        String key = dictionaryItemKey(dictionaryCode, dictionaryVersion, itemCode);
        putJson(
                key,
                dictionaryIndex(dictionaryCode),
                item,
                ttl(REFERENCE_TTL, dictionaryCode + ':' + itemCode + ':' + dictionaryVersion, 31),
                "Dictionary");
    }

    @Override
    public void evictDictionary(String dictionaryCode) {
        evictIndex(dictionaryIndex(dictionaryCode), "Dictionary");
    }

    private <T> Optional<T> readJson(String key, Class<T> type, String capability) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, type));
        } catch (JacksonException exception) {
            readFailure(capability, key, exception);
            return Optional.empty();
        } catch (RuntimeException exception) {
            readFailure(capability, key, exception);
            return Optional.empty();
        }
    }

    private void putJson(
            String key,
            String index,
            Object value,
            Duration ttl,
            String capability) {
        if (!enabled) {
            return;
        }
        try {
            putEncoded(key, index, objectMapper.writeValueAsString(value), ttl, capability);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法编码 System Runtime Cache Projection", exception);
        }
    }

    private void putEncoded(
            String key,
            String index,
            String json,
            Duration ttl,
            String capability) {
        if (!enabled) {
            return;
        }
        try {
            redis.opsForValue().set(key, json, ttl);
            redis.opsForSet().add(index, key);
            redis.expire(index, ttl.plus(INDEX_EXTRA_TTL));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "System Runtime {} Cache 写入失败，权威 PostgreSQL 保持不变。failureType={}",
                    capability,
                    exception.getClass().getSimpleName());
        }
    }

    private void readFailure(String capability, String key, Exception exception) {
        LOGGER.warn(
                "System Runtime {} Cache 读取失败，回源 PostgreSQL。failureType={}",
                capability,
                exception.getClass().getSimpleName());
        bestEffortDelete(key);
    }

    private void evictIndex(String index, String capability) {
        if (!enabled) {
            return;
        }
        try {
            Set<String> keys = redis.opsForSet().members(index);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete(index);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "System Runtime {} Cache 失效失败，等待可靠事件重投或 TTL 修复。failureType={}",
                    capability,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void bestEffortDelete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // 原始异常已经记录；删除失败由可靠失效事件或 TTL 修复。
        }
    }

    private String catalogKey(String applicationCode, long releaseVersion, String checksum) {
        return prefix() + "catalog-release:v1:"
                + applicationCode + ':' + releaseVersion + ':' + checksum;
    }

    private String catalogIndex(String applicationCode) {
        return prefix() + "catalog-release-index:v1:" + applicationCode;
    }

    private String parameterKey(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version) {
        return prefix() + "parameter-resolved:v1:"
                + segment(lookupScopeCode) + ':'
                + parameterKey + ':'
                + resolvedScopeType.name().toLowerCase(java.util.Locale.ROOT) + ':'
                + segment(resolvedScopeCode) + ':'
                + version;
    }

    private String parameterIndex(String parameterKey) {
        return prefix() + "parameter-resolved-index:v1:" + parameterKey;
    }

    private String dictionaryItemsKey(String dictionaryCode, long dictionaryVersion) {
        return prefix() + "dictionary-active:v1:" + dictionaryCode + ':' + dictionaryVersion;
    }

    private String dictionaryItemKey(
            String dictionaryCode, long dictionaryVersion, String itemCode) {
        return prefix() + "dictionary-item:v1:"
                + dictionaryCode + ':' + dictionaryVersion + ':' + itemCode;
    }

    private String dictionaryIndex(String dictionaryCode) {
        return prefix() + "dictionary-index:v1:" + dictionaryCode;
    }

    private String prefix() {
        return "mom:" + environment + ":system:";
    }

    private static String segment(String value) {
        return value == null || value.isEmpty() ? "_global" : value;
    }

    private static Duration ttl(
            Duration base,
            String seed,
            int jitterMinutesExclusive) {
        int jitterMinutes = Math.floorMod(seed.hashCode(), jitterMinutesExclusive);
        return base.plusMinutes(jitterMinutes);
    }

    private static String requireEnvironment(String value) {
        if (value == null || value.isBlank()
                || !value.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("runtime-cache environment 格式非法");
        }
        return value;
    }

    /** 避免依赖通用 List 反序列化的明确 Cache Value。 */
    record DictionaryItemsCacheValue(List<SystemDictionaryItemOption> items) {
        DictionaryItemsCacheValue {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
