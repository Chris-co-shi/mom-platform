package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry for cache policies.
 *
 * <p>The first implementation keeps policies in memory. Dynamic configuration
 * will be introduced by a future platform configuration capability.</p>
 */
public class CachePolicyRegistry {

    private final Map<CacheType, CachePolicy> policies = new EnumMap<>(CacheType.class);

    public void register(CacheType type, CachePolicy policy) {
        policies.put(type, policy);
    }

    public CachePolicy get(CacheType type) {
        return policies.get(type);
    }
}
