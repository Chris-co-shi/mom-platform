package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheValueType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 基于 Spring Boot 管理的 Jackson 3 ObjectMapper 实现缓存 JSON 线格式。
 *
 * <p>该 Adapter 不自行创建或修改 ObjectMapper，因此沿用应用统一的日期、模块和安全配置。新格式只保存
 * 稳定逻辑类型，不开启 Default Typing，也不写 Java FQCN。类型/版本不兼容与 JSON 损坏均 fail-fast 给
 * Redis Provider，由 Provider 删除精确 Key 并降级为 Miss；类无可变状态，可跨线程复用。</p>
 */
public final class JacksonCacheSerializer implements CacheSerializer {

    static final int CURRENT_FORMAT_VERSION = 1;

    private final ObjectMapper objectMapper;

    /**
     * 创建默认 Serializer。
     *
     * @param objectMapper Spring Boot 统一管理的 ObjectMapper，不得传入为缓存单独创建的 Mapper
     */
    public JacksonCacheSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public <T> String serialize(T value, CacheValueType<T> valueType) {
        Objects.requireNonNull(value, "缓存值不能为空");
        Objects.requireNonNull(valueType, "缓存值类型不能为空");
        if (!valueType.javaType().isInstance(value)) {
            throw new IllegalArgumentException("缓存值与 CacheValueType 不匹配");
        }
        try {
            String payload = objectMapper.writeValueAsString(value);
            CacheEntryEnvelope envelope = new CacheEntryEnvelope(
                    CURRENT_FORMAT_VERSION,
                    valueType.id(),
                    valueType.schemaVersion(),
                    payload
            );
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException ex) {
            throw new CacheSerializationException("缓存值 JSON 序列化失败", ex);
        }
    }

    @Override
    public <T> T deserialize(String value, CacheValueType<T> valueType) {
        Objects.requireNonNull(valueType, "缓存值类型不能为空");
        try {
            CacheEntryEnvelope envelope = objectMapper.readValue(value, CacheEntryEnvelope.class);
            verifyEnvelope(envelope, valueType);
            return objectMapper.readValue(envelope.payload(), valueType.javaType());
        } catch (IncompatibleCacheEntryException ex) {
            throw ex;
        } catch (JacksonException | NullPointerException ex) {
            throw new CacheSerializationException("缓存值 JSON 反序列化失败", ex);
        }
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new CacheSerializationException("旧缓存值 JSON 序列化失败", ex);
        }
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public <T> T deserialize(String value, Class<T> type) {
        requireConcreteType(type);
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException ex) {
            throw new CacheSerializationException("旧缓存值 JSON 反序列化失败", ex);
        }
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public CacheValueEnvelope wrap(Object value) {
        Objects.requireNonNull(value, "缓存值不能为空");
        return new CacheValueEnvelope("legacy", "1", serialize(value));
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public <T> T unwrap(CacheValueEnvelope envelope, Class<T> type) {
        Objects.requireNonNull(envelope, "旧缓存信封不能为空");
        requireConcreteType(type);
        return deserialize(envelope.payload(), type);
    }

    private static <T> void verifyEnvelope(CacheEntryEnvelope envelope, CacheValueType<T> valueType) {
        if (envelope == null || envelope.formatVersion() != CURRENT_FORMAT_VERSION) {
            throw new IncompatibleCacheEntryException("缓存信封格式版本不兼容");
        }
        if (!valueType.id().equals(envelope.valueType())) {
            throw new IncompatibleCacheEntryException("缓存逻辑类型不兼容");
        }
        if (valueType.schemaVersion() != envelope.schemaVersion()) {
            throw new IncompatibleCacheEntryException("缓存 Payload Schema 版本不兼容");
        }
        if (envelope.payload() == null) {
            throw new IncompatibleCacheEntryException("缓存信封缺少 Payload");
        }
    }

    private static void requireConcreteType(Class<?> type) {
        Objects.requireNonNull(type, "缓存恢复类型不能为空");
        if (Object.class.equals(type)) {
            throw new IllegalArgumentException("缓存反序列化禁止使用 Object.class");
        }
    }
}
