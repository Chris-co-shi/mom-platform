package io.github.chrisshi.mom.system.infrastructure.cache;

import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheScope;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.cache.api.CacheValueType;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeCachePort;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 通过 mom-cache typed API 保存 Dynamic I18n 不可变 Release Projection。
 *
 * <p>System Application 先从 PostgreSQL 读取权威 Header，再使用包含发布版本与 checksum 的 Global Key。
 * Redis/Caffeine、序列化和损坏数据删除均由 Framework 负责；失效只清理有界 L1 Region，版本化 L2 由 TTL
 * 回收。类型无请求级可变状态，可安全并发调用。</p>
 */
@Component
public class MomCacheSystemI18nRuntimeCacheAdapter implements SystemI18nRuntimeCachePort {
    private static final CacheRegion<SystemI18nRuntimeQueryPort.RuntimeSnapshot> I18N = new CacheRegion<>(
            "system",
            "i18n-release",
            1,
            CacheValueType.of(
                    "system.i18n-runtime-snapshot", 1, SystemI18nRuntimeQueryPort.RuntimeSnapshot.class),
            Duration.ofMinutes(5),
            Duration.ofHours(12),
            true,
            true);

    private final CacheService cacheService;
    private final boolean enabled;

    /**
     * 创建 I18n Runtime Cache Adapter。
     *
     * @param cacheService mom-cache 统一入口
     * @param enabled 是否启用 System Runtime Cache
     */
    public MomCacheSystemI18nRuntimeCacheAdapter(
            CacheService cacheService,
            @Value("${mom.system.runtime-cache.enabled:false}") boolean enabled) {
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService");
        this.enabled = enabled;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SystemI18nRuntimeQueryPort.RuntimeSnapshot> find(
            SystemI18nRuntimeQueryPort.RuntimeHeader header) {
        Objects.requireNonNull(header, "header");
        if (!enabled) {
            return Optional.empty();
        }
        SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot = cacheService.get(key(header));
        return snapshot != null && matches(header, snapshot) ? Optional.of(snapshot) : Optional.empty();
    }

    /** {@inheritDoc} */
    @Override
    public void put(
            SystemI18nRuntimeQueryPort.RuntimeHeader header,
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!matches(header, snapshot)) {
            throw new IllegalArgumentException("I18n Cache Snapshot 与 PostgreSQL Header 不一致");
        }
        if (enabled) {
            cacheService.put(key(header), snapshot);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void evict(String applicationCode, String resourceCode) {
        if (enabled) {
            cacheService.invalidateLocalRegion(I18N);
        }
    }

    private static CacheEntryKey<SystemI18nRuntimeQueryPort.RuntimeSnapshot> key(
            SystemI18nRuntimeQueryPort.RuntimeHeader header) {
        return CacheEntryKey.of(
                I18N,
                CacheScope.global(),
                header.applicationCode() + ':' + header.resourceCode() + ':'
                        + header.releaseVersion() + ':' + header.locale() + ':' + header.checksum());
    }

    private static boolean matches(
            SystemI18nRuntimeQueryPort.RuntimeHeader header,
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot) {
        return header.applicationCode().equals(snapshot.applicationCode())
                && header.resourceCode().equals(snapshot.resourceCode())
                && header.locale().equals(snapshot.locale())
                && header.defaultLocale().equals(snapshot.defaultLocale())
                && header.releaseVersion() == snapshot.releaseVersion()
                && header.checksum().equals(snapshot.checksum())
                && header.fallbackCount() == snapshot.fallbackCount()
                && header.publishedAt().equals(snapshot.publishedAt());
    }
}
