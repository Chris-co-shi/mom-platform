package io.github.chrisshi.mom.auth.application.model;

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
}
