package io.github.chrisshi.mom.auth.controller.response;

import io.github.chrisshi.mom.auth.application.model.RoleView;

import java.time.Instant;

public record RoleResponse(
    String id,
    String code,
    String name,
    String description,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public static RoleResponse from(RoleView view) {
        return new RoleResponse(
            view.id(), view.code(), view.name(), view.description(), view.enabled(),
            view.version(), view.createdAt(), view.updatedAt()
        );
    }
}
