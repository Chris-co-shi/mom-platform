package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;

import java.time.Instant;

/**
 * System Application Catalog 的聚合根。
 *
 * <p>Application Code 创建后不可修改，Version 同时作为 Application 元数据与全部 Navigation Draft 的
 * 聚合并发边界。enabled 是运行时即时 Kill Switch；Navigation 与展示元数据只有 Publish 后才进入不可变
 * Snapshot。该模型不表达 OAuth Client、Role、Permission 定义或可执行前端组件。</p>
 */
public record SystemApplication(
        String id,
        String applicationCode,
        ApplicationType applicationType,
        String i18nResourceCode,
        String i18nMessageKey,
        String iconKey,
        String description,
        int routeContractVersion,
        int sortOrder,
        boolean enabled,
        String publishedReleaseId,
        long publishedVersion,
        long version,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {

    /** 创建尚未持久化且尚未发布的 Application。 */
    public static SystemApplication create(
            String code, ApplicationType type, String resourceCode, String messageKey,
            String iconKey, String description, int routeContractVersion, int sortOrder, boolean enabled) {
        return new SystemApplication(null, code, type, resourceCode, messageKey, iconKey, description,
                routeContractVersion, sortOrder, enabled, null, 0L, 0L, null, null, null, null);
    }

    /** 按期望聚合版本建立管理元数据更新快照。 */
    public SystemApplication update(
            long expectedVersion, ApplicationType type, String resourceCode, String messageKey,
            String newIconKey, String newDescription, int contractVersion, int newSortOrder) {
        return new SystemApplication(id, applicationCode, type, resourceCode, messageKey, newIconKey,
                newDescription, contractVersion, newSortOrder, enabled, publishedReleaseId,
                publishedVersion, expectedVersion, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 建立即时启停更新快照。 */
    public SystemApplication changeStatus(long expectedVersion, boolean newEnabled) {
        return new SystemApplication(id, applicationCode, applicationType, i18nResourceCode, i18nMessageKey,
                iconKey, description, routeContractVersion, sortOrder, newEnabled, publishedReleaseId,
                publishedVersion, expectedVersion, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 只推进聚合 Version，用于串行化 Navigation Draft 写入。 */
    public SystemApplication touch(long expectedVersion) {
        return new SystemApplication(id, applicationCode, applicationType, i18nResourceCode, i18nMessageKey,
                iconKey, description, routeContractVersion, sortOrder, enabled, publishedReleaseId,
                publishedVersion, expectedVersion, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 原子推进当前发布指针。 */
    public SystemApplication publish(long expectedVersion, String releaseId, long releaseVersion) {
        return new SystemApplication(id, applicationCode, applicationType, i18nResourceCode, i18nMessageKey,
                iconKey, description, routeContractVersion, sortOrder, enabled, releaseId,
                releaseVersion, expectedVersion, createdBy, createdAt, updatedBy, updatedAt);
    }
}
