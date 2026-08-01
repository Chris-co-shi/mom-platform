package io.github.chrisshi.mom.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;

import java.time.Duration;

/**
 * Local L1 cache provider based on Caffeine.
 */
public class CaffeineCacheProvider implements CacheProvider {

    private final Cache<String, Object> cache;

    public CaffeineCacheProvider() {
        this.cache = Caffeine.newBuilder().build();
    }

    @Override
    public boolean supports(CachePolicy policy) {
        return policy.localEnabled();
    }

    @Override
    public Object get(CacheKey key) {
        return cache.getIfPresent(key.build());
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        cache.put(key.build(), value);
    }

    @Override
    public void delete(CacheKey key) {
        cache.invalidate(key.build());
    }
}
