package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;

import java.time.Instant;

/**
 * Application 层对外暴露的用户只读视图。
 *
 * <p>当前 UserEntity → UserView 仅是单表 1:1 字段投影，因此由 View 提供静态工厂集中机械映射，
 * 避免多个 Application 重复私有 toView 方法；在没有多来源或复杂转换前不额外引入 Converter/MapStruct。</p>
 */
public record UserView(
    String id,
    String username,
    String displayName,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * 从持久化实体创建用户视图。
     *
     * @param entity 用户持久化实体
     * @return 不携带密码摘要的用户视图
     */
    public static UserView from(UserEntity entity) {
        return new UserView(
            entity.getId(),
            entity.getUsername(),
            entity.getDisplayName(),
            Boolean.TRUE.equals(entity.getEnabled()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
