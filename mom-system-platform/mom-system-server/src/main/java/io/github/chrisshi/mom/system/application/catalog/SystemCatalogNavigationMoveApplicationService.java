package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationItem;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.NavigationView;

/** Navigation Parent/排序移动的独立本地事务用例。 */
@Service
public class SystemCatalogNavigationMoveApplicationService {
    private final SystemApplicationRepository applications;
    private final SystemNavigationRepository navigation;

    public SystemCatalogNavigationMoveApplicationService(
            SystemApplicationRepository applications,
            SystemNavigationRepository navigation) {
        this.applications = Objects.requireNonNull(applications, "applications");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /**
     * 使用 Application 聚合 Version 和节点 Version 原子移动节点。
     *
     * <p>目标 Parent、跨 Channel、ROUTE Parent、循环和最大深度由完整 Draft 校验统一裁决；任一失败时
     * Application Touch 与节点更新在同一事务回滚。</p>
     */
    @Transactional
    public NavigationView move(
            String applicationId,
            String navigationId,
            Long applicationVersion,
            Long version,
            String parentId,
            Integer sortOrder) {
        long expectedApplicationVersion = requireVersion(applicationVersion);
        long expectedNodeVersion = requireVersion(version);
        SystemApplication application = applications.findById(applicationId)
                .orElseThrow(() -> new SystemCatalogException.NotFound(
                        "not_found", "Application 不存在"));
        if (application.version() != expectedApplicationVersion
                || !applications.touch(application.touch(expectedApplicationVersion))) {
            throw stale();
        }
        SystemApplication touched = applications.findById(applicationId)
                .orElseThrow(() -> new IllegalStateException("Application Touch 后不存在"));
        SystemNavigationItem item = navigation.findById(navigationId)
                .filter(value -> applicationId.equals(value.applicationId()))
                .orElseThrow(() -> new SystemCatalogException.NotFound(
                        "not_found", "Navigation 不存在"));
        if (item.version() != expectedNodeVersion) {
            throw stale();
        }
        String normalizedParent = parentId == null || parentId.isBlank() ? null : parentId.trim();
        SystemNavigationItem moved = item.update(item.version(), normalizedParent, item.navigationType(),
                item.i18nResourceCode(), item.i18nMessageKey(), item.permissionCode(), item.iconKey(),
                item.visibleInMenu(), item.visibleInBreadcrumb(), item.visibleInTab(), item.keepAlive(),
                SystemCatalogRules.requireSortOrder(sortOrder));
        if (!navigation.update(moved)) {
            throw stale();
        }
        SystemCatalogRules.buildSnapshot(application, navigation.findByApplication(applicationId));
        return NavigationView.from(navigation.findById(navigationId)
                .orElseThrow(() -> new IllegalStateException("Navigation 移动后不存在")), touched.version());
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 必须大于等于 0");
        }
        return version;
    }

    private static SystemCatalogException.StaleVersion stale() {
        return new SystemCatalogException.StaleVersion("Catalog 已被其他请求修改");
    }
}
