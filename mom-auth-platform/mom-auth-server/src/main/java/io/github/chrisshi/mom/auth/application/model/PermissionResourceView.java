package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionResourceEntity;
import java.time.Instant;

/**
 * Auth 权限资源的 Application 只读视图。
 *
 * <p>只包含目录展示所需事实，不携带 ORM 或 HTTP 类型；不可变且线程安全。
 * 数据库读取失败由 Application 向上报告，不伪造资源归属。</p>
 */
public record PermissionResourceView(
    String id, String domainCode, String resourceCode, String name, String description,
    int sortOrder, boolean enabled, long version, Instant createdAt, Instant updatedAt
) {
    /**
     * 将持久化实体映射成只读视图，无数据库副作用。
     *
     * @param entity 已读取的资源实体
     * @return 资源视图
     */
    public static PermissionResourceView from(PermissionResourceEntity entity) {
        return new PermissionResourceView(
            entity.getId(), entity.getDomainCode(), entity.getResourceCode(), entity.getName(),
            entity.getDescription(), entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()),
            entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
