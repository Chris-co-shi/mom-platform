package io.github.chrisshi.mom.cache.config;

import java.time.Duration;

/**
 * Redis cache configuration properties.
 */
public record RedisCacheProperties(
        String keyPrefix,
        boolean enabled,
        Duration timeout
) {

    public RedisCacheProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "mom:cache";
        }
        if (timeout == null) {
            timeout = Duration.ofMillis(200);
        }
    }
}
