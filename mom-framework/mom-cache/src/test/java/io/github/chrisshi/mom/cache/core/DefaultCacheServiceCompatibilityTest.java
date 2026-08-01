package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheType;
import io.github.chrisshi.mom.cache.config.CaffeineCacheProperties;
import io.github.chrisshi.mom.cache.local.CaffeineCacheManager;
import io.github.chrisshi.mom.cache.local.CaffeineCacheProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证旧 CacheService 方法通过 typed 桥保持行为，并为每次调用记录 Legacy Usage。
 *
 * <p>测试只使用真实 Caffeine L1，不依赖 Redis。它是旧 API 后续删除前的兼容门禁；只有全仓调用清零、
 * 生产指标连续两个 Release 为零并接受 Removal ADR 后，才允许随旧 API 一起调整。</p>
 */
@SuppressWarnings("deprecation")
class DefaultCacheServiceCompatibilityTest {

    @Test
    void shouldBridgeLegacyPutGetAndEvictWhileCountingUsage() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CacheMetrics metrics = new CacheMetrics(meterRegistry);
        CachePolicyRegistry policies = new CachePolicyRegistry();
        policies.register(CacheType.SYSTEM_DICTIONARY, new CachePolicy() {
            @Override
            public Duration ttl() {
                return Duration.ofMinutes(5);
            }

            @Override
            public boolean localEnabled() {
                return true;
            }

            @Override
            public boolean redisEnabled() {
                return false;
            }
        });
        CaffeineCacheProvider local = new CaffeineCacheProvider(new CaffeineCacheManager(
                new CaffeineCacheProperties(100, Duration.ofMinutes(5))));
        DefaultCacheService service = new DefaultCacheService(
                policies,
                new MultiLevelCacheProvider(List.of(local), metrics),
                "test",
                metrics);
        CacheKey key = new CacheKey(CacheType.SYSTEM_DICTIONARY, "material-type");

        service.put(key, new ExampleValue("material-type"));
        assertThat(service.get(key, ExampleValue.class)).isEqualTo(new ExampleValue("material-type"));
        service.evict(key);
        assertThat(service.get(key, ExampleValue.class)).isNull();

        assertThat(meterRegistry.find(CacheMetrics.LEGACY_USAGE).counter().count()).isEqualTo(4.0);
    }

    private record ExampleValue(String code) {
    }
}
