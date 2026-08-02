package io.github.chrisshi.mom.system.api;

import java.time.Instant;
import java.util.List;

/**
 * System Application Catalog 的稳定只读跨模块契约。
 *
 * <p>契约只暴露不可执行的目录元数据：applicationCode、routeKey、I18n Reference、Permission
 * Reference 和展示提示。Path、Component、Layout、JavaScript、HTML、数据库 ID 与审计字段均不会进入该
 * 契约；客户端必须通过自身静态 Route Registry 将 routeKey 映射到可执行实现。</p>
 */
public final class SystemCatalogContracts {
    private SystemCatalogContracts() {
    }

    /** V1 Application 产品分类，不表达 OAuth Client。 */
    public enum ApplicationType {
        PLATFORM,
        BUSINESS
    }

    /** V1 客户端执行渠道。 */
    public enum ClientChannel {
        WEB,
        MOBILE
    }

    /** V1 导航节点类型。 */
    public enum NavigationType {
        GROUP,
        ROUTE
    }

    /** Dynamic I18n 的稳定资源与消息 Key 引用。 */
    public record I18nReference(String resourceCode, String messageKey) {
    }

    /** 权限过滤后的不可执行导航节点。 */
    public record RuntimeNavigationItem(
            String routeKey,
            NavigationType type,
            String permissionCode,
            I18nReference i18n,
            String iconKey,
            boolean visibleInMenu,
            boolean visibleInBreadcrumb,
            boolean visibleInTab,
            boolean keepAlive,
            List<RuntimeNavigationItem> children) {
        public RuntimeNavigationItem {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /** 单客户端渠道的权限过滤目录。 */
    public record RuntimeChannelCatalog(
            ClientChannel clientChannel,
            List<RuntimeNavigationItem> navigation) {
        public RuntimeChannelCatalog {
            navigation = navigation == null ? List.of() : List.copyOf(navigation);
        }
    }

    /** 单个 Application 的已发布运行时目录。 */
    public record RuntimeApplicationCatalog(
            String applicationCode,
            ApplicationType applicationType,
            long catalogVersion,
            int routeContractVersion,
            I18nReference i18n,
            String iconKey,
            List<RuntimeChannelCatalog> channels) {
        public RuntimeApplicationCatalog {
            channels = channels == null ? List.of() : List.copyOf(channels);
        }
    }

    /** 当前用户全部可见 Application Catalog。 */
    public record RuntimeCatalogView(
            int snapshotSchemaVersion,
            Instant generatedAt,
            List<RuntimeApplicationCatalog> applications) {
        public RuntimeCatalogView {
            applications = applications == null ? List.of() : List.copyOf(applications);
        }
    }
}
