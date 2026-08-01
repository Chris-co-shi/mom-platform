package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;

import java.util.List;

/**
 * Coordinates multi-level cache lookup order.
 */
public class MultiLevelCacheProvider {

    private final List<CacheProvider> providers;

    public MultiLevelCacheProvider(List<CacheProvider> providers) {
        this.providers = providers;
    }

    public Object get(CacheKey key, CachePolicy policy) {
        for (CacheProvider provider : providers) {
            if (provider.supports(policy)) {
                Object value = provider.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }
}
