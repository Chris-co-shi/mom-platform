package io.github.chrisshi.mom.cache.redis;

/**
 * Wrapper for Redis cache payload metadata.
 */
public record CacheValueEnvelope(
        String type,
        String version,
        String payload
) {
}
