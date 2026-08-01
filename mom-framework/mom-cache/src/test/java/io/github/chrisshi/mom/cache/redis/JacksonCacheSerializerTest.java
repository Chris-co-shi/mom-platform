package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheValueType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证默认 Jackson 缓存信封的类型恢复与版本拒绝语义。
 *
 * <p>测试使用与 Spring Boot 相同的 Jackson 3 API，但不启动应用上下文。损坏、类型不匹配和版本不兼容
 * 都必须显式失败，调用方据此删除精确 Key 并按 Miss 回源，禁止降级为 Map 或伪对象。</p>
 */
class JacksonCacheSerializerTest {

    private final JacksonCacheSerializer serializer = new JacksonCacheSerializer(JsonMapper.builder().build());

    @Test
    void shouldRoundTripUsingStableTypeIdInsteadOfJavaClassName() {
        CacheValueType<ExampleValue> type = CacheValueType.of("system.dictionary", 3, ExampleValue.class);

        String json = serializer.serialize(new ExampleValue("material-type", 7), type);
        ExampleValue restored = serializer.deserialize(json, type);

        assertThat(restored).isEqualTo(new ExampleValue("material-type", 7));
        assertThat(json).contains("\"formatVersion\":1", "\"valueType\":\"system.dictionary\"");
        assertThat(json).doesNotContain(ExampleValue.class.getName());
    }

    @Test
    void shouldRejectTypeAndSchemaVersionMismatch() {
        CacheValueType<ExampleValue> source = CacheValueType.of("system.dictionary", 1, ExampleValue.class);
        String json = serializer.serialize(new ExampleValue("material-type", 7), source);

        assertThatThrownBy(() -> serializer.deserialize(
                json,
                CacheValueType.of("system.parameter", 1, ExampleValue.class)))
                .isInstanceOf(IncompatibleCacheEntryException.class);
        assertThatThrownBy(() -> serializer.deserialize(
                json,
                CacheValueType.of("system.dictionary", 2, ExampleValue.class)))
                .isInstanceOf(IncompatibleCacheEntryException.class);
    }

    @Test
    void shouldRejectCorruptedPayloadInsteadOfReturningMap() {
        CacheValueType<ExampleValue> type = CacheValueType.of("system.dictionary", 1, ExampleValue.class);

        assertThatThrownBy(() -> serializer.deserialize("{not-json", type))
                .isInstanceOf(CacheSerializationException.class);
    }

    private record ExampleValue(String code, int version) {
    }
}
