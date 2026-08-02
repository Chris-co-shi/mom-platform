package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.CatalogReleaseView;
import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.PublishCommand;
import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.RollbackCommand;

/**
 * Catalog Publish/Rollback 的唯一 PostgreSQL 本地事务提交服务。
 *
 * <p>该服务不调用 Feign、Redis、RocketMQ 或其他网络资源。事务内只重新构建并验证聚合，追加不可变 Release，
 * 追加同事务 Outbox，并 CAS 推进 Application 发布指针。任一步失败时三者整体回滚。</p>
 */
@Service
public class SystemCatalogPublishCommitService {
    private final SystemApplicationRepository applications;
    private final SystemNavigationRepository navigation;
    private final SystemCatalogReleaseRepository releases;
    private final SystemCatalogSnapshotCodec codec;
    private final CatalogI18nReferenceQuery i18nReferences;
    private final SystemRuntimeChangeEventPort events;

    public SystemCatalogPublishCommitService(
            SystemApplicationRepository applications,
            SystemNavigationRepository navigation,
            SystemCatalogReleaseRepository releases,
            SystemCatalogSnapshotCodec codec,
            CatalogI18nReferenceQuery i18nReferences,
            SystemRuntimeChangeEventPort events) {
        this.applications = Objects.requireNonNull(applications, "applications");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.i18nReferences = Objects.requireNonNull(i18nReferences, "i18nReferences");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * 使用事务外已校验计划原子发布当前 Draft。
     *
     * @return 新 Release 与提交后的 Application Version
     */
    @Transactional
    public CatalogReleaseView publish(
            String applicationId,
            PublishCommand command,
            SystemCatalogPublishPlan plan) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(plan, "plan");
        long expectedVersion = requireVersion(command.applicationVersion());
        requirePlanVersion(plan, expectedVersion);
        SystemApplication application = requireApplication(applicationId);
        requireExpectedVersion(application, expectedVersion);
        if (!application.enabled()) {
            throw new SystemCatalogException.Conflict(
                    "catalog_integrity_conflict", "禁用 Application 不允许发布");
        }
        String changeNote = requireChangeNote(command.changeNote());
        var built = SystemCatalogRules.buildSnapshot(
                application, navigation.findByApplication(applicationId));
        String json = encodeSnapshot(built.snapshot());
        String checksum = SystemCatalogRules.sha256(json);
        Set<String> permissions = collectPermissionCodes(built.snapshot());
        requireSameCandidate(plan, checksum, permissions);
        validatePublishedI18n(application.applicationCode(), built.snapshot());
        rejectNoOp(application, checksum);

        long releaseVersion = releases.nextVersion(applicationId);
        SystemCatalogRelease inserted = releases.insert(new SystemCatalogRelease(
                null, application.id(), application.applicationCode(), releaseVersion,
                SystemCatalogRules.SNAPSHOT_SCHEMA_VERSION, application.routeContractVersion(),
                application.version(), null, json, built.nodeCount(), checksum, changeNote,
                null, null, null, null));
        events.catalogPublished(new SystemRuntimeChangeEventPort.CatalogPublishedEvent(
                application.id(), application.applicationCode(), inserted.releaseVersion(),
                inserted.routeContractVersion(), inserted.checksum(), null));
        if (!applications.updatePublished(application.publish(
                expectedVersion, inserted.id(), releaseVersion))) {
            throw stale();
        }
        return CatalogReleaseView.from(inserted, requireApplication(applicationId).version());
    }

    /**
     * 验证目标历史 Snapshot 当前 Permission/I18n 后复制为新单调版本。
     */
    @Transactional
    public CatalogReleaseView rollback(
            String applicationId,
            RollbackCommand command,
            SystemCatalogPublishPlan plan) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(plan, "plan");
        long expectedVersion = requireVersion(command.applicationVersion());
        requirePlanVersion(plan, expectedVersion);
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
        requireSameCandidate(plan, target.checksum(), collectPermissionCodes(snapshot));
        validatePublishedI18n(application.applicationCode(), snapshot);
        rejectNoOp(application, target.checksum());

        long releaseVersion = releases.nextVersion(applicationId);
        SystemCatalogRelease inserted = releases.insert(new SystemCatalogRelease(
                null, application.id(), application.applicationCode(), releaseVersion,
                target.snapshotSchemaVersion(), target.routeContractVersion(), application.version(),
                target.releaseVersion(), target.snapshotJson(), target.nodeCount(), target.checksum(),
                requireChangeNote(command.changeNote()), null, null, null, null));
        events.catalogPublished(new SystemRuntimeChangeEventPort.CatalogPublishedEvent(
                application.id(), application.applicationCode(), inserted.releaseVersion(),
                inserted.routeContractVersion(), inserted.checksum(), target.releaseVersion()));
        if (!applications.updatePublished(application.publish(
                expectedVersion, inserted.id(), releaseVersion))) {
            throw stale();
        }
        return CatalogReleaseView.from(inserted, requireApplication(applicationId).version());
    }

    private void validatePublishedI18n(String applicationCode, SystemCatalogSnapshot snapshot) {
        Set<CatalogI18nReferenceQuery.Reference> expected = new HashSet<>();
        expected.add(new CatalogI18nReferenceQuery.Reference(
                snapshot.i18nResourceCode(), snapshot.i18nMessageKey()));
        snapshot.channels().forEach(channel -> channel.navigation()
                .forEach(node -> collectReferences(node, expected)));
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

    private static Set<String> collectPermissionCodes(SystemCatalogSnapshot snapshot) {
        Set<String> result = new TreeSet<>();
        snapshot.channels().forEach(channel -> channel.navigation()
                .forEach(node -> collectPermissionCodes(node, result)));
        return Set.copyOf(result);
    }

    private static void collectPermissionCodes(
            SystemCatalogSnapshot.NodeSnapshot node,
            Set<String> result) {
        if (node.permissionCode() != null) {
            result.add(node.permissionCode());
        }
        node.children().forEach(child -> collectPermissionCodes(child, result));
    }

    private String encodeSnapshot(SystemCatalogSnapshot snapshot) {
        String json = codec.encode(snapshot);
        if (json.getBytes(StandardCharsets.UTF_8).length > SystemCatalogRules.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Catalog Snapshot 超过 1 MiB");
        }
        return json;
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

    private static void requireSameCandidate(
            SystemCatalogPublishPlan plan,
            String checksum,
            Set<String> permissionCodes) {
        if (!plan.checksum().equals(checksum)
                || !plan.permissionCodes().equals(permissionCodes)) {
            throw stale();
        }
    }

    private static void requirePlanVersion(SystemCatalogPublishPlan plan, long expectedVersion) {
        if (plan.applicationVersion() != expectedVersion) {
            throw stale();
        }
    }

    private SystemApplication requireApplication(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("applicationId 不能为空");
        }
        return applications.findById(id)
                .orElseThrow(() -> new SystemCatalogException.NotFound(
                        "not_found", "Application 不存在"));
    }

    private static void requireExpectedVersion(SystemApplication value, long expected) {
        if (value.version() != expected) {
            throw stale();
        }
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 必须大于等于 0");
        }
        return version;
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

    private static SystemCatalogException.StaleVersion stale() {
        return new SystemCatalogException.StaleVersion("Catalog 已被其他请求修改");
    }
}
