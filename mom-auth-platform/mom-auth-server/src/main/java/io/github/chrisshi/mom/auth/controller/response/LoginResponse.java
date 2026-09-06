package io.github.chrisshi.mom.auth.controller.response;

import io.github.chrisshi.mom.auth.application.model.LoginView;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt) {
    public static LoginResponse from(LoginView view) {
        return new LoginResponse(view.accessToken(), view.tokenType(), view.expiresAt());
    }
}
