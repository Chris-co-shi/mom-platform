package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;

import java.time.Instant;

/**
 * Application 内可编辑 Navigation Draft 节点。
 *
 * <p>routeKey 创建后不可修改，只能由客户端静态 Registry 映射为 Path、Component 和 Layout。Parent、循环、
 * 深度、节点数量与跨 Application/Channel 约束由 Application Service 在同一事务内校验；数据库不建立物理
 * 外键。实例不可变，可安全用于全树校验和确定性快照构建。</p>
 */
public record SystemNavigationItem(
        String id,
        String applicationId,
        String parentId,
        ClientChannel clientChannel,
        NavigationType navigationType,
        String routeKey,
        String i18nResourceCode,
        String i18nMessageKey,
        String permissionCode,
        String iconKey,
        boolean visibleInMenu,
        boolean visibleInBreadcrumb,
        boolean visibleInTab,
        boolean keepAlive,
        int sortOrder,
        boolean enabled,
        long version,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {

    /** 创建未持久化节点。 */
    public static SystemNavigationItem create(
            String applicationId, String parentId, ClientChannel channel, NavigationType type,
            String routeKey, String resourceCode, String messageKey, String permissionCode,
            String iconKey, boolean visibleInMenu, boolean visibleInBreadcrumb,
            boolean visibleInTab, boolean keepAlive, int sortOrder, boolean enabled) {
        return new SystemNavigationItem(null, applicationId, parentId, channel, type, routeKey,
                resourceCode, messageKey, permissionCode, iconKey, visibleInMenu,
                visibleInBreadcrumb, visibleInTab, keepAlive, sortOrder, enabled,
                0L, null, null, null, null);
    }

    /** 更新可变元数据、Parent 与排序，routeKey/Application/Channel 保持不变。 */
    public SystemNavigationItem update(
            long expectedVersion, String newParentId, NavigationType type,
            String resourceCode, String messageKey, String newPermissionCode, String newIconKey,
            boolean newVisibleInMenu, boolean newVisibleInBreadcrumb, boolean newVisibleInTab,
            boolean newKeepAlive, int newSortOrder) {
        return new SystemNavigationItem(id, applicationId, newParentId, clientChannel, type, routeKey,
                resourceCode, messageKey, newPermissionCode, newIconKey, newVisibleInMenu,
                newVisibleInBreadcrumb, newVisibleInTab, newKeepAlive, newSortOrder, enabled,
                expectedVersion, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 建立 Draft 启停快照。 */
    public SystemNavigationItem changeStatus(long expectedVersion, boolean newEnabled) {
        return new SystemNavigationItem(id, applicationId, parentId, clientChannel, navigationType,
                routeKey, i18nResourceCode, i18nMessageKey, permissionCode, iconKey,
                visibleInMenu, visibleInBreadcrumb, visibleInTab, keepAlive, sortOrder,
                newEnabled, expectedVersion, createdBy, createdAt, updatedBy, updatedAt);
    }
}
