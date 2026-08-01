package io.github.chrisshi.mom.cache.api;

/**
 * Standard cache key abstraction.
 */
public record CacheKey(CacheType type, String value) {

    public String build() {
        return "mom:cache:" + type.name().toLowerCase() + ":" + value;
    }
}
