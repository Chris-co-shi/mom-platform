package io.github.chrisshi.mom.cache.api;

import java.time.Duration;

/**
 * Cache backend provider SPI.
 */
public interface CacheProvider {

    CacheLayer layer();

    boolean supports(CachePolicy policy);

    Object get(CacheKey key);

    void put(CacheKey key, Object value, Duration ttl);

    void delete(CacheKey key);
}
