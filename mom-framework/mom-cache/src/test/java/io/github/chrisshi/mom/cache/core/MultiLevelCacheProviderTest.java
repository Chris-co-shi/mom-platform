package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheLayer;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheScope;
import io.github.chrisshi.mom.cache.api.CacheValueType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 刻画 L1/L2 查询顺序、短路与 L2 命中后的 L1 回填语义。
 *
 * <p>该测试使用内存记录 Provider，不依赖具体 Caffeine/Redis 实现，从而把多级缓存编排责任固定在
 * Framework Core。Provider 故障与真实 Redis 恢复由独立集成测试覆盖。</p>
 */
class MultiLevelCacheProviderTest {

    private static final CacheRegion<ExampleValue> REGION = new CacheRegion<>(
            "system",
            "dictionary",
            1,
            CacheValueType.of("system.dictionary", 1, ExampleValue.class),
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            true,
            true
    );
    private static final CacheEntryKey<ExampleValue> KEY = CacheEntryKey.of(
            REGION,
            CacheScope.global(),
            "material-type"
    );

    @Test
    void shouldReturnL1HitWithoutCallingL2() {
        RecordingProvider local = new RecordingProvider(CacheLayer.LOCAL, new ExampleValue("L1"));
        RecordingProvider remote = new RecordingProvider(CacheLayer.REMOTE, new ExampleValue("L2"));
        MultiLevelCacheProvider provider = new MultiLevelCacheProvider(List.of(remote, local));

        ExampleValue value = provider.get(KEY, KEY.build("prod"));

        assertThat(value).isEqualTo(new ExampleValue("L1"));
        assertThat(local.operations).containsExactly("get");
        assertThat(remote.operations).isEmpty();
    }

    @Test
    void shouldReadL2ThenRefreshL1() {
        RecordingProvider local = new RecordingProvider(CacheLayer.LOCAL, null);
        RecordingProvider remote = new RecordingProvider(CacheLayer.REMOTE, new ExampleValue("L2"));
        MultiLevelCacheProvider provider = new MultiLevelCacheProvider(List.of(remote, local));

        ExampleValue value = provider.get(KEY, KEY.build("prod"));

        assertThat(value).isEqualTo(new ExampleValue("L2"));
        assertThat(local.operations).containsExactly("get", "put:L2");
        assertThat(remote.operations).containsExactly("get");
    }

    private record ExampleValue(String source) {
    }

    private static final class RecordingProvider implements CacheProvider {

        private final CacheLayer layer;
        private final ExampleValue storedValue;
        private final List<String> operations = new ArrayList<>();

        private RecordingProvider(CacheLayer layer, ExampleValue storedValue) {
            this.layer = layer;
            this.storedValue = storedValue;
        }

        @Override
        public CacheLayer layer() {
            return layer;
        }

        @Override
        public boolean supports(CachePolicy policy) {
            return true;
        }

        @Override
        public Object get(CacheKey key) {
            throw new UnsupportedOperationException("测试仅覆盖 typed API");
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
            throw new UnsupportedOperationException("测试仅覆盖 typed API");
        }

        @Override
        public void delete(CacheKey key) {
            throw new UnsupportedOperationException("测试仅覆盖 typed API");
        }

        @Override
        public <T> T get(CacheEntryKey<T> key, String storageKey) {
            operations.add("get");
            return storedValue == null ? null : key.region().valueType().javaType().cast(storedValue);
        }

        @Override
        public <T> void put(CacheEntryKey<T> key, String storageKey, T value, Duration ttl) {
            operations.add("put:" + ((ExampleValue) value).source());
        }

        @Override
        public void delete(CacheEntryKey<?> key, String storageKey) {
            operations.add("delete");
        }
    }
}
