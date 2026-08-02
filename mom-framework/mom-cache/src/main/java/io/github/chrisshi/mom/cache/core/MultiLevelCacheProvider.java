package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheLayer;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.api.CacheRegion;

import java.util.Comparator;
import java.util.List;

/**
 * 编排 L1/L2 查询、回填、写入与精确失效顺序。
 *
 * <p>Provider 按 {@link CacheLayer#priority()} 固定排序。L1 命中立即返回；L2 命中只回填已启用的 L1；
 * 全部 Miss 交还 CacheService Loader。远端失败由 Adapter 转换为 Miss，因此 Core 不吞 Loader/业务异常。
 * Provider 列表和指标引用构造后不变，可被并发请求安全复用。</p>
 */
public class MultiLevelCacheProvider {

    private final List<CacheProvider> providers;
    private final CacheMetrics metrics;

    /**
     * 使用无指标模式创建编排器，保留既有构造兼容。
     *
     * @param providers 可用 Provider 列表
     */
    public MultiLevelCacheProvider(List<CacheProvider> providers) {
        this(providers, new CacheMetrics(null));
    }

    /**
     * 创建多级缓存编排器。
     *
     * @param providers Caffeine/Redis 等真实 Adapter
     * @param metrics 低基数指标记录器
     */
    public MultiLevelCacheProvider(List<CacheProvider> providers, CacheMetrics metrics) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(provider -> provider.layer().priority()))
                .toList();
        this.metrics = metrics;
    }

    /** @deprecated 仅供旧 Core 兼容，新 CacheService 使用 typed 方法 */
    @Deprecated(since = "P1.6", forRemoval = false)
    public Object get(CacheKey key, CachePolicy policy) {
        for (CacheProvider provider : providers) {
            if (!provider.supports(policy)) {
                continue;
            }
            Object value = provider.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** @deprecated 仅供旧 Core 兼容，新 CacheService 使用 typed 方法 */
    @Deprecated(since = "P1.6", forRemoval = false)
    public void put(CacheKey key, Object value, CachePolicy policy) {
        providers.stream()
                .filter(provider -> provider.supports(policy))
                .forEach(provider -> provider.put(key, value, policy.ttl()));
    }

    /** @deprecated 仅供旧 Core 兼容，新 CacheService 使用 typed 方法 */
    @Deprecated(since = "P1.6", forRemoval = false)
    public void evict(CacheKey key, CachePolicy policy) {
        providers.stream()
                .filter(provider -> provider.supports(policy))
                .forEach(provider -> provider.delete(key));
    }

    /**
     * 按 L1→L2 顺序读取，L2 命中时回填 L1。
     *
     * @param key 类型化 Key
     * @param storageKey 完整物理 Key
     * @param <T> 值类型
     * @return 命中值或 null
     */
    public <T> T get(CacheEntryKey<T> key, String storageKey) {
        CacheProvider localProvider = localProvider(key.region());
        for (CacheProvider provider : providers) {
            if (!provider.supports(key.region())) {
                continue;
            }
            T value = provider.get(key, storageKey);
            if (value == null) {
                metrics.miss(provider.layer());
                continue;
            }
            metrics.hit(provider.layer());
            if (provider.layer() == CacheLayer.REMOTE && localProvider != null) {
                localProvider.put(key, storageKey, value, key.region().localTtl());
            }
            return value;
        }
        return null;
    }

    /**
     * 写入 Region 启用的全部层。
     *
     * @param key 类型化 Key
     * @param storageKey 完整物理 Key
     * @param value 非空值
     * @param <T> 值类型
     */
    public <T> void put(CacheEntryKey<T> key, String storageKey, T value) {
        providers.stream()
                .filter(provider -> provider.supports(key.region()))
                .forEach(provider -> provider.put(
                        key,
                        storageKey,
                        value,
                        provider.layer() == CacheLayer.LOCAL
                                ? key.region().localTtl()
                                : key.region().remoteTtl()));
    }

    /**
     * 在所有启用层精确删除 Key。
     *
     * @param key 类型化 Key
     * @param storageKey 完整物理 Key
     */
    public void evict(CacheEntryKey<?> key, String storageKey) {
        providers.stream()
                .filter(provider -> provider.supports(key.region()))
                .forEach(provider -> provider.delete(key, storageKey));
        metrics.eviction("key");
    }

    /**
     * 仅清理进程内 L1 Region，不扫描 L2。
     *
     * @param region 待失效 Region
     */
    public void invalidateLocalRegion(CacheRegion<?> region) {
        providers.stream()
                .filter(provider -> provider.layer() == CacheLayer.LOCAL)
                .forEach(provider -> provider.invalidateLocalRegion(region));
        metrics.eviction("region");
    }

    private CacheProvider localProvider(CacheRegion<?> region) {
        return providers.stream()
                .filter(provider -> provider.layer() == CacheLayer.LOCAL)
                .filter(provider -> provider.supports(region))
                .findFirst()
                .orElse(null);
    }
}
