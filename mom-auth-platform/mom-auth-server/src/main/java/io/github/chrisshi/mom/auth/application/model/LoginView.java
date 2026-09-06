package io.github.chrisshi.mom.auth.application.model;

import java.time.Instant;

public record LoginView(String accessToken, String tokenType, Instant expiresAt) {
}
