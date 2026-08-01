package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.I18nReference;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeApplicationCatalog;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeCatalogView;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeChannelCatalog;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeNavigationItem;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.RuntimeResult;

/**
 * Catalog 已发布 Runtime 的 PostgreSQL 权威读取与 Redis Projection Cache 用例。
 *
 * <p>本 Query Service 不开启数据库事务：先用短查询读取 PostgreSQL Application 启用状态、发布指针和 Release，
 * 再访问 Redis，避免持有数据库连接等待网络。Redis Miss/故障/损坏时使用已读取的权威 Release JSON；数据库
 * 故障发生在访问 Cache 之前，因此不会返回 stale Cache。</p>
 */
@Service
public class SystemCatalogRuntimeApplicationService {
    private final SystemApplicationRepository applications;
    private final SystemCatalogReleaseRepository releases;
    private final SystemCatalogSnapshotCodec codec;
    private final SystemRuntimeCachePort cache;

    public SystemCatalogRuntimeApplicationService(
            SystemApplicationRepository applications,
            SystemCatalogReleaseRepository releases,
            SystemCatalogSnapshotCodec codec,
            SystemRuntimeCachePort cache) {
        this.applications = Objects.requireNonNull(applications, "applications");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /** 返回当前用户全部可见 Application Catalog。 */
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

    /** 返回单个 Application 的权限过滤发布目录。 */
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
        var cached = cache.findCatalog(
                        release.applicationCode(), release.releaseVersion(), release.checksum())
                .filter(snapshot -> validSnapshot(release, snapshot))
                .orElse(null);
        if (cached != null) {
            return cached;
        }
        if (!SystemCatalogRules.sha256(release.snapshotJson()).equals(release.checksum())) {
            throw new IllegalStateException("Catalog Release checksum 不一致");
        }
        SystemCatalogSnapshot snapshot = codec.decode(release.snapshotJson());
        if (!validSnapshot(release, snapshot)) {
            throw new IllegalStateException("Catalog Release 元数据与 Snapshot 不一致");
        }
        cache.putCatalog(release.applicationCode(), release.releaseVersion(), release.checksum(), snapshot);
        return snapshot;
    }

    private boolean validSnapshot(SystemCatalogRelease release, SystemCatalogSnapshot snapshot) {
        return snapshot != null
                && snapshot.snapshotSchemaVersion() == release.snapshotSchemaVersion()
                && snapshot.applicationCode().equals(release.applicationCode())
                && snapshot.routeContractVersion() == release.routeContractVersion()
                && SystemCatalogRules.sha256(codec.encode(snapshot)).equals(release.checksum());
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
}
