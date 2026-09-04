package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceUserRolesRequest(
    @NotNull @Size(max = 200) List<@Valid @NotBlank String> roleIds
) {
}
