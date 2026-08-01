package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.application.catalog.port.CatalogReferenceValidationPort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.CatalogReleaseView;
import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.PublishCommand;
import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.RollbackCommand;

/**
 * Catalog 发布的非事务编排器。
 *
 * <p>编排器先读取候选聚合并构建指纹，再在数据库事务之外调用 IAM Permission 权威服务；全部有效后调用独立
 * Transactional Commit Service。{@code Propagation.NEVER} 防止未来调用方把 Feign 校验放入活动事务。</p>
 */
@Service
public class SystemCatalogPublishOrchestrator {
    private final SystemApplicationRepository applications;
    private final SystemNavigationRepository navigation;
    private final SystemCatalogReleaseRepository releases;
    private final SystemCatalogSnapshotCodec codec;
    private final CatalogReferenceValidationPort referenceValidation;
    private final SystemCatalogPublishCommitService commitService;

    public SystemCatalogPublishOrchestrator(
            SystemApplicationRepository applications,
            SystemNavigationRepository navigation,
            SystemCatalogReleaseRepository releases,
            SystemCatalogSnapshotCodec codec,
            CatalogReferenceValidationPort referenceValidation,
            SystemCatalogPublishCommitService commitService) {
        this.applications = Objects.requireNonNull(applications, "applications");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.referenceValidation = Objects.requireNonNull(referenceValidation, "referenceValidation");
        this.commitService = Objects.requireNonNull(commitService, "commitService");
    }

    /** 事务外校验当前 Draft，再进入单 PostgreSQL 本地提交事务。 */
    @Transactional(propagation = Propagation.NEVER)
    public CatalogReleaseView publish(String applicationId, PublishCommand command) {
        Objects.requireNonNull(command, "command");
        long expectedVersion = requireVersion(command.applicationVersion());
        SystemApplication application = requireApplication(applicationId);
        requireExpectedVersion(application, expectedVersion);
        if (!application.enabled()) {
            throw new SystemCatalogException.Conflict(
                    "catalog_integrity_conflict", "禁用 Application 不允许发布");
        }
        var built = SystemCatalogRules.buildSnapshot(
                application, navigation.findByApplication(applicationId));
        String checksum = checksum(built.snapshot());
        Set<String> permissions = collectPermissionCodes(built.snapshot());
        requireEnabled(permissions);
        return commitService.publish(applicationId, command,
                new SystemCatalogPublishPlan(expectedVersion, checksum, permissions));
    }

    /** 事务外校验目标历史版本当前 Permission，再复制形成新的单调 Release。 */
    @Transactional(propagation = Propagation.NEVER)
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
        Set<String> permissions = collectPermissionCodes(snapshot);
        requireEnabled(permissions);
        return commitService.rollback(applicationId, command,
                new SystemCatalogPublishPlan(expectedVersion, target.checksum(), permissions));
    }

    private void requireEnabled(Set<String> permissions) {
        if (permissions.isEmpty()) {
            return;
        }
        var result = referenceValidation.validate(permissions);
        Set<String> invalid = new TreeSet<>();
        permissions.forEach(code -> {
            if (result.statuses().get(code) != CatalogReferenceValidationPort.Status.ENABLED) {
                invalid.add(code);
            }
        });
        if (!invalid.isEmpty()) {
            throw new SystemCatalogException.Conflict(
                    "invalid_permission_reference",
                    "Catalog 引用了不存在或已禁用的 IAM Permission");
        }
    }

    private String checksum(SystemCatalogSnapshot snapshot) {
        String json = codec.encode(snapshot);
        if (json.getBytes(StandardCharsets.UTF_8).length > SystemCatalogRules.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Catalog Snapshot 超过 1 MiB");
        }
        return SystemCatalogRules.sha256(json);
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
            throw new SystemCatalogException.StaleVersion("Catalog 已被其他请求修改");
        }
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 必须大于等于 0");
        }
        return version;
    }
}
