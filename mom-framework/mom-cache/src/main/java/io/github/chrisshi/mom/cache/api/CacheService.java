package io.github.chrisshi.mom.cache.api;

/**
 * Unified cache access entry point for business modules.
 */
public interface CacheService {

    <T> T get(CacheType type, String key, Class<T> clazz);

    void put(CacheType type, String key, Object value);

    void evict(CacheType type, String key);
}
