package io.github.chrisshi.mom.cache.redis;

/**
 * P1.6 之前的 Redis 缓存信封。
 *
 * <p>旧信封没有独立的格式版本和 Schema 版本，无法安全支持类型演进。为遵守渐进迁移规则，本轮保留该
 * 记录供旧 SPI 编译兼容，但新 Redis Adapter 只写入 {@link CacheEntryEnvelope}。</p>
 *
 * @deprecated 使用同时携带格式版本、逻辑类型和 Schema 版本的 {@link CacheEntryEnvelope}
 */
@Deprecated(since = "P1.6", forRemoval = false)
public record CacheValueEnvelope(
        String type,
        String version,
        String payload
) {
}
