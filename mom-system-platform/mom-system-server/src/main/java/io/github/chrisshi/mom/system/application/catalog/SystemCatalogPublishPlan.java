package io.github.chrisshi.mom.system.application.catalog;

import java.util.Set;

/**
 * 事务外权威校验完成后交给本地提交事务的不可变候选指纹。
 *
 * <p>提交事务必须重新构建 Snapshot，并同时比较 Application Version、checksum 与 Permission Set。任何差异均
 * 表示校验后聚合发生变化，必须按 stale version 拒绝。</p>
 */
public record SystemCatalogPublishPlan(
        long applicationVersion,
        String checksum,
        Set<String> permissionCodes) {
    public SystemCatalogPublishPlan {
        if (applicationVersion < 0 || checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("Catalog Publish Plan 非法");
        }
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }
}
