package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionResourceEntity;
import io.github.chrisshi.mom.auth.infrastructure.query.PermissionCatalogRow;

import java.time.Instant;

/**
 * Application 层 Permission 只读视图。
 *
 * <p>该视图组合本服务权威 Permission 和 Resource 元数据；用于目录展示，不是独立授权事实。
 * 不暴露 ORM 或 HTTP 类型；不可变且线程安全。</p>
 */
public record PermissionView(
    String id,
    String resourceId,
    String domainCode,
    String resourceCode,
    String resourceName,
    String actionCode,
    String code,
    String name,
    String description,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * 从同一 Auth Schema 的 Permission 与 Resource 实体创建视图。
     *
     * @param entity Permission 实体
     * @param resource 其权威资源实体
     * @return Permission 视图
     */
    public static PermissionView from(PermissionEntity entity, PermissionResourceEntity resource) {
        return new PermissionView(
            entity.getId(),
            entity.getResourceId(),
            resource.getDomainCode(),
            resource.getResourceCode(),
            resource.getName(),
            entity.getActionCode(),
            entity.getCode(),
            entity.getName(),
            entity.getDescription(),
            Boolean.TRUE.equals(entity.getEnabled()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * 从有界本地 JOIN 查询行创建视图；分页时避免逐条读取 Resource。
     *
     * @param row 当前数据库快照的查询行
     * @return Permission 视图
     */
    public static PermissionView from(PermissionCatalogRow row) {
        return new PermissionView(
            row.getId(), row.getResourceId(), row.getDomainCode(), row.getResourceCode(),
            row.getResourceName(), row.getActionCode(), row.getCode(), row.getName(),
            row.getDescription(), Boolean.TRUE.equals(row.getEnabled()), row.getVersion(),
            row.getCreatedAt(), row.getUpdatedAt()
        );
    }
}
