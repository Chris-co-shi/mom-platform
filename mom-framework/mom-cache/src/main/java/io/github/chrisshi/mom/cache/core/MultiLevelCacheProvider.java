package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;

import java.util.Comparator;
import java.util.List;

/**
 * Coordinates multi-level cache lookup order.
 *
 * <p>The orchestration layer owns cache hierarchy decisions while individual
 * providers only implement backend operations.</p>
 */
public class MultiLevelCacheProvider {

    private final List<CacheProvider> providers;

    public MultiLevelCacheProvider(List<CacheProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(this::priority))
                .toList();
    }

    public Object get(CacheKey key, CachePolicy policy) {
        for (CacheProvider provider : providers) {
            if (!provider.supports(policy)) {
                continue;
            }
            Object value = provider.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public void put(CacheKey key, Object value, CachePolicy policy) {
        providers.stream()
                .filter(provider -> provider.supports(policy))
                .forEach(provider -> provider.put(key, value, policy.ttl()));
    }

    public void evict(CacheKey key, CachePolicy policy) {
        providers.stream()
                .filter(provider -> provider.supports(policy))
                .forEach(provider -> provider.delete(key));
    }

    private int priority(CacheProvider provider) {
        return provider.getClass().getSimpleName().contains("Caffeine") ? 10 : 20;
    }
}
