package io.github.chrisshi.mom.cache.local;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheType;
import io.github.chrisshi.mom.cache.config.CaffeineCacheProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 旧 CacheKey 兼容入口的 Caffeine Provider 行为测试。 */
@SuppressWarnings("deprecation")
class CaffeineCacheProviderTest {

    @Test
    void shouldPutAndGetValue() {
        CaffeineCacheProvider provider = provider();
        CacheKey key = new CacheKey(CacheType.SYSTEM_I18N, "zh-CN");

        provider.put(key, "hello", Duration.ofMinutes(1));

        assertThat(provider.get(key)).isEqualTo("hello");
    }

    @Test
    void shouldEvictValue() {
        CaffeineCacheProvider provider = provider();
        CacheKey key = new CacheKey(CacheType.SYSTEM_DICTIONARY, "status");

        provider.put(key, "active", Duration.ofMinutes(1));
        provider.delete(key);

        assertThat(provider.get(key)).isNull();
    }

    private static CaffeineCacheProvider provider() {
        CaffeineCacheProperties properties =
                new CaffeineCacheProperties(100, Duration.ofMinutes(10));
        return new CaffeineCacheProvider(new CaffeineCacheManager(properties));
    }
}
