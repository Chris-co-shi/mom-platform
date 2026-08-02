package io.github.chrisshi.mom.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheLayer;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheType;

import java.time.Duration;

/**
 * 基于 Caffeine 的进程内 L1 Cache Adapter。
 *
 * <p>Adapter 不负责 Key Scope、序列化或 L2 协调，只按 Core 提供的完整 Key 访问对应 Region。Caffeine
 * 原生支持并发，基础设施位于进程内，不会把异常降级为远程旧值；整个 Region 失效仅清理本实例。</p>
 */
public class CaffeineCacheProvider implements CacheProvider {

    private final CaffeineCacheManager cacheManager;

    /** @param cacheManager Region 隔离的 Caffeine 管理器 */
    public CaffeineCacheProvider(CaffeineCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public CacheLayer layer() {
        return CacheLayer.LOCAL;
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public boolean supports(CachePolicy policy) {
        return policy != null && policy.localEnabled();
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public Object get(CacheKey key) {
        return cache(key).getIfPresent(key.build());
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public void put(CacheKey key, Object value, Duration ttl) {
        cache(key).put(key.build(), value);
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public void delete(CacheKey key) {
        cache(key).invalidate(key.build());
    }

    private Cache<String, Object> cache(CacheKey key) {
        return cacheManager.getCache(CacheType.valueOf(key.type().name()));
    }

    @Override
    public <T> T get(CacheEntryKey<T> key, String storageKey) {
        Object value = cacheManager.getCache(key.region()).getIfPresent(storageKey);
        return value == null ? null : key.region().valueType().javaType().cast(value);
    }

    @Override
    public <T> void put(CacheEntryKey<T> key, String storageKey, T value, Duration ttl) {
        cacheManager.getCache(key.region()).put(storageKey, value);
    }

    @Override
    public void delete(CacheEntryKey<?> key, String storageKey) {
        cacheManager.getCache(key.region()).invalidate(storageKey);
    }

    @Override
    public void invalidateLocalRegion(CacheRegion<?> region) {
        cacheManager.invalidate(region);
    }
}
