package io.github.chrisshi.mom.cache.api;

import java.time.Duration;

/**
 * Cache backend provider SPI.
 */
public interface CacheProvider {

    boolean supports(CachePolicy policy);

    Object get(String key);

    void put(String key, Object value, Duration ttl);

    void delete(String key);
}
