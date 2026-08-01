package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.application.catalog.port.CatalogReferenceValidationPort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 当前已发布 Catalog 稳定 Reference 的只读权威对账服务。
 *
 * <p>对账不修改 Catalog、不自动取消发布，也不复制 IAM 权威状态。方法强制在数据库事务之外执行，读取当前
 * 发布指针和不可变 Snapshot 后，按最多 1000 个 Code 分批调用 IAM；UNKNOWN/DISABLED 只产生低基数指标与
 * 脱敏告警。Runtime 仍由 JWT Authority 精确匹配保持 Fail Closed。</p>
 */
@Service
public class SystemCatalogReferenceReconciliationService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SystemCatalogReferenceReconciliationService.class);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final String METRIC =
            "mom.system.catalog.permission_reconciliation.results";

    private final SystemApplicationRepository applications;
    private final SystemCatalogReleaseRepository releases;
    private final SystemCatalogSnapshotCodec codec;
    private final CatalogReferenceValidationPort references;
    private final MeterRegistry meterRegistry;

    public SystemCatalogReferenceReconciliationService(
            SystemApplicationRepository applications,
            SystemCatalogReleaseRepository releases,
            SystemCatalogSnapshotCodec codec,
            CatalogReferenceValidationPort references,
            MeterRegistry meterRegistry) {
        this.applications = Objects.requireNonNull(applications, "applications");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.references = Objects.requireNonNull(references, "references");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    /** 执行一次完整只读对账。 */
    @Transactional(propagation = Propagation.NEVER)
    public ReconciliationResult reconcile() {
        List<SystemApplication> published = applications.findEnabledPublished();
        if (published.isEmpty()) {
            record(0, 0, 0);
            return new ReconciliationResult(0, 0, 0, 0, 0);
        }

        Map<String, SystemCatalogRelease> releaseById = releases.findByIds(
                        published.stream().map(SystemApplication::publishedReleaseId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        SystemCatalogRelease::id,
                        release -> release,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Set<String> codes = new TreeSet<>();
        for (SystemApplication application : published) {
            SystemCatalogRelease release = releaseById.get(application.publishedReleaseId());
            if (release == null
                    || !application.id().equals(release.applicationId())
                    || !application.applicationCode().equals(release.applicationCode())
                    || application.publishedVersion() != release.releaseVersion()) {
                throw new IllegalStateException("当前 Catalog 发布指针与 Release 不一致");
            }
            SystemCatalogSnapshot snapshot = checkedSnapshot(release);
            collect(snapshot, codes);
        }

        int enabled = 0;
        int disabled = 0;
        int unknown = 0;
        List<String> ordered = List.copyOf(codes);
        for (int from = 0; from < ordered.size(); from += MAX_BATCH_SIZE) {
            int to = Math.min(from + MAX_BATCH_SIZE, ordered.size());
            Set<String> batch = Set.copyOf(ordered.subList(from, to));
            var result = references.validate(batch);
            if (!result.statuses().keySet().equals(batch)) {
                throw new SystemCatalogException.DependencyProtocol(
                        "IAM Catalog Reference 对账响应缺少或增加了 Code");
            }
            for (CatalogReferenceValidationPort.Status status : result.statuses().values()) {
                switch (status) {
                    case ENABLED -> enabled++;
                    case DISABLED -> disabled++;
                    case UNKNOWN -> unknown++;
                }
            }
        }

        record(enabled, disabled, unknown);
        if (disabled > 0 || unknown > 0) {
            LOGGER.warn(
                    "Catalog Reference 对账发现失效引用。applications={} references={} disabled={} unknown={}",
                    published.size(),
                    codes.size(),
                    disabled,
                    unknown);
        }
        return new ReconciliationResult(
                published.size(), codes.size(), enabled, disabled, unknown);
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

    private static void collect(SystemCatalogSnapshot snapshot, Collection<String> target) {
        snapshot.channels().forEach(channel ->
                channel.navigation().forEach(node -> collect(node, target)));
    }

    private static void collect(
            SystemCatalogSnapshot.NodeSnapshot node,
            Collection<String> target) {
        if (node.permissionCode() != null) {
            target.add(node.permissionCode());
        }
        node.children().forEach(child -> collect(child, target));
    }

    private void record(int enabled, int disabled, int unknown) {
        meterRegistry.counter(METRIC, "status", "enabled").increment(enabled);
        meterRegistry.counter(METRIC, "status", "disabled").increment(disabled);
        meterRegistry.counter(METRIC, "status", "unknown").increment(unknown);
    }

    /** 不包含具体 Reference Code 的低基数执行结果。 */
    public record ReconciliationResult(
            int applicationCount,
            int referenceCount,
            int enabledCount,
            int disabledCount,
            int unknownCount) {
    }
}
