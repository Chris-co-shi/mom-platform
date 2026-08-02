package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheValueType;

/**
 * Redis 缓存值与版本化 JSON 信封之间的转换契约。
 *
 * <p>当前有生产 Redis Adapter 和独立序列化契约测试两个直接消费者，抽象用于隔离 Spring Data Redis 与
 * Jackson 线格式。实现必须使用精确 {@link CacheValueType}，不得接受 Object.class 恢复或 Java 原生序列化。
 * 实现应无状态并可跨线程复用。</p>
 */
public interface CacheSerializer {

    /**
     * 把类型化值编码成完整版本信封 JSON。
     *
     * @param value 非空且匹配 valueType 的值
     * @param valueType 稳定类型与 Schema 版本
     * @param <T> 值类型
     * @return 可写入 Redis String 的 JSON
     * @throws CacheSerializationException JSON 编码失败时抛出，不包含 Payload
     */
    <T> String serialize(T value, CacheValueType<T> valueType);

    /**
     * 按精确类型解码完整版本信封。
     *
     * @param value Redis 中的 JSON
     * @param valueType 当前 Region 期望类型
     * @param <T> 值类型
     * @return 精确 Java 值，不返回 Map 或伪对象
     * @throws CacheSerializationException JSON 损坏时抛出
     * @throws IncompatibleCacheEntryException 类型或版本不兼容时抛出
     */
    <T> T deserialize(String value, CacheValueType<T> valueType);

    /** @deprecated 仅供旧 Serializer SPI 兼容 */
    @Deprecated(since = "P1.6", forRemoval = false)
    String serialize(Object value);

    /** @deprecated 仅供旧 Serializer SPI 兼容，仍禁止 Object.class */
    @Deprecated(since = "P1.6", forRemoval = false)
    <T> T deserialize(String value, Class<T> type);

    /** @deprecated 旧信封不具备完整版本语义 */
    @Deprecated(since = "P1.6", forRemoval = false)
    CacheValueEnvelope wrap(Object value);

    /** @deprecated 旧信封不具备完整版本语义，仍禁止 Object.class */
    @Deprecated(since = "P1.6", forRemoval = false)
    <T> T unwrap(CacheValueEnvelope envelope, Class<T> type);
}
