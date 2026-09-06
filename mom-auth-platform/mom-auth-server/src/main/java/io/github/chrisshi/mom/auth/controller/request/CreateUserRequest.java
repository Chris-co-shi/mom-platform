package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 120) String username,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(max = 200) String displayName,
    @NotNull Boolean enabled
) {
}
