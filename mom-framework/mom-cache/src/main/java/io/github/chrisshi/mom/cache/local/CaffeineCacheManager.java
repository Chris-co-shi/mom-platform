package io.github.chrisshi.mom.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.chrisshi.mom.cache.api.CacheType;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.config.CaffeineCacheProperties;

import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 为每个 typed Region 创建独立的 Caffeine L1 Cache。
 *
 * <p>Region 身份与 TTL 在首次创建后固定，避免不同 bounded context 或 Key 版本共享本地条目。旧 CacheType
 * Map 仅为旧 Provider SPI 保留。底层 ConcurrentMap 与 Caffeine Cache 均支持并发访问；Region 失效只清理
 * 当前进程，不影响 Redis L2。</p>
 */
public class CaffeineCacheManager {

    private final CaffeineCacheProperties properties;
    private final EnumMap<CacheType, Cache<String, Object>> caches = new EnumMap<>(CacheType.class);
    private final ConcurrentMap<String, Cache<String, Object>> regionCaches = new ConcurrentHashMap<>();

    /** @param properties 全局容量上限与旧 API 默认 TTL */
    public CaffeineCacheManager(CaffeineCacheProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取旧 CacheType 的本地实例。
     *
     * @param type 旧缓存类型
     * @return 对应 Caffeine Cache
     * @deprecated 新代码使用 {@link #getCache(CacheRegion)}
     */
    @Deprecated(since = "P1.6", forRemoval = false)
    public Cache<String, Object> getCache(CacheType type) {
        synchronized (caches) {
            return caches.computeIfAbsent(type, key -> Caffeine.newBuilder()
                    .maximumSize(properties.maximumSize())
                    .expireAfterWrite(properties.expireAfterWrite())
                    .build());
        }
    }

    /**
     * 获取或创建 Region 隔离的本地缓存。
     *
     * @param region 类型化缓存区域
     * @return 使用 Region L1 TTL 的 Caffeine Cache
     */
    public Cache<String, Object> getCache(CacheRegion<?> region) {
        return regionCaches.computeIfAbsent(region.identity(), ignored -> Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(region.localTtl())
                .build());
    }

    /**
     * 清理当前进程的一个 Region。
     *
     * @param region 待清理 Region；不存在时无副作用
     */
    public void invalidate(CacheRegion<?> region) {
        Cache<String, Object> cache = regionCaches.get(region.identity());
        if (cache != null) {
            cache.invalidateAll();
        }
    }
}
