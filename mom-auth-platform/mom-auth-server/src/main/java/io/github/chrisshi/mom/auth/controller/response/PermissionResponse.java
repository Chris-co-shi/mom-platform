package io.github.chrisshi.mom.auth.controller.response;

import io.github.chrisshi.mom.auth.application.model.PermissionView;

import java.time.Instant;

public record PermissionResponse(
    String id,
    String code,
    String name,
    String description,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public static PermissionResponse from(PermissionView view) {
        return new PermissionResponse(
            view.id(), view.code(), view.name(), view.description(), view.enabled(),
            view.version(), view.createdAt(), view.updatedAt()
        );
    }
}
