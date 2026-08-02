package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheType;

/**
 * Runtime cache context.
 */
public record CacheContext(CacheType type, String key) {
}
