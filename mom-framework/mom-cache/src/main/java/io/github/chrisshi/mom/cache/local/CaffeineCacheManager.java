package io.github.chrisshi.mom.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.chrisshi.mom.cache.api.CacheType;
import io.github.chrisshi.mom.cache.config.CaffeineCacheProperties;

import java.util.EnumMap;

/**
 * Creates isolated local caches by cache type.
 */
public class CaffeineCacheManager {

    private final CaffeineCacheProperties properties;
    private final EnumMap<CacheType, Cache<String, Object>> caches = new EnumMap<>(CacheType.class);

    public CaffeineCacheManager(CaffeineCacheProperties properties) {
        this.properties = properties;
    }

    public Cache<String, Object> getCache(CacheType type) {
        return caches.computeIfAbsent(type, key -> Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.expireAfterWrite())
                .build());
    }
}
