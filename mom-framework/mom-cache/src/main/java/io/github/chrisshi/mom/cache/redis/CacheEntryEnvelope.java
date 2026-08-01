package io.github.chrisshi.mom.cache.redis;

/**
 * Redis L2 缓存的版本化 JSON 信封。
 *
 * <p>信封只保存稳定逻辑类型和显式版本，不保存 Java FQCN。Payload 是由统一 ObjectMapper 生成的明确 JSON；
 * 反序列化必须同时匹配 formatVersion、valueType 和 schemaVersion，否则按损坏数据处理并删除精确 Key。
 * 该记录不可变，可安全跨线程使用。</p>
 *
 * @param formatVersion 信封格式版本
 * @param valueType 稳定逻辑类型标识
 * @param schemaVersion Payload Schema 版本
 * @param payload 明确 JSON Payload
 */
public record CacheEntryEnvelope(
        int formatVersion,
        String valueType,
        int schemaVersion,
        String payload
) {
}
