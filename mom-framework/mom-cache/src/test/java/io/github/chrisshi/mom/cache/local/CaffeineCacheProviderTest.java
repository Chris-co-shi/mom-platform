package io.github.chrisshi.mom.cache.local;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineCacheProviderTest {

    @Test
    void shouldPutAndGetValue() {
        CaffeineCacheProvider provider = new CaffeineCacheProvider();
        CacheKey key = new TestCacheKey(CacheType.SYSTEM_I18N, "zh-CN");

        provider.put(key, "hello", Duration.ofMinutes(1));

        assertThat(provider.get(key)).isEqualTo("hello");
    }

    @Test
    void shouldEvictValue() {
        CaffeineCacheProvider provider = new CaffeineCacheProvider();
        CacheKey key = new TestCacheKey(CacheType.SYSTEM_DICTIONARY, "status");

        provider.put(key, "active", Duration.ofMinutes(1));
        provider.delete(key);

        assertThat(provider.get(key)).isNull();
    }

    private record TestCacheKey(CacheType type, String value) implements CacheKey {
    }
}
