package io.github.chrisshi.mom.cache.config;

import java.time.Duration;

/**
 * Caffeine local cache configuration metadata holder.
 */
public record CaffeineCacheProperties(
        long maximumSize,
        Duration expireAfterWrite
) {
}
