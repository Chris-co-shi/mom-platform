package io.github.chrisshi.mom.cache.api;

import java.time.Duration;

/**
 * Cache Backend Adapter 的最小 SPI。
 *
 * <p>当前已有 Caffeine 与 Redis 两个真实生产 Adapter，因此该 SPI 的抽象成立。Provider 只实现单层读写，
 * L1/L2 顺序、回填和 Loader 均由 Core 编排。旧方法为二进制迁移保留；新方法携带精确类型和已经构建的
 *物理 Key，禁止 Provider 自行推导 Factory Scope。</p>
 */
public interface CacheProvider {

    /** @return Provider 所属缓存层，用于确定固定查询顺序 */
    CacheLayer layer();

    /**
     * 判断是否支持旧策略。
     *
     * @param policy 旧策略
     * @return 是否启用该 Provider
     * @deprecated 仅供旧 SPI 兼容
     */
    @Deprecated(since = "P1.6", forRemoval = false)
    boolean supports(CachePolicy policy);

    /**
     * 判断 Region 是否启用当前层。
     *
     * @param region 类型化 Region
     * @return 当前层是否启用
     */
    default boolean supports(CacheRegion<?> region) {
        return switch (layer()) {
            case LOCAL -> region.localEnabled();
            case REMOTE -> region.remoteEnabled();
        };
    }

    /** @deprecated 仅供旧 SPI 兼容，新 Core 不调用 */
    @Deprecated(since = "P1.6", forRemoval = false)
    Object get(CacheKey key);

    /** @deprecated 仅供旧 SPI 兼容，新 Core 不调用 */
    @Deprecated(since = "P1.6", forRemoval = false)
    void put(CacheKey key, Object value, Duration ttl);

    /** @deprecated 仅供旧 SPI 兼容，新 Core 不调用 */
    @Deprecated(since = "P1.6", forRemoval = false)
    void delete(CacheKey key);

    /**
     * 从当前缓存层读取精确类型值。
     *
     * @param key 类型化 Key
     * @param storageKey 已包含环境与 Scope 的物理 Key
     * @param <T> 值类型
     * @return 命中值；Miss 或可降级基础设施故障时返回 null
     */
    default <T> T get(CacheEntryKey<T> key, String storageKey) {
        throw new UnsupportedOperationException("Provider 尚未实现 typed cache get");
    }

    /**
     * 向当前层写入精确类型值。
     *
     * @param key 类型化 Key
     * @param storageKey 完整物理 Key
     * @param value 非空值
     * @param ttl 当前层 TTL
     * @param <T> 值类型
     */
    default <T> void put(CacheEntryKey<T> key, String storageKey, T value, Duration ttl) {
        throw new UnsupportedOperationException("Provider 尚未实现 typed cache put");
    }

    /**
     * 从当前层精确删除一个 Key。
     *
     * @param key 类型化 Key
     * @param storageKey 完整物理 Key
     */
    default void delete(CacheEntryKey<?> key, String storageKey) {
        throw new UnsupportedOperationException("Provider 尚未实现 typed cache delete");
    }

    /**
     * 清理当前 Provider 的本地 Region。
     *
     * <p>默认无操作，只有 LOCAL Provider 需要覆盖；REMOTE Provider 禁止借此执行无界扫描。</p>
     *
     * @param region 待失效 Region
     */
    default void invalidateLocalRegion(CacheRegion<?> region) {
        // 远端缓存通过版本化 Key 与 TTL 回收。
    }
}
