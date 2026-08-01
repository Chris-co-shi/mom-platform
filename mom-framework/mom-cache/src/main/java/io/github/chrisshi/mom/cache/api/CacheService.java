package io.github.chrisshi.mom.cache.api;

/**
 * Unified cache access entry point for business modules.
 */
public interface CacheService {

    <T> T get(CacheKey key, Class<T> clazz);

    <T> void put(CacheKey key, T value);

    void evict(CacheKey key);
}
