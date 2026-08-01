package io.github.chrisshi.mom.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.api.CacheType;

import java.time.Duration;

/**
 * Local L1 cache provider based on Caffeine cache manager.
 */
public class CaffeineCacheProvider implements CacheProvider {

    private final CaffeineCacheManager cacheManager;

    public CaffeineCacheProvider(CaffeineCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public boolean supports(CachePolicy policy) {
        return policy.localEnabled();
    }

    @Override
    public Object get(CacheKey key) {
        return cache(key).getIfPresent(key.build());
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        cache(key).put(key.build(), value);
    }

    @Override
    public void delete(CacheKey key) {
        cache(key).invalidate(key.build());
    }

    private Cache<String, Object> cache(CacheKey key) {
        return cacheManager.getCache(CacheType.valueOf(key.type().name()));
    }
}
