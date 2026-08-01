package io.github.chrisshi.mom.system.application.runtime;

import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;

import java.util.Optional;

/**
 * System Runtime 不可变 Projection Cache Port。
 *
 * <p>Cache 不是权威数据源。调用方必须先从 PostgreSQL 读取当前启用状态、Release 指针、版本和 checksum，才能
 * 使用本 Port；Cache Miss、Redis 故障或损坏必须回源 PostgreSQL。</p>
 */
public interface SystemRuntimeCachePort {

    /** 读取指定不可变 Catalog Release Snapshot。 */
    Optional<SystemCatalogSnapshot> findCatalog(
            String applicationCode, long releaseVersion, String checksum);

    /** 写入指定不可变 Catalog Release Snapshot。 */
    void putCatalog(
            String applicationCode, long releaseVersion, String checksum,
            SystemCatalogSnapshot snapshot);

    /** best-effort 清理指定 Application 的所有 Catalog Projection。 */
    void evictCatalog(String applicationCode);
}
