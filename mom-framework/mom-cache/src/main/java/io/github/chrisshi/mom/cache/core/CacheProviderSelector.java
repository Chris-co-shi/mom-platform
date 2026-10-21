package io.github.chrisshi.mom.cache.core;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;

import java.util.List;

/**
 * Selects a cache provider according to cache policy.
 */
@RequiredArgsConstructor
public class CacheProviderSelector {

    private final List<CacheProvider> providers;



    public CacheProvider select(CachePolicy policy) {
        return providers.stream()
                .filter(provider -> provider.supports(policy))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No cache provider found"));
    }
}
