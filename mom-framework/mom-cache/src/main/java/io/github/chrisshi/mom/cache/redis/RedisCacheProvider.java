package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;

import java.time.Duration;

/**
 * Redis based second-level cache provider.
 */
public class RedisCacheProvider implements CacheProvider {

    @Override
    public boolean supports(CachePolicy policy) {
        return policy != null && policy.redisEnabled();
    }

    @Override
    public Object get(CacheKey key) {
        return null;
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        // Redis implementation will be wired with RedisTemplate in next slice.
    }

    @Override
    public void delete(CacheKey key) {
        // Redis eviction implementation will be wired in next slice.
    }
}
