package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheKey;
import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CachePolicy;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheScope;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.cache.api.CacheType;
import io.github.chrisshi.mom.cache.api.CacheValueType;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 默认 CacheService，实现 typed API 与旧 API 兼容桥。
 *
 * <p>所有物理 Key 由固定 environment 和显式 Scope 构建。旧调用被桥接为 Global typed Region，并记录
 * Legacy Usage；不会再驱动 Redis 使用 Object.class。缓存失败按 Provider 约定变成 Miss，Loader 异常保持
 * 原样。类自身无可变请求状态，可并发调用。</p>
 */
public class DefaultCacheService implements CacheService {

    private final CachePolicyRegistry policyRegistry;
    private final MultiLevelCacheProvider cacheProvider;
    private final String environment;
    private final CacheMetrics metrics;

    /**
     * 创建保留兼容的本地环境 Service。
     *
     * @param policyRegistry 旧 CacheType 策略表
     * @param cacheProvider 多级缓存编排器
     */
    public DefaultCacheService(CachePolicyRegistry policyRegistry,
                               MultiLevelCacheProvider cacheProvider) {
        this(policyRegistry, cacheProvider, "local", new CacheMetrics(null));
    }

    /**
     * 创建生产 CacheService。
     *
     * @param policyRegistry 旧 API 策略表
     * @param cacheProvider 多级缓存编排器
     * @param environment 部署环境 Key 段
     * @param metrics 指标记录器
     */
    public DefaultCacheService(
            CachePolicyRegistry policyRegistry,
            MultiLevelCacheProvider cacheProvider,
            String environment,
            CacheMetrics metrics) {
        this.policyRegistry = policyRegistry;
        this.cacheProvider = cacheProvider;
        this.environment = Objects.requireNonNull(environment, "缓存环境不能为空");
        this.metrics = metrics;
    }

    @Override
    public <T> T get(CacheEntryKey<T> key) {
        return cacheProvider.get(key, key.build(environment));
    }

    @Override
    public <T> T getOrLoad(CacheEntryKey<T> key, Supplier<T> loader) {
        Objects.requireNonNull(loader, "缓存 Loader 不能为空");
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    @Override
    public <T> void put(CacheEntryKey<T> key, T value) {
        Objects.requireNonNull(value, "缓存值不能为空");
        if (!key.region().valueType().javaType().isInstance(value)) {
            throw new IllegalArgumentException("缓存值与 Region 类型不匹配");
        }
        cacheProvider.put(key, key.build(environment), value);
    }

    @Override
    public void evict(CacheEntryKey<?> key) {
        cacheProvider.evict(key, key.build(environment));
    }

    @Override
    public void invalidateLocalRegion(CacheRegion<?> region) {
        cacheProvider.invalidateLocalRegion(region);
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public <T> T get(CacheKey key, Class<T> clazz) {
        metrics.legacyUsage();
        return get(legacyKey(key, clazz));
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public <T> void put(CacheKey key, T value) {
        metrics.legacyUsage();
        Objects.requireNonNull(value, "旧缓存值不能为空");
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) value.getClass();
        put(legacyKey(key, type), value);
    }

    @Override
    @Deprecated(since = "P1.6", forRemoval = false)
    public void evict(CacheKey key) {
        metrics.legacyUsage();
        CachePolicy policy = requireLegacyPolicy(key.type());
        CacheRegion<String> region = legacyRegion(key.type(), String.class, policy);
        evict(CacheEntryKey.of(region, CacheScope.global(), key.value()));
    }

    private <T> CacheEntryKey<T> legacyKey(CacheKey key, Class<T> javaType) {
        if (Object.class.equals(javaType)) {
            throw new IllegalArgumentException("旧 CacheService 也禁止 Object.class 反序列化");
        }
        CachePolicy policy = requireLegacyPolicy(key.type());
        return CacheEntryKey.of(legacyRegion(key.type(), javaType, policy), CacheScope.global(), key.value());
    }

    private CachePolicy requireLegacyPolicy(CacheType type) {
        CachePolicy policy = policyRegistry.get(type);
        if (policy == null) {
            throw new IllegalStateException("旧 CacheType 未注册 CachePolicy: " + type.name());
        }
        return policy;
    }

    private static <T> CacheRegion<T> legacyRegion(CacheType type, Class<T> javaType, CachePolicy policy) {
        String typeName = type.name().toLowerCase(Locale.ROOT);
        String boundedContext = typeName.startsWith("system_") ? "system" : "iam";
        String capability = typeName.replace('_', '-');
        return new CacheRegion<>(
                boundedContext,
                capability,
                1,
                CacheValueType.of("legacy." + typeName, 1, javaType),
                policy.ttl(),
                policy.ttl(),
                policy.localEnabled(),
                policy.redisEnabled()
        );
    }
}
