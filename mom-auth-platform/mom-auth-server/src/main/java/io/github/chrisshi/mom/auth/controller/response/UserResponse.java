package io.github.chrisshi.mom.auth.controller.response;

import io.github.chrisshi.mom.auth.application.model.UserView;

import java.time.Instant;

public record UserResponse(
    String id,
    String username,
    String displayName,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserResponse from(UserView view) {
        return new UserResponse(
            view.id(), view.username(), view.displayName(), view.enabled(),
            view.version(), view.createdAt(), view.updatedAt()
        );
    }
}
