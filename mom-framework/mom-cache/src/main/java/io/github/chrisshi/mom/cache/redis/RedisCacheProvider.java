package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheLayer;
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
    public CacheLayer layer() {
        return CacheLayer.REMOTE;
    }

    @Override
    public boolean supports(CachePolicy policy) {
        return policy != null && policy.redisEnabled();
    }

    @Override
    public Object get(CacheKey key) {
        try {
            String value = redisTemplate.opsForValue().get(namespace(key));
            return value == null ? null : serializer.deserialize(value, Object.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        CacheValueEnvelope envelope = serializer.wrap(value);
        redisTemplate.opsForValue().set(namespace(key), serializer.serialize(envelope), ttl);
    }

    @Override
    public void delete(CacheKey key) {
        try {
            redisTemplate.delete(namespace(key));
        } catch (RuntimeException ignored) {
            // cache eviction failure must not break business flow
        }
    }

    private String namespace(CacheKey key) {
        return "mom:cache:v1:" + key.value();
    }
}
