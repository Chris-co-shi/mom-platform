package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
    @NotBlank @Size(max = 160) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull Boolean enabled
) {
}
