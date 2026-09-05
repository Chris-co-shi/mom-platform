package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;

import java.time.Instant;

/**
 * Application 层角色只读视图。
 *
 * <p>当前 RoleEntity → RoleView 是简单单表投影，静态工厂用于集中字段映射，
 * 不为机械复制引入额外 Converter/Assembler 层。</p>
 */
public record RoleView(
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
     * 从角色持久化实体创建 Application View。
     *
     * @param entity 角色实体
     * @return 角色视图
     */
    public static RoleView from(RoleEntity entity) {
        return new RoleView(
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
