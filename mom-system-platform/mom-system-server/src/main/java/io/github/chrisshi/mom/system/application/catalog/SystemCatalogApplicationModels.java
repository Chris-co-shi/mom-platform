package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeCatalogView;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationItem;

import java.time.Instant;
import java.util.List;

/** Application Catalog 的 Command、Query、管理 View 与 Runtime Result。 */
public final class SystemCatalogApplicationModels {
    private SystemCatalogApplicationModels() {
    }

    public record CreateApplicationCommand(
            String applicationCode, ApplicationType applicationType,
            String i18nResourceCode, String i18nMessageKey, String iconKey, String description,
            Integer routeContractVersion, Integer sortOrder, Boolean enabled) {
    }

    public record UpdateApplicationCommand(
            ApplicationType applicationType, String i18nResourceCode, String i18nMessageKey,
            String iconKey, String description, Integer routeContractVersion,
            Integer sortOrder, Long version) {
    }

    public record ApplicationStatusCommand(Boolean enabled, Long version) {
    }

    public record ApplicationPageQuery(
            String applicationCode, ApplicationType applicationType, Boolean enabled, int page, int size) {
    }

    public record CreateNavigationCommand(
            Long applicationVersion, String parentId, ClientChannel clientChannel,
            NavigationType navigationType, String routeKey,
            String i18nResourceCode, String i18nMessageKey, String permissionCode, String iconKey,
            Boolean visibleInMenu, Boolean visibleInBreadcrumb, Boolean visibleInTab,
            Boolean keepAlive, Integer sortOrder, Boolean enabled) {
    }

    public record UpdateNavigationCommand(
            Long applicationVersion, Long version, String parentId,
            NavigationType navigationType, String i18nResourceCode, String i18nMessageKey,
            String permissionCode, String iconKey, Boolean visibleInMenu,
            Boolean visibleInBreadcrumb, Boolean visibleInTab, Boolean keepAlive, Integer sortOrder) {
    }

    public record NavigationStatusCommand(Long applicationVersion, Long version, Boolean enabled) {
    }

    public record ReorderNavigationCommand(
            Long applicationVersion, ClientChannel clientChannel, String parentId,
            List<ReorderItem> items) {
        public ReorderNavigationCommand {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record ReorderItem(String navigationId, Long version, Integer sortOrder) {
    }

    public record PublishCommand(Long applicationVersion, String changeNote) {
    }

    public record RollbackCommand(Long targetReleaseVersion, Long applicationVersion, String changeNote) {
    }

    public record ApplicationView(
            String id, String applicationCode, ApplicationType applicationType,
            String i18nResourceCode, String i18nMessageKey, String iconKey, String description,
            int routeContractVersion, int sortOrder, boolean enabled,
            long publishedVersion, long version,
            String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        static ApplicationView from(SystemApplication value) {
            return new ApplicationView(value.id(), value.applicationCode(), value.applicationType(),
                    value.i18nResourceCode(), value.i18nMessageKey(), value.iconKey(), value.description(),
                    value.routeContractVersion(), value.sortOrder(), value.enabled(), value.publishedVersion(),
                    value.version(), value.createdBy(), value.createdAt(), value.updatedBy(), value.updatedAt());
        }
    }

    public record NavigationView(
            String id, String applicationId, long applicationVersion, String parentId,
            ClientChannel clientChannel, NavigationType navigationType, String routeKey,
            String i18nResourceCode, String i18nMessageKey, String permissionCode, String iconKey,
            boolean visibleInMenu, boolean visibleInBreadcrumb, boolean visibleInTab,
            boolean keepAlive, int sortOrder, boolean enabled, long version,
            String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        static NavigationView from(SystemNavigationItem value, long applicationVersion) {
            return new NavigationView(value.id(), value.applicationId(), applicationVersion, value.parentId(),
                    value.clientChannel(), value.navigationType(), value.routeKey(), value.i18nResourceCode(),
                    value.i18nMessageKey(), value.permissionCode(), value.iconKey(), value.visibleInMenu(),
                    value.visibleInBreadcrumb(), value.visibleInTab(), value.keepAlive(), value.sortOrder(),
                    value.enabled(), value.version(), value.createdBy(), value.createdAt(), value.updatedBy(),
                    value.updatedAt());
        }
    }

    public record NavigationTreeView(
            String applicationId, long applicationVersion, ClientChannel clientChannel,
            List<NavigationNodeView> navigation) {
        public NavigationTreeView {
            navigation = navigation == null ? List.of() : List.copyOf(navigation);
        }
    }

    public record NavigationNodeView(
            NavigationView item, List<NavigationNodeView> children) {
        public NavigationNodeView {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record CatalogReleaseView(
            String applicationCode, long releaseVersion, int snapshotSchemaVersion,
            int routeContractVersion, String checksum, int nodeCount,
            Long sourceReleaseVersion, long applicationVersion, Instant publishedAt) {
        static CatalogReleaseView from(SystemCatalogRelease value, long applicationVersion) {
            return new CatalogReleaseView(value.applicationCode(), value.releaseVersion(),
                    value.snapshotSchemaVersion(), value.routeContractVersion(), value.checksum(),
                    value.nodeCount(), value.sourceReleaseVersion(), applicationVersion, value.createdAt());
        }
    }

    public record ReleaseHistoryView(
            long releaseVersion, int routeContractVersion, String checksum, int nodeCount,
            Long sourceReleaseVersion, String changeNote, String publishedBy, Instant publishedAt) {
        static ReleaseHistoryView from(SystemCatalogRelease value) {
            return new ReleaseHistoryView(value.releaseVersion(), value.routeContractVersion(),
                    value.checksum(), value.nodeCount(), value.sourceReleaseVersion(), value.changeNote(),
                    value.createdBy(), value.createdAt());
        }
    }

    public record PageView<T>(List<T> items, long total, int page, int size) {
        public PageView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** ETag 与 Runtime Body。 */
    public record RuntimeResult(RuntimeCatalogView view, String checksum) {
    }
}
