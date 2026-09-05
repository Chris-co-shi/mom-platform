package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;

import java.time.Instant;

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
