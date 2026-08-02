package io.github.chrisshi.mom.cache.api;

import java.util.function.Supplier;

/**
 * MOM 业务模块访问可重建缓存投影的统一入口。
 *
 * <p>新调用必须使用 {@link CacheEntryKey} 明确类型与 Global/Factory Scope。缓存基础设施失败统一按 Miss
 * 处理，由 Loader 或业务 Adapter 回源；Loader 异常保持原样抛出。最终授权决策不属于本接口允许的缓存值。
 * 实现可被多个请求线程并发调用。</p>
 */
public interface CacheService {

    /**
     * 读取类型化缓存值。
     *
     * @param key 类型化缓存 Key
     * @param <T> 缓存值类型
     * @return 命中值；完全 Miss 或基础设施不可用时返回 null
     */
    <T> T get(CacheEntryKey<T> key);

    /**
     * 读取缓存，Miss 时由调用方提供的 Loader 回源并回填。
     *
     * @param key 类型化缓存 Key
     * @param loader 权威数据回源函数，仅在完全 Miss 时调用一次
     * @param <T> 缓存值类型
     * @return 命中值或 Loader 返回值；Loader 返回 null 时不写缓存
     * @throws RuntimeException Loader 失败时保留原异常，缓存层不会伪造成功
     */
    <T> T getOrLoad(CacheEntryKey<T> key, Supplier<T> loader);

    /**
     * 写入 Region 启用的缓存层。
     *
     * @param key 类型化缓存 Key
     * @param value 非空且与 Region 类型一致的缓存值
     * @param <T> 缓存值类型
     */
    <T> void put(CacheEntryKey<T> key, T value);

    /**
     * 精确删除 L1/L2 Key。
     *
     * @param key 待删除的类型化 Key；删除失败不会破坏业务主流程
     */
    void evict(CacheEntryKey<?> key);

    /**
     * 清理当前进程的整个 L1 Region。
     *
     * <p>L2 通过 Key 版本与 TTL 回收，不执行无界 Redis SCAN。</p>
     *
     * @param region 待清理的 Region
     */
    void invalidateLocalRegion(CacheRegion<?> region);

    /**
     * 读取旧 CacheKey。
     *
     * @param key 旧 Key
     * @param clazz 精确恢复类型，禁止 Object.class
     * @param <T> 值类型
     * @return 命中值或 null
     * @deprecated 迁移到 {@link #get(CacheEntryKey)}；每次调用都会记录 Legacy Usage
     */
    @Deprecated(since = "P1.6", forRemoval = false)
    <T> T get(CacheKey key, Class<T> clazz);

    /**
     * 写入旧 CacheKey。
     *
     * @param key 旧 Key
     * @param value 非空值
     * @param <T> 值类型
     * @deprecated 迁移到 {@link #put(CacheEntryKey, Object)}；每次调用都会记录 Legacy Usage
     */
    @Deprecated(since = "P1.6", forRemoval = false)
    <T> void put(CacheKey key, T value);

    /**
     * 删除旧 CacheKey。
     *
     * @param key 旧 Key
     * @deprecated 迁移到 {@link #evict(CacheEntryKey)}；每次调用都会记录 Legacy Usage
     */
    @Deprecated(since = "P1.6", forRemoval = false)
    void evict(CacheKey key);
}
