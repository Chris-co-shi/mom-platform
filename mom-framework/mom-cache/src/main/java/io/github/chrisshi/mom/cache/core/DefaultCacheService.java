package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.cache.api.CacheType;

import java.time.Duration;

public class DefaultCacheService implements CacheService {

    private final CachePolicyRegistry policyRegistry;
    private final CacheProviderSelector providerSelector;

    public DefaultCacheService(CachePolicyRegistry policyRegistry,
                               CacheProviderSelector providerSelector) {
        this.policyRegistry = policyRegistry;
        this.providerSelector = providerSelector;
    }

    @Override
    public <T> T get(CacheType type, String key, Class<T> clazz) {
        CachePolicy policy = policyRegistry.get(type);
        CacheProvider provider = providerSelector.select(policy);
        Object value = provider.get(key);
        return clazz.cast(value);
    }

    @Override
    public void put(CacheType type, String key, Object value) {
        CachePolicy policy = policyRegistry.get(type);
        CacheProvider provider = providerSelector.select(policy);
        provider.put(key, value, policy.ttl());
    }

    @Override
    public void evict(CacheType type, String key) {
        CachePolicy policy = policyRegistry.get(type);
        CacheProvider provider = providerSelector.select(policy);
        provider.delete(key);
    }
}
