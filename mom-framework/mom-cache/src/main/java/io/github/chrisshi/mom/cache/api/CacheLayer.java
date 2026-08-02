package io.github.chrisshi.mom.cache.api;

/**
 * Cache hierarchy layer.
 */
public enum CacheLayer {

    LOCAL(10),

    REMOTE(20);

    private final int priority;

    CacheLayer(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
