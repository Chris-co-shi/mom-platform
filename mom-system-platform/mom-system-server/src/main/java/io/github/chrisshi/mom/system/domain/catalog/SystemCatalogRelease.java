package io.github.chrisshi.mom.system.domain.catalog;

import java.time.Instant;

/**
 * Application Catalog 的不可变完整发布版本。
 *
 * <p>Release 只追加，不更新、不删除；Rollback 复制历史 Snapshot 形成新的单调版本。snapshotJson 只能由受控
 * Codec 生成，不包含数据库 ID、Path、Component、Layout、JavaScript 或 HTML。数据库 Trigger 继续作为
 * 应用约束之外的不可变兜底。</p>
 */
public record SystemCatalogRelease(
        String id,
        String applicationId,
        String applicationCode,
        long releaseVersion,
        int snapshotSchemaVersion,
        int routeContractVersion,
        long sourceApplicationVersion,
        Long sourceReleaseVersion,
        String snapshotJson,
        int nodeCount,
        String checksum,
        String changeNote,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {
}
