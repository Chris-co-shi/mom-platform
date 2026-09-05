package io.github.chrisshi.mom.auth.application.model;

import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;

import java.time.Instant;

public record UserView(
    String id,
    String username,
    String displayName,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {

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
