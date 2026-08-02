package io.github.chrisshi.mom.cache.api;

/**
 * P1.6 之前不包含 Environment 与 Factory Scope 的旧缓存 Key。
 *
 * <p>该类型仅用于兼容既有调用，统一入口会把它桥接为 {@link CacheEntryKey}。实例不可变；由于无法表达
 * Factory 隔离，新生产代码不得继续使用。</p>
 *
 * @deprecated 使用携带 {@link CacheScope} 与 {@link CacheRegion} 的 {@link CacheEntryKey}
 */
@Deprecated(since = "P1.6", forRemoval = false)
public record CacheKey(CacheType type, String value) {

    /**
     * 构建旧格式 Key，仅供旧 Provider 二进制兼容。
     *
     * @return 不具备 Factory 隔离能力的旧格式 Key
     * @deprecated 新实现由 {@link CacheEntryKey#build(String)} 构建完整 Key
     */
    @Deprecated(since = "P1.6", forRemoval = false)
    public String build() {
        return "mom:cache:" + type.name().toLowerCase() + ":" + value;
    }
}
