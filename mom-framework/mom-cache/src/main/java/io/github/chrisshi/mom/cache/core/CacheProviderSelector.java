package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;

import java.util.List;

/**
 * Selects a cache provider according to cache policy.
 */
public class CacheProviderSelector {

    private final List<CacheProvider> providers;

    public CacheProviderSelector(List<CacheProvider> providers) {
        this.providers = providers;
    }

    public CacheProvider select(CachePolicy policy) {
        return providers.stream()
                .filter(provider -> provider.supports(policy))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No cache provider found"));
    }
}
