package io.github.chrisshi.mom.cache.api;

/**
 * Defines cache failure behavior. Cache infrastructure failures must not break business flows.
 */
public interface CacheFailurePolicy {

    Object onFailure(CacheKey key, RuntimeException exception);
}
