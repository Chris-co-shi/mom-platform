package io.github.chrisshi.mom.cache.redis;

/**
 * Redis value serializer abstraction.
 */
public interface CacheSerializer {

    String serialize(Object value);

    <T> T deserialize(String value, Class<T> type);
}
