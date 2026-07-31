package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Application Catalog 的纯领域校验、树完整性与确定性 Snapshot 规则。
 *
 * <p>规则不依赖 Spring、MyBatis、Jackson、SecurityContext 或网络。最大深度固定为 4，每个 Application/
 * Channel 最多 500 个 Draft 节点；循环、孤儿、跨 Channel Parent、ROUTE 子节点和非法 Reference 均
 * Fail Closed。相同规范输入按稳定顺序生成相同树，供受控 Codec 生成确定性 JSON 与 SHA-256。</p>
 */
public final class SystemCatalogRules {
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int MAX_TREE_DEPTH = 4;
    public static final int MAX_NODES_PER_CHANNEL = 500;
    public static final int MAX_REORDER_ITEMS = 200;
    public static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;

    private static final Pattern APPLICATION_CODE =
            Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
    private static final Pattern ROUTE_KEY =
            Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$");
    private static final Pattern I18N_RESOURCE = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");
    private static final Pattern I18N_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$");
    private static final Pattern PERMISSION = Pattern.compile(
            "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    private static final Comparator<SystemNavigationItem> NODE_ORDER =
            Comparator.comparingInt(SystemNavigationItem::sortOrder)
                    .thenComparing(SystemNavigationItem::routeKey)
                    .thenComparing(SystemNavigationItem::id,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private SystemCatalogRules() {
    }

    /** 校验稳定小写 kebab-case Application Code。 */
    public static String requireApplicationCode(String value) {
        String normalized = requireText(value, "applicationCode", 64).toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || !APPLICATION_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("applicationCode 格式非法");
        }
        return normalized;
    }

    /** 校验客户端静态 Registry 使用的稳定 routeKey。 */
    public static String requireRouteKey(String value) {
        String normalized = requireText(value, "routeKey", 128).toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || !ROUTE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("routeKey 格式非法");
        }
        return normalized;
    }

    /** 校验 Dynamic I18n 资源 Code。 */
    public static String requireI18nResourceCode(String value) {
        String normalized = requireText(value, "i18nResourceCode", 64).toLowerCase(Locale.ROOT);
        if (!I18N_RESOURCE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("i18nResourceCode 格式非法");
        }
        return normalized;
    }

    /** 校验 Dynamic I18n 消息 Key。 */
    public static String requireI18nMessageKey(String value) {
        String normalized = requireText(value, "i18nMessageKey", 128);
        if (!I18N_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("i18nMessageKey 格式非法");
        }
        return normalized;
    }

    /** 校验 IAM Permission Code Reference；空值表示仅认证可见。 */
    public static String normalizePermissionCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = requireText(value, "permissionCode", 160).toLowerCase(Locale.ROOT);
        if (!PERMISSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("permissionCode 格式非法");
        }
        return normalized;
    }

    /** 校验静态 Icon Registry Key，不允许 URL、脚本或控制字符。 */
    public static String normalizeIconKey(String value) {
        String normalized = normalizeOptionalText(value, "iconKey", 128);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("<script") || lower.contains("javascript:")
                || lower.contains("://") || lower.startsWith("data:")) {
            throw new IllegalArgumentException("iconKey 不允许 URL 或可执行内容");
        }
        return normalized;
    }

    /** 规范可选管理说明。 */
    public static String normalizeDescription(String value) {
        return normalizeOptionalText(value, "description", 1000);
    }

    /** 校验正整数 Route Contract Version。 */
    public static int requireRouteContractVersion(Integer value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("routeContractVersion 必须大于 0");
        }
        return value;
    }

    /** 校验非负排序。 */
    public static int requireSortOrder(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("sortOrder 必须大于等于 0");
        }
        return value;
    }

    /** GROUP 不允许 keepAlive。 */
    public static boolean requireKeepAlive(NavigationType type, Boolean value) {
        boolean keepAlive = Boolean.TRUE.equals(value);
        if (type == NavigationType.GROUP && keepAlive) {
            throw new IllegalArgumentException("GROUP 节点不允许 keepAlive");
        }
        return keepAlive;
    }

    /**
     * 校验完整 Draft，并只把 enabled 且祖先 enabled 的节点构建成发布 Snapshot。
     *
     * @return Snapshot 与发布节点数量
     */
    public static SnapshotBuild buildSnapshot(
            SystemApplication application, List<SystemNavigationItem> allItems) {
        Map<String, SystemNavigationItem> byId = new HashMap<>();
        Map<ClientChannel, List<SystemNavigationItem>> byChannel = new EnumMap<>(ClientChannel.class);
        for (ClientChannel channel : ClientChannel.values()) {
            byChannel.put(channel, new ArrayList<>());
        }
        for (SystemNavigationItem item : allItems) {
            if (!application.id().equals(item.applicationId())) {
                throw new IllegalArgumentException("Navigation 不属于目标 Application");
            }
            if (byId.put(item.id(), item) != null) {
                throw new IllegalStateException("Navigation 持久化 ID 重复");
            }
            byChannel.get(item.clientChannel()).add(item);
        }

        List<SystemCatalogSnapshot.ChannelSnapshot> channels = new ArrayList<>();
        int nodeCount = 0;
        for (ClientChannel channel : ClientChannel.values()) {
            List<SystemNavigationItem> items = byChannel.get(channel);
            if (items.size() > MAX_NODES_PER_CHANNEL) {
                throw new IllegalArgumentException("单 Application/Channel 节点最多 500 个");
            }
            validateChannel(items, byId, channel);
            List<SystemCatalogSnapshot.NodeSnapshot> roots = buildEnabledRoots(items, channel);
            if (!roots.isEmpty()) {
                channels.add(new SystemCatalogSnapshot.ChannelSnapshot(channel, roots));
                nodeCount += countNodes(roots);
            }
        }
        return new SnapshotBuild(new SystemCatalogSnapshot(
                SNAPSHOT_SCHEMA_VERSION,
                application.applicationCode(),
                application.applicationType(),
                application.routeContractVersion(),
                application.i18nResourceCode(),
                application.i18nMessageKey(),
                application.iconKey(),
                channels), nodeCount);
    }

    /** 计算确定性 Snapshot JSON 的 SHA-256。 */
    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private static void validateChannel(
            List<SystemNavigationItem> items,
            Map<String, SystemNavigationItem> byId,
            ClientChannel channel) {
        Map<String, List<SystemNavigationItem>> children = childrenMap(items);
        for (SystemNavigationItem item : items) {
            if (item.parentId() != null) {
                SystemNavigationItem parent = byId.get(item.parentId());
                if (parent == null) {
                    throw new IllegalArgumentException("Navigation Parent 不存在");
                }
                if (parent.clientChannel() != channel) {
                    throw new IllegalArgumentException("Navigation Parent 不允许跨 Channel");
                }
                if (parent.navigationType() != NavigationType.GROUP) {
                    throw new IllegalArgumentException("ROUTE 节点不允许拥有子节点");
                }
            }
            if (item.navigationType() == NavigationType.ROUTE
                    && !children.getOrDefault(item.id(), List.of()).isEmpty()) {
                throw new IllegalArgumentException("ROUTE 节点不允许拥有子节点");
            }
            validateDepthAndCycle(item, byId);
        }
    }

    private static void validateDepthAndCycle(
            SystemNavigationItem item, Map<String, SystemNavigationItem> byId) {
        Set<String> visited = new HashSet<>();
        int depth = 1;
        SystemNavigationItem current = item;
        while (current.parentId() != null) {
            if (!visited.add(current.id())) {
                throw new IllegalArgumentException("Navigation Tree 存在循环");
            }
            current = byId.get(current.parentId());
            if (current == null) {
                throw new IllegalArgumentException("Navigation Parent 不存在");
            }
            depth++;
            if (depth > MAX_TREE_DEPTH) {
                throw new IllegalArgumentException("Navigation Tree 最大深度为 4");
            }
        }
        if (!visited.add(current.id())) {
            throw new IllegalArgumentException("Navigation Tree 存在循环");
        }
    }

    private static List<SystemCatalogSnapshot.NodeSnapshot> buildEnabledRoots(
            List<SystemNavigationItem> items, ClientChannel channel) {
        Map<String, List<SystemNavigationItem>> children = childrenMap(items);
        return items.stream()
                .filter(item -> item.clientChannel() == channel)
                .filter(item -> item.parentId() == null)
                .sorted(NODE_ORDER)
                .map(item -> buildEnabledNode(item, children))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static SystemCatalogSnapshot.NodeSnapshot buildEnabledNode(
            SystemNavigationItem item,
            Map<String, List<SystemNavigationItem>> children) {
        if (!item.enabled()) {
            return null;
        }
        List<SystemCatalogSnapshot.NodeSnapshot> childSnapshots = children
                .getOrDefault(item.id(), List.of()).stream()
                .sorted(NODE_ORDER)
                .map(child -> buildEnabledNode(child, children))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (item.navigationType() == NavigationType.GROUP && childSnapshots.isEmpty()) {
            return null;
        }
        return new SystemCatalogSnapshot.NodeSnapshot(
                item.routeKey(), item.navigationType(), item.i18nResourceCode(), item.i18nMessageKey(),
                item.permissionCode(), item.iconKey(), item.visibleInMenu(), item.visibleInBreadcrumb(),
                item.visibleInTab(), item.keepAlive(), childSnapshots);
    }

    private static Map<String, List<SystemNavigationItem>> childrenMap(List<SystemNavigationItem> items) {
        Map<String, List<SystemNavigationItem>> children = new HashMap<>();
        for (SystemNavigationItem item : items) {
            if (item.parentId() != null) {
                children.computeIfAbsent(item.parentId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        return children;
    }

    private static int countNodes(List<SystemCatalogSnapshot.NodeSnapshot> roots) {
        int count = 0;
        ArrayDeque<SystemCatalogSnapshot.NodeSnapshot> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            SystemCatalogSnapshot.NodeSnapshot node = queue.removeFirst();
            count++;
            queue.addAll(node.children());
        }
        return count;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " 格式非法");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    /** Snapshot 与发布节点数量。 */
    public record SnapshotBuild(SystemCatalogSnapshot snapshot, int nodeCount) {
    }
}
