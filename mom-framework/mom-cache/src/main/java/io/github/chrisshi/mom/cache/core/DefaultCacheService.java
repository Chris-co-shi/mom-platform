package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheService;

public class DefaultCacheService implements CacheService {

    private final CachePolicyRegistry policyRegistry;
    private final MultiLevelCacheProvider cacheProvider;

    public DefaultCacheService(CachePolicyRegistry policyRegistry,
                               MultiLevelCacheProvider cacheProvider) {
        this.policyRegistry = policyRegistry;
        this.cacheProvider = cacheProvider;
    }

    @Override
    public <T> T get(CacheKey key, Class<T> clazz) {
        CachePolicy policy = policyRegistry.get(key.type());
        Object value = cacheProvider.get(key, policy);
        return value == null ? null : clazz.cast(value);
    }

    @Override
    public <T> void put(CacheKey key, T value) {
        CachePolicy policy = policyRegistry.get(key.type());
        cacheProvider.put(key, value, policy);
    }

    @Override
    public void evict(CacheKey key) {
        CachePolicy policy = policyRegistry.get(key.type());
        cacheProvider.evict(key, policy);
    }
}
