package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ResetUserPasswordRequest(
    @NotBlank @Size(min = 8, max = 128) String newPassword,
    @NotNull @PositiveOrZero Long version
) {
}
