package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.I18nReference;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeApplicationCatalog;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeCatalogView;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeChannelCatalog;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeNavigationItem;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationItem;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.*;

/**
 * Application Catalog 的 Draft CRUD、全树发布、回滚和权限过滤 Runtime 用例服务。
 *
 * <p>全部写入使用 System 单 PostgreSQL 本地事务；Application Version 是全部 Navigation Draft 写入和 Publish
 * 的聚合并发边界。Runtime 只读取不可变 Release，不读取 Draft，不调用 IAM，不使用 Redis、MQ、Seata 或
 * 跨服务事务。Permission 过滤只影响目录展示，业务 API 仍由各 Resource Server 独立鉴权。</p>
 */
@Service
public class SystemCatalogApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Comparator<SystemNavigationItem> ADMIN_ORDER =
            Comparator.comparingInt(SystemNavigationItem::sortOrder)
                    .thenComparing(SystemNavigationItem::routeKey)
                    .thenComparing(SystemNavigationItem::id);

    private final SystemApplicationRepository applications;
    private final SystemNavigationRepository navigation;
    private final SystemCatalogReleaseRepository releases;
    private final SystemCatalogSnapshotCodec codec;
    private final CatalogI18nReferenceQuery i18nReferences;

    public SystemCatalogApplicationService(
            SystemApplicationRepository applications,
            SystemNavigationRepository navigation,
            SystemCatalogReleaseRepository releases,
            SystemCatalogSnapshotCodec codec,
            CatalogI18nReferenceQuery i18nReferences) {
        this.applications = Objects.requireNonNull(applications, "applications");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.i18nReferences = Objects.requireNonNull(i18nReferences, "i18nReferences");
    }

    @Transactional
    public ApplicationView createApplication(CreateApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        var value = SystemApplication.create(
                SystemCatalogRules.requireApplicationCode(command.applicationCode()),
                Objects.requireNonNull(command.applicationType(), "applicationType"),
                SystemCatalogRules.requireI18nResourceCode(command.i18nResourceCode()),
                SystemCatalogRules.requireI18nMessageKey(command.i18nMessageKey()),
                SystemCatalogRules.normalizeIconKey(command.iconKey()),
                SystemCatalogRules.normalizeDescription(command.description()),
                SystemCatalogRules.requireRouteContractVersion(command.routeContractVersion()),
                SystemCatalogRules.requireSortOrder(command.sortOrder()),
                command.enabled() == null || command.enabled());
        return ApplicationView.from(applications.insert(value));
    }

    @Transactional
    public ApplicationView updateApplication(String id, UpdateApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        long version = requireVersion(command.version());
        SystemApplication current = requireApplication(id);
        requireExpectedVersion(current, version);
        SystemApplication changed = current.update(version,
                Objects.requireNonNull(command.applicationType(), "applicationType"),
                SystemCatalogRules.requireI18nResourceCode(command.i18nResourceCode()),
                SystemCatalogRules.requireI18nMessageKey(command.i18nMessageKey()),
                SystemCatalogRules.normalizeIconKey(command.iconKey()),
                SystemCatalogRules.normalizeDescription(command.description()),
                SystemCatalogRules.requireRouteContractVersion(command.routeContractVersion()),
                SystemCatalogRules.requireSortOrder(command.sortOrder()));
        updateApplicationOrStale(changed);
        return ApplicationView.from(requireApplication(id));
    }

    @Transactional
    public ApplicationView changeApplicationStatus(String id, ApplicationStatusCommand command) {
        Objects.requireNonNull(command, "command");
        long version = requireVersion(command.version());
        boolean enabled = requireBoolean(command.enabled(), "enabled");
        SystemApplication current = requireApplication(id);
        requireExpectedVersion(current, version);
        if (!applications.updateStatus(current.changeStatus(version, enabled))) {
            throw stale();
        }
        return ApplicationView.from(requireApplication(id));
    }

    @Transactional(readOnly = true)
    public ApplicationView getApplication(String id) {
        return ApplicationView.from(requireApplication(id));
    }

    @Transactional(readOnly = true)
    public PageView<ApplicationView> pageApplications(ApplicationPageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.page(), query.size());
        String code = query.applicationCode() == null || query.applicationCode().isBlank()
                ? null : SystemCatalogRules.requireApplicationCode(query.applicationCode());
        var page = applications.findPage(new SystemApplicationRepository.ApplicationQuery(
                code, query.applicationType(), query.enabled(), query.page(), query.size()));
        return new PageView<>(page.items().stream().map(ApplicationView::from).toList(),
                page.total(), page.page(), page.size());
    }

    @Transactional
    public NavigationView createNavigation(String applicationId, CreateNavigationCommand command) {
        Objects.requireNonNull(command, "command");
        SystemApplication current = requireApplication(applicationId);
        SystemApplication touched = touch(current, requireVersion(command.applicationVersion()));
        NavigationType type = Objects.requireNonNull(command.navigationType(), "navigationType");
        SystemNavigationItem inserted = navigation.insert(SystemNavigationItem.create(
                current.id(), normalizeParent(command.parentId()),
                Objects.requireNonNull(command.clientChannel(), "clientChannel"), type,
                SystemCatalogRules.requireRouteKey(command.routeKey()),
                SystemCatalogRules.requireI18nResourceCode(command.i18nResourceCode()),
                SystemCatalogRules.requireI18nMessageKey(command.i18nMessageKey()),
                SystemCatalogRules.normalizePermissionCode(command.permissionCode()),
                SystemCatalogRules.normalizeIconKey(command.iconKey()),
                command.visibleInMenu() == null || command.visibleInMenu(),
                command.visibleInBreadcrumb() == null || command.visibleInBreadcrumb(),
                command.visibleInTab() == null || command.visibleInTab(),
                SystemCatalogRules.requireKeepAlive(type, command.keepAlive()),
                SystemCatalogRules.requireSortOrder(command.sortOrder()),
                command.enabled() == null || command.enabled()));
        validateDraft(current.id());
        return NavigationView.from(inserted, touched.version());
    }

    @Transactional
    public NavigationView updateNavigation(
            String applicationId, String navigationId, UpdateNavigationCommand command) {
        Objects.requireNonNull(command, "command");
        SystemApplication current = requireApplication(applicationId);
        SystemNavigationItem item = requireNavigation(applicationId, navigationId);
        requireNavigationVersion(item, requireVersion(command.version()));
        SystemApplication touched = touch(current, requireVersion(command.applicationVersion()));
        NavigationType type = Objects.requireNonNull(command.navigationType(), "navigationType");
        SystemNavigationItem changed = item.update(item.version(), normalizeParent(command.parentId()), type,
                SystemCatalogRules.requireI18nResourceCode(command.i18nResourceCode()),
                SystemCatalogRules.requireI18nMessageKey(command.i18nMessageKey()),
                SystemCatalogRules.normalizePermissionCode(command.permissionCode()),
                SystemCatalogRules.normalizeIconKey(command.iconKey()),
                requireBoolean(command.visibleInMenu(), "visibleInMenu"),
                requireBoolean(command.visibleInBreadcrumb(), "visibleInBreadcrumb"),
                requireBoolean(command.visibleInTab(), "visibleInTab"),
                SystemCatalogRules.requireKeepAlive(type, command.keepAlive()),
                SystemCatalogRules.requireSortOrder(command.sortOrder()));
        if (!navigation.update(changed)) {
            throw stale();
        }
        validateDraft(current.id());
        return NavigationView.from(requireNavigation(applicationId, navigationId), touched.version());
    }

    @Transactional
    public NavigationView changeNavigationStatus(
            String applicationId, String navigationId, NavigationStatusCommand command) {
        Objects.requireNonNull(command, "command");
        SystemApplication current = requireApplication(applicationId);
        SystemNavigationItem item = requireNavigation(applicationId, navigationId);
        requireNavigationVersion(item, requireVersion(command.version()));
        SystemApplication touched = touch(current, requireVersion(command.applicationVersion()));
        if (!navigation.updateStatus(item.changeStatus(item.version(), requireBoolean(command.enabled(), "enabled")))) {
            throw stale();
        }
        validateDraft(current.id());
        return NavigationView.from(requireNavigation(applicationId, navigationId), touched.version());
    }

    @Transactional
    public NavigationTreeView reorderNavigation(String applicationId, ReorderNavigationCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.items().isEmpty() || command.items().size() > SystemCatalogRules.MAX_REORDER_ITEMS) {
            throw new IllegalArgumentException("批量排序必须包含 1～200 个节点");
        }
        Set<String> ids = new HashSet<>();
        for (ReorderItem item : command.items()) {
            if (item.navigationId() == null || !ids.add(item.navigationId())) {
                throw new IllegalArgumentException("批量排序存在重复或空 navigationId");
            }
        }
        ClientChannel channel = Objects.requireNonNull(command.clientChannel(), "clientChannel");
        SystemApplication current = requireApplication(applicationId);
        SystemApplication touched = touch(current, requireVersion(command.applicationVersion()));
        List<SystemNavigationItem> values = navigation.findByIds(ids);
        if (values.size() != ids.size()) {
            throw new SystemCatalogException.NotFound("not_found", "Navigation 不存在");
        }
        Map<String, ReorderItem> input = command.items().stream().collect(
                java.util.stream.Collectors.toMap(ReorderItem::navigationId, value -> value));
        String parentId = normalizeParent(command.parentId());
        for (SystemNavigationItem value : values) {
            if (!applicationId.equals(value.applicationId())
                    || value.clientChannel() != channel
                    || !Objects.equals(value.parentId(), parentId)) {
                throw new IllegalArgumentException("批量排序节点必须属于同一 Application/Channel/Parent");
            }
            ReorderItem order = input.get(value.id());
            requireNavigationVersion(value, requireVersion(order.version()));
            SystemNavigationItem changed = value.update(value.version(), value.parentId(), value.navigationType(),
                    value.i18nResourceCode(), value.i18nMessageKey(), value.permissionCode(), value.iconKey(),
                    value.visibleInMenu(), value.visibleInBreadcrumb(), value.visibleInTab(), value.keepAlive(),
                    SystemCatalogRules.requireSortOrder(order.sortOrder()));
            if (!navigation.update(changed)) {
                throw stale();
            }
        }
        validateDraft(applicationId);
        return tree(applicationId, touched.version(), channel);
    }

    @Transactional(readOnly = true)
    public NavigationTreeView navigationTree(String applicationId, ClientChannel channel) {
        SystemApplication application = requireApplication(applicationId);
        return tree(applicationId, application.version(), Objects.requireNonNull(channel, "clientChannel"));
    }

    @Transactional
    public CatalogReleaseView publish(String applicationId, PublishCommand command) {
        Objects.requireNonNull(command, "command");
        long expectedVersion = requireVersion(command.applicationVersion());
        SystemApplication application = requireApplication(applicationId);
        requireExpectedVersion(application, expectedVersion);
        if (!application.enabled()) {
            throw new SystemCatalogException.Conflict(
                    "catalog_integrity_conflict", "禁用 Application 不允许发布");
        }
        String changeNote = requireChangeNote(command.changeNote());
        var built = SystemCatalogRules.buildSnapshot(application, navigation.findByApplication(applicationId));
        validatePublishedI18n(application.applicationCode(), built.snapshot());
        String json = encodeSnapshot(built.snapshot());
        String checksum = SystemCatalogRules.sha256(json);
        rejectNoOp(application, checksum);
        long releaseVersion = releases.nextVersion(applicationId);
        SystemCatalogRelease inserted = releases.insert(new SystemCatalogRelease(
                null, application.id(), application.applicationCode(), releaseVersion,
                SystemCatalogRules.SNAPSHOT_SCHEMA_VERSION, application.routeContractVersion(),
                application.version(), null, json, built.nodeCount(), checksum, changeNote,
                null, null, null, null));
        if (!applications.updatePublished(application.publish(expectedVersion, inserted.id(), releaseVersion))) {
            throw stale();
        }
        SystemApplication updated = requireApplication(applicationId);
        return CatalogReleaseView.from(inserted, updated.version());
    }

    @Transactional
    public CatalogReleaseView rollback(String applicationId, RollbackCommand command) {
        Objects.requireNonNull(command, "command");
        long expectedVersion = requireVersion(command.applicationVersion());
        if (command.targetReleaseVersion() == null || command.targetReleaseVersion() < 1) {
            throw new IllegalArgumentException("targetReleaseVersion 必须大于 0");
        }
        SystemApplication application = requireApplication(applicationId);
        requireExpectedVersion(application, expectedVersion);
        if (!application.enabled()) {
            throw new SystemCatalogException.Conflict(
                    "catalog_integrity_conflict", "禁用 Application 不允许回滚");
        }
        SystemCatalogRelease target = releases.findByApplicationAndVersion(
                        applicationId, command.targetReleaseVersion())
                .orElseThrow(() -> new SystemCatalogException.NotFound(
                        "not_found", "目标 Catalog Release 不存在"));
        SystemCatalogSnapshot snapshot = checkedSnapshot(target);
        validatePublishedI18n(application.applicationCode(), snapshot);
        rejectNoOp(application, target.checksum());
        long releaseVersion = releases.nextVersion(applicationId);
        SystemCatalogRelease inserted = releases.insert(new SystemCatalogRelease(
                null, application.id(), application.applicationCode(), releaseVersion,
                target.snapshotSchemaVersion(), target.routeContractVersion(), application.version(),
                target.releaseVersion(), target.snapshotJson(), target.nodeCount(), target.checksum(),
                requireChangeNote(command.changeNote()), null, null, null, null));
        if (!applications.updatePublished(application.publish(expectedVersion, inserted.id(), releaseVersion))) {
            throw stale();
        }
        return CatalogReleaseView.from(inserted, requireApplication(applicationId).version());
    }

    @Transactional(readOnly = true)
    public PageView<ReleaseHistoryView> releaseHistory(String applicationId, int page, int size) {
        requirePage(page, size);
        requireApplication(applicationId);
        var result = releases.findHistory(applicationId, page, size);
        return new PageView<>(result.items().stream().map(ReleaseHistoryView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @Transactional(readOnly = true)
    public RuntimeResult runtimeCatalog(Set<String> authorities) {
        Set<String> granted = authorities == null ? Set.of() : Set.copyOf(authorities);
        List<SystemApplication> applicationList = applications.findEnabledPublished();
        Map<String, SystemCatalogRelease> releaseById = new HashMap<>();
        releases.findByIds(applicationList.stream().map(SystemApplication::publishedReleaseId).toList())
                .forEach(value -> releaseById.put(value.id(), value));
        List<RuntimeApplicationCatalog> visible = new ArrayList<>();
        List<String> checksumParts = new ArrayList<>();
        Instant generatedAt = Instant.EPOCH;
        for (SystemApplication application : applicationList) {
            SystemCatalogRelease release = releaseById.get(application.publishedReleaseId());
            if (!isCurrentRelease(application, release)) {
                continue;
            }
            RuntimeApplicationCatalog runtime = toRuntime(release, granted);
            if (runtime.channels().isEmpty()) {
                continue;
            }
            visible.add(runtime);
            checksumParts.add(application.applicationCode() + ':' + release.checksum());
            if (release.createdAt() != null && release.createdAt().isAfter(generatedAt)) {
                generatedAt = release.createdAt();
            }
        }
        String checksum = SystemCatalogRules.sha256(String.join("|", checksumParts)
                + "|" + String.join(",", granted.stream().sorted().toList()));
        return new RuntimeResult(new RuntimeCatalogView(
                SystemCatalogRules.SNAPSHOT_SCHEMA_VERSION, generatedAt, visible), checksum);
    }

    @Transactional(readOnly = true)
    public RuntimeResult runtimeApplication(String applicationCode, Set<String> authorities) {
        String code = SystemCatalogRules.requireApplicationCode(applicationCode);
        SystemApplication application = applications.findByCode(code)
                .filter(SystemApplication::enabled)
                .filter(value -> value.publishedReleaseId() != null && value.publishedVersion() > 0)
                .orElseThrow(() -> new SystemCatalogException.NotFound(
                        "catalog_not_published", "Application Catalog 不存在或未发布"));
        SystemCatalogRelease release = releases.findById(application.publishedReleaseId())
                .filter(value -> isCurrentRelease(application, value))
                .orElseThrow(() -> new SystemCatalogException.NotFound(
                        "catalog_not_published", "Application Catalog 当前发布版本不完整"));
        Set<String> granted = authorities == null ? Set.of() : Set.copyOf(authorities);
        RuntimeApplicationCatalog runtime = toRuntime(release, granted);
        if (runtime.channels().isEmpty()) {
            throw new SystemCatalogException.NotFound("not_found", "Application Catalog 不可见");
        }
        String checksum = SystemCatalogRules.sha256(
                application.applicationCode() + ':' + release.checksum()
                        + "|" + String.join(",", granted.stream().sorted().toList()));
        return new RuntimeResult(new RuntimeCatalogView(
                release.snapshotSchemaVersion(), release.createdAt(), List.of(runtime)), checksum);
    }

    private NavigationTreeView tree(String applicationId, long applicationVersion, ClientChannel channel) {
        List<SystemNavigationItem> items = navigation.findByApplicationAndChannel(applicationId, channel);
        SystemCatalogRules.buildSnapshot(requireApplication(applicationId), items);
        Map<String, List<SystemNavigationItem>> children = new HashMap<>();
        for (SystemNavigationItem item : items) {
            children.computeIfAbsent(item.parentId(), ignored -> new ArrayList<>()).add(item);
        }
        List<NavigationNodeView> roots = children.getOrDefault(null, List.of()).stream()
                .sorted(ADMIN_ORDER)
                .map(item -> adminNode(item, applicationVersion, children))
                .toList();
        return new NavigationTreeView(applicationId, applicationVersion, channel, roots);
    }

    private NavigationNodeView adminNode(
            SystemNavigationItem item, long applicationVersion,
            Map<String, List<SystemNavigationItem>> children) {
        return new NavigationNodeView(NavigationView.from(item, applicationVersion),
                children.getOrDefault(item.id(), List.of()).stream()
                        .sorted(ADMIN_ORDER)
                        .map(child -> adminNode(child, applicationVersion, children))
                        .toList());
    }

    private void validateDraft(String applicationId) {
        SystemCatalogRules.buildSnapshot(requireApplication(applicationId), navigation.findByApplication(applicationId));
    }

    private void validatePublishedI18n(String applicationCode, SystemCatalogSnapshot snapshot) {
        Set<CatalogI18nReferenceQuery.Reference> expected = new HashSet<>();
        expected.add(new CatalogI18nReferenceQuery.Reference(
                snapshot.i18nResourceCode(), snapshot.i18nMessageKey()));
        for (SystemCatalogSnapshot.ChannelSnapshot channel : snapshot.channels()) {
            channel.navigation().forEach(node -> collectReferences(node, expected));
        }
        Set<CatalogI18nReferenceQuery.Reference> actual =
                i18nReferences.findPublished(applicationCode, expected);
        if (!actual.containsAll(expected)) {
            throw new SystemCatalogException.Conflict(
                    "invalid_i18n_reference", "Catalog 引用的 Dynamic I18n Resource/Key 未发布");
        }
    }

    private static void collectReferences(
            SystemCatalogSnapshot.NodeSnapshot node,
            Set<CatalogI18nReferenceQuery.Reference> references) {
        references.add(new CatalogI18nReferenceQuery.Reference(
                node.i18nResourceCode(), node.i18nMessageKey()));
        node.children().forEach(child -> collectReferences(child, references));
    }

    private String encodeSnapshot(SystemCatalogSnapshot snapshot) {
        String json = codec.encode(snapshot);
        if (json.getBytes(StandardCharsets.UTF_8).length > SystemCatalogRules.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Catalog Snapshot 超过 1 MiB");
        }
        return json;
    }

    private void rejectNoOp(SystemApplication application, String checksum) {
        if (application.publishedReleaseId() == null) {
            return;
        }
        releases.findById(application.publishedReleaseId())
                .filter(current -> current.checksum().equals(checksum))
                .ifPresent(current -> {
                    throw new SystemCatalogException.Conflict(
                            "no_catalog_changes", "Catalog 内容与当前发布版本相同");
                });
    }

    private RuntimeApplicationCatalog toRuntime(SystemCatalogRelease release, Set<String> authorities) {
        SystemCatalogSnapshot snapshot = checkedSnapshot(release);
        List<RuntimeChannelCatalog> channels = snapshot.channels().stream()
                .map(channel -> new RuntimeChannelCatalog(channel.clientChannel(),
                        channel.navigation().stream()
                                .map(node -> filterNode(node, authorities))
                                .filter(Objects::nonNull)
                                .toList()))
                .filter(channel -> !channel.navigation().isEmpty())
                .toList();
        return new RuntimeApplicationCatalog(snapshot.applicationCode(), snapshot.applicationType(),
                release.releaseVersion(), snapshot.routeContractVersion(),
                new I18nReference(snapshot.i18nResourceCode(), snapshot.i18nMessageKey()),
                snapshot.iconKey(), channels);
    }

    private SystemCatalogSnapshot checkedSnapshot(SystemCatalogRelease release) {
        if (!SystemCatalogRules.sha256(release.snapshotJson()).equals(release.checksum())) {
            throw new IllegalStateException("Catalog Release checksum 不一致");
        }
        SystemCatalogSnapshot snapshot = codec.decode(release.snapshotJson());
        if (snapshot.snapshotSchemaVersion() != release.snapshotSchemaVersion()
                || !snapshot.applicationCode().equals(release.applicationCode())
                || snapshot.routeContractVersion() != release.routeContractVersion()) {
            throw new IllegalStateException("Catalog Release 元数据与 Snapshot 不一致");
        }
        return snapshot;
    }

    private RuntimeNavigationItem filterNode(
            SystemCatalogSnapshot.NodeSnapshot node, Set<String> authorities) {
        if (node.permissionCode() != null && !authorities.contains(node.permissionCode())) {
            return null;
        }
        List<RuntimeNavigationItem> children = node.children().stream()
                .map(child -> filterNode(child, authorities))
                .filter(Objects::nonNull)
                .toList();
        if (node.navigationType() == NavigationType.GROUP && children.isEmpty()) {
            return null;
        }
        return new RuntimeNavigationItem(node.routeKey(), node.navigationType(), node.permissionCode(),
                new I18nReference(node.i18nResourceCode(), node.i18nMessageKey()), node.iconKey(),
                node.visibleInMenu(), node.visibleInBreadcrumb(), node.visibleInTab(),
                node.keepAlive(), children);
    }

    private static boolean isCurrentRelease(SystemApplication application, SystemCatalogRelease release) {
        return release != null
                && application.id().equals(release.applicationId())
                && application.applicationCode().equals(release.applicationCode())
                && application.publishedVersion() == release.releaseVersion();
    }

    private SystemApplication touch(SystemApplication application, long expectedVersion) {
        requireExpectedVersion(application, expectedVersion);
        if (!applications.touch(application.touch(expectedVersion))) {
            throw stale();
        }
        return requireApplication(application.id());
    }

    private void updateApplicationOrStale(SystemApplication changed) {
        if (!applications.update(changed)) {
            throw stale();
        }
    }

    private SystemApplication requireApplication(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("applicationId 不能为空");
        }
        return applications.findById(id)
                .orElseThrow(() -> new SystemCatalogException.NotFound("not_found", "Application 不存在"));
    }

    private SystemNavigationItem requireNavigation(String applicationId, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("navigationId 不能为空");
        }
        return navigation.findById(id)
                .filter(value -> applicationId.equals(value.applicationId()))
                .orElseThrow(() -> new SystemCatalogException.NotFound("not_found", "Navigation 不存在"));
    }

    private static void requireExpectedVersion(SystemApplication value, long expected) {
        if (value.version() != expected) {
            throw stale();
        }
    }

    private static void requireNavigationVersion(SystemNavigationItem value, long expected) {
        if (value.version() != expected) {
            throw stale();
        }
    }

    private static SystemCatalogException.StaleVersion stale() {
        return new SystemCatalogException.StaleVersion("Catalog 已被其他请求修改");
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 必须大于等于 0");
        }
        return version;
    }

    private static boolean requireBoolean(Boolean value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static String normalizeParent(String parentId) {
        return parentId == null || parentId.isBlank() ? null : parentId.trim();
    }

    private static String requireChangeNote(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("changeNote 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 1000 || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("changeNote 格式非法");
        }
        return normalized;
    }

    private static void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("分页参数非法");
        }
    }
}
