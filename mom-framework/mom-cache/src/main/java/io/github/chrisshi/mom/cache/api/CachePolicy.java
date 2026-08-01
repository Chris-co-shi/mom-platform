package io.github.chrisshi.mom.cache.api;

import java.time.Duration;

/**
 * Cache strategy definition.
 */
public interface CachePolicy {

    Duration ttl();

    boolean localEnabled();

    boolean redisEnabled();
}
