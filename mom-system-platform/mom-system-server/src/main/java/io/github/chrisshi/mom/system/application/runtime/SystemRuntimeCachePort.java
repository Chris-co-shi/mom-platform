package io.github.chrisshi.mom.system.application.runtime;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * System Runtime 可重建 Projection Cache Port。
 *
 * <p>Cache 不是权威数据源。调用方必须先从 PostgreSQL 读取当前启用状态、发布指针或生效记录版本，才能
 * 使用本 Port；Cache Miss、Redis 故障、Value 损坏或版本不匹配必须回源 PostgreSQL。</p>
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

    /** 读取经过 PostgreSQL 生效 Header 确认后的 Parameter Projection。 */
    Optional<ResolvedSystemParameter> findParameter(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version);

    /** 写入经过 PostgreSQL 生效 Header 确认后的 Parameter Projection。 */
    void putParameter(
            String lookupScopeCode,
            String parameterKey,
            ParameterScopeType resolvedScopeType,
            String resolvedScopeCode,
            long version,
            ResolvedSystemParameter value);

    /** 清理同一 Parameter Key 的 GLOBAL 与全部 Application Projection。 */
    void evictParameter(String parameterKey);

    /** 读取指定字典版本下的有效 Item 选择项。 */
    Optional<List<SystemDictionaryItemOption>> findDictionaryItems(
            String dictionaryCode, long dictionaryVersion);

    /** 写入指定字典版本下的有效 Item 选择项。 */
    void putDictionaryItems(
            String dictionaryCode,
            long dictionaryVersion,
            List<SystemDictionaryItemOption> items);

    /** 读取指定字典版本下的单项兼容 Projection。 */
    Optional<ResolvedSystemDictionaryItem> findDictionaryItem(
            String dictionaryCode,
            long dictionaryVersion,
            String itemCode);

    /** 写入指定字典版本下的单项兼容 Projection。 */
    void putDictionaryItem(
            String dictionaryCode,
            long dictionaryVersion,
            String itemCode,
            ResolvedSystemDictionaryItem item);

    /** 清理指定字典的 Active List 与单项 Projection。 */
    void evictDictionary(String dictionaryCode);
}
