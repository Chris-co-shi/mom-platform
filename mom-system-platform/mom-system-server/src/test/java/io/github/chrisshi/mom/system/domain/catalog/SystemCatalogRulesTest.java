package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catalog Code、Tree、循环、深度、状态与发布 Snapshot 的纯领域测试。 */
class SystemCatalogRulesTest {
    @Test
    void codesAndExecutableContentMustBeStrictlyBounded() {
        assertThat(SystemCatalogRules.requireApplicationCode("IAM-Admin")).isEqualTo("iam-admin");
        assertThat(SystemCatalogRules.requireRouteKey("IAM.Users")).isEqualTo("iam.users");
        assertThat(SystemCatalogRules.normalizePermissionCode("IAM:User:Read"))
                .isEqualTo("iam:user:read");
        assertThatThrownBy(() -> SystemCatalogRules.requireApplicationCode("IAM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemCatalogRules.normalizeIconKey("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemCatalogRules.requireKeepAlive(NavigationType.GROUP, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotMustPreserveChannelAndStableOrderingWhileRemovingDisabledNodes() {
        SystemNavigationItem group = node("1", null, ClientChannel.WEB,
                NavigationType.GROUP, "iam.management", null, 20, true);
        SystemNavigationItem users = node("2", "1", ClientChannel.WEB,
                NavigationType.ROUTE, "iam.users", "iam:user:read", 20, true);
        SystemNavigationItem roles = node("3", "1", ClientChannel.WEB,
                NavigationType.ROUTE, "iam.roles", "iam:role:read", 10, true);
        SystemNavigationItem disabled = node("4", null, ClientChannel.MOBILE,
                NavigationType.ROUTE, "iam.mobile", null, 10, false);
        var built = SystemCatalogRules.buildSnapshot(
                application(), List.of(users, disabled, group, roles));
        assertThat(built.nodeCount()).isEqualTo(3);
        assertThat(built.snapshot().channels()).hasSize(1);
        assertThat(built.snapshot().channels().getFirst().clientChannel()).isEqualTo(ClientChannel.WEB);
        assertThat(built.snapshot().channels().getFirst().navigation().getFirst().children())
                .extracting(SystemCatalogSnapshot.NodeSnapshot::routeKey)
                .containsExactly("iam.roles", "iam.users");
    }

    @Test
    void orphanCycleCrossChannelAndDepthOverflowMustFailClosed() {
        assertThatThrownBy(() -> SystemCatalogRules.buildSnapshot(application(), List.of(
                node("1", "missing", ClientChannel.WEB, NavigationType.ROUTE,
                        "iam.users", null, 10, true))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Parent");
        assertThatThrownBy(() -> SystemCatalogRules.buildSnapshot(application(), List.of(
                node("1", "2", ClientChannel.WEB, NavigationType.GROUP,
                        "iam.one", null, 10, true),
                node("2", "1", ClientChannel.WEB, NavigationType.GROUP,
                        "iam.two", null, 20, true))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("循环");
        assertThatThrownBy(() -> SystemCatalogRules.buildSnapshot(application(), List.of(
                node("1", null, ClientChannel.WEB, NavigationType.GROUP,
                        "iam.root", null, 10, true),
                node("2", "1", ClientChannel.MOBILE, NavigationType.ROUTE,
                        "iam.mobile", null, 10, true))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("跨 Channel");
        assertThatThrownBy(() -> SystemCatalogRules.buildSnapshot(application(), List.of(
                node("1", null, ClientChannel.WEB, NavigationType.GROUP, "a.root", null, 1, true),
                node("2", "1", ClientChannel.WEB, NavigationType.GROUP, "a.two", null, 2, true),
                node("3", "2", ClientChannel.WEB, NavigationType.GROUP, "a.three", null, 3, true),
                node("4", "3", ClientChannel.WEB, NavigationType.GROUP, "a.four", null, 4, true),
                node("5", "4", ClientChannel.WEB, NavigationType.ROUTE, "a.five", null, 5, true))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最大深度");
    }

    private static SystemApplication application() {
        return new SystemApplication("app", "iam", ApplicationType.PLATFORM,
                "mom-web", "mom.menu.iam", "lucide:shield", null,
                1, 10, true, null, 0, 0, null, null, null, null);
    }

    private static SystemNavigationItem node(
            String id, String parentId, ClientChannel channel, NavigationType type,
            String routeKey, String permission, int sortOrder, boolean enabled) {
        return new SystemNavigationItem(id, "app", parentId, channel, type, routeKey,
                "mom-web", "mom.menu." + routeKey.replace('.', '-'), permission,
                null, true, true, true, false, sortOrder, enabled, 0,
                null, null, null, null);
    }
}
