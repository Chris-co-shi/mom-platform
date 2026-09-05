package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;

import java.time.Instant;

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
