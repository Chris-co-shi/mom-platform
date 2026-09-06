package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;

import java.time.Instant;

/**
 * Application 层 Permission 只读视图。
 *
 * <p>当前 PermissionEntity → PermissionView 只是单表字段投影，因此由静态工厂完成集中映射；
 * 若以后出现多来源聚合或复杂转换，再基于真实复杂度引入独立转换组件。</p>
 */
public record PermissionView(
    String id,
    String code,
    String name,
    String description,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * 从 Permission 持久化实体创建 Application View。
     *
     * @param entity Permission 实体
     * @return Permission 视图
     */
    public static PermissionView from(PermissionEntity entity) {
        return new PermissionView(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getDescription(),
            Boolean.TRUE.equals(entity.getEnabled()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
