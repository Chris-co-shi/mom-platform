package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;

import java.util.List;

/**
 * Publish 时生成并完整持久化的 Catalog Snapshot。
 *
 * <p>Snapshot 只包含稳定 Reference 与展示提示，不含任何可执行前端实现。列表在构造时复制为不可变结构，
 * 使相同输入可生成确定性 JSON 与 SHA-256；解码损坏时 Infrastructure 必须 Fail Closed。</p>
 */
public record SystemCatalogSnapshot(
        int snapshotSchemaVersion,
        String applicationCode,
        ApplicationType applicationType,
        int routeContractVersion,
        String i18nResourceCode,
        String i18nMessageKey,
        String iconKey,
        List<ChannelSnapshot> channels) {
    public SystemCatalogSnapshot {
        channels = channels == null ? List.of() : List.copyOf(channels);
    }

    /** 单客户端渠道的根导航集合。 */
    public record ChannelSnapshot(ClientChannel clientChannel, List<NodeSnapshot> navigation) {
        public ChannelSnapshot {
            navigation = navigation == null ? List.of() : List.copyOf(navigation);
        }
    }

    /** 不可执行的树节点 Snapshot。 */
    public record NodeSnapshot(
            String routeKey,
            NavigationType navigationType,
            String i18nResourceCode,
            String i18nMessageKey,
            String permissionCode,
            String iconKey,
            boolean visibleInMenu,
            boolean visibleInBreadcrumb,
            boolean visibleInTab,
            boolean keepAlive,
            List<NodeSnapshot> children) {
        public NodeSnapshot {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
