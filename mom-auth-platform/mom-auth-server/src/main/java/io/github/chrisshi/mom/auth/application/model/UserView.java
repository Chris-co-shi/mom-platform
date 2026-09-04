package io.github.chrisshi.mom.auth.application.model;

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
}
