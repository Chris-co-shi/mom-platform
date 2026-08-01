package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis based second-level cache provider.
 */
public class RedisCacheProvider implements CacheProvider {

    private final StringRedisTemplate redisTemplate;
    private final CacheSerializer serializer;

    public RedisCacheProvider(StringRedisTemplate redisTemplate, CacheSerializer serializer) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
    }

    @Override
    public boolean supports(CachePolicy policy) {
        return policy != null && policy.redisEnabled();
    }

    @Override
    public Object get(CacheKey key) {
        String value = redisTemplate.opsForValue().get(key.value());
        return value == null ? null : serializer.deserialize(value, Object.class);
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key.value(), serializer.serialize(value), ttl);
    }

    @Override
    public void delete(CacheKey key) {
        redisTemplate.delete(key.value());
    }
}
