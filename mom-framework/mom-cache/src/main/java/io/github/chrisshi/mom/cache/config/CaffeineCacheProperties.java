package io.github.chrisshi.mom.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Caffeine local cache configuration properties.
 */
@ConfigurationProperties(prefix = "mom.cache.caffeine")
public record CaffeineCacheProperties(
        long maximumSize,
        Duration expireAfterWrite
) {

    public CaffeineCacheProperties {
        if (maximumSize <= 0) {
            maximumSize = 10000;
        }
        if (expireAfterWrite == null) {
            expireAfterWrite = Duration.ofMinutes(10);
        }
    }
}
