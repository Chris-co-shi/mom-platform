package io.github.chrisshi.mom.system.infrastructure.cache;

import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheScope;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.cache.api.CacheValueType;
import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 通过 mom-cache typed API 实现 System Runtime 可重建 Projection Cache Port。
 *
 * <p>该 Infrastructure Adapter 不直接依赖 Redis、Caffeine 或 Jackson。所有 System Runtime 数据属于平台级
 * Global Scope；调用方已经先读取 PostgreSQL 权威 Header，因此版本化 L2 旧值不会被再次命中。失效时清理
 * 整个进程内 Region，L2 旧版本由 TTL 回收，禁止无界扫描。CacheService 的基础设施失败按 Miss 处理。</p>
 */
@Component
public class MomCacheSystemRuntimeCacheAdapter implements SystemRuntimeCachePort {
    private static final CacheScope GLOBAL = CacheScope.global();
    private static final Duration REFERENCE_LOCAL_TTL = Duration.ofMinutes(2);
    private static final Duration REFERENCE_REMOTE_TTL = Duration.ofMinutes(5);
    private static final Duration RELEASE_LOCAL_TTL = Duration.ofMinutes(5);
    private static final Duration RELEASE_REMOTE_TTL = Duration.ofHours(12);

    private static final CacheRegion<SystemCatalogSnapshot> CATALOG = region(
            "catalog-release", CacheValueType.of("system.catalog-snapshot", 1, SystemCatalogSnapshot.class),
            RELEASE_LOCAL_TTL, RELEASE_REMOTE_TTL);
    private static final CacheRegion<ResolvedSystemParameter> PARAMETER = region(
            "parameter-resolved", CacheValueType.of("system.resolved-parameter", 1, ResolvedSystemParameter.class),
            REFERENCE_LOCAL_TTL, REFERENCE_REMOTE_TTL);
    private static final CacheRegion<DictionaryItemsCacheValue> DICTIONARY_ITEMS = region(
            "dictionary-active", CacheValueType.of(
                    "system.dictionary-items", 1, DictionaryItemsCacheValue.class),
            REFERENCE_LOCAL_TTL, REFERENCE_REMOTE_TTL);
    private static final CacheRegion<ResolvedSystemDictionaryItem> DICTIONARY_ITEM = region(
            "dictionary-item", CacheValueType.of(
                    "system.resolved-dictionary-item", 1, ResolvedSystemDictionaryItem.class),
            REFERENCE_LOCAL_TTL, REFERENCE_REMOTE_TTL);

    private final CacheService cacheService;
    private final boolean enabled;

    /**
     * 创建 System Runtime Cache Adapter。
     *
     * @param cacheService mom-cache 统一入口
     * @param enabled 是否启用 System Runtime Cache；关闭时所有读取均为 Miss
     */
    public MomCacheSystemRuntimeCacheAdapter(
            CacheService cacheService,
            @Value("${mom.system.runtime-cache.enabled:false}") boolean enabled) {
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService");
        this.enabled = enabled;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SystemCatalogSnapshot> findCatalog(
            String applicationCode, long releaseVersion, String checksum) {
        return read(key(CATALOG, applicationCode + ':' + releaseVersion + ':' + checksum));
    }

    /** {@inheritDoc} */
    @Override
    public void putCatalog(
            String applicationCode, long releaseVersion, String checksum, SystemCatalogSnapshot snapshot) {
        put(key(CATALOG, applicationCode + ':' + releaseVersion + ':' + checksum), snapshot);
    }

    /** {@inheritDoc} */
    @Override
    public void evictCatalog(String applicationCode) {
        invalidateLocal(CATALOG);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ResolvedSystemParameter> findParameter(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version) {
        return read(key(PARAMETER, parameterSubject(
                lookupScopeCode, parameterKey, resolvedScopeType, resolvedScopeCode, version)));
    }

    /** {@inheritDoc} */
    @Override
    public void putParameter(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version,
            ResolvedSystemParameter value) {
        put(key(PARAMETER, parameterSubject(
                lookupScopeCode, parameterKey, resolvedScopeType, resolvedScopeCode, version)), value);
    }

    /** {@inheritDoc} */
    @Override
    public void evictParameter(String parameterKey) {
        invalidateLocal(PARAMETER);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<List<SystemDictionaryItemOption>> findDictionaryItems(
            String dictionaryCode, long dictionaryVersion) {
        return read(key(DICTIONARY_ITEMS, dictionaryCode + ':' + dictionaryVersion))
                .map(DictionaryItemsCacheValue::items);
    }

    /** {@inheritDoc} */
    @Override
    public void putDictionaryItems(
            String dictionaryCode, long dictionaryVersion, List<SystemDictionaryItemOption> items) {
        put(key(DICTIONARY_ITEMS, dictionaryCode + ':' + dictionaryVersion),
                new DictionaryItemsCacheValue(items));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ResolvedSystemDictionaryItem> findDictionaryItem(
            String dictionaryCode, long dictionaryVersion, String itemCode) {
        return read(key(DICTIONARY_ITEM, dictionaryCode + ':' + dictionaryVersion + ':' + itemCode));
    }

    /** {@inheritDoc} */
    @Override
    public void putDictionaryItem(
            String dictionaryCode,
            long dictionaryVersion,
            String itemCode,
            ResolvedSystemDictionaryItem item) {
        put(key(DICTIONARY_ITEM, dictionaryCode + ':' + dictionaryVersion + ':' + itemCode), item);
    }

    /** {@inheritDoc} */
    @Override
    public void evictDictionary(String dictionaryCode) {
        invalidateLocal(DICTIONARY_ITEMS);
        invalidateLocal(DICTIONARY_ITEM);
    }

    private <T> Optional<T> read(CacheEntryKey<T> key) {
        return enabled ? Optional.ofNullable(cacheService.get(key)) : Optional.empty();
    }

    private <T> void put(CacheEntryKey<T> key, T value) {
        if (enabled) {
            cacheService.put(key, Objects.requireNonNull(value, "缓存 Projection 不能为空"));
        }
    }

    private void invalidateLocal(CacheRegion<?> region) {
        if (enabled) {
            cacheService.invalidateLocalRegion(region);
        }
    }

    private static String parameterSubject(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version) {
        return segment(lookupScopeCode) + ':' + parameterKey + ':'
                + resolvedScopeType.name().toLowerCase(java.util.Locale.ROOT) + ':'
                + segment(resolvedScopeCode) + ':' + version;
    }

    private static String segment(String value) {
        return value == null || value.isEmpty() ? CacheScope.GLOBAL_VALUE : value;
    }

    private static <T> CacheEntryKey<T> key(CacheRegion<T> region, String subject) {
        return CacheEntryKey.of(region, GLOBAL, subject);
    }

    private static <T> CacheRegion<T> region(
            String capability,
            CacheValueType<T> valueType,
            Duration localTtl,
            Duration remoteTtl) {
        return new CacheRegion<>("system", capability, 1, valueType, localTtl, remoteTtl, true, true);
    }

    /** 明确封装 List 的可恢复缓存值，避免泛型擦除后反序列化成 Map。 */
    public record DictionaryItemsCacheValue(List<SystemDictionaryItemOption> items) {
        public DictionaryItemsCacheValue {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
