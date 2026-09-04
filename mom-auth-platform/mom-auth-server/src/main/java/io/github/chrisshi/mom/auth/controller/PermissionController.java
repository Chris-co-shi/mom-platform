package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.PermissionApplication;
import io.github.chrisshi.mom.auth.application.model.PageView;
import io.github.chrisshi.mom.auth.controller.request.CreatePermissionRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdatePermissionRequest;
import io.github.chrisshi.mom.auth.controller.response.OffsetPageResponse;
import io.github.chrisshi.mom.auth.controller.response.PermissionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionApplication permissionApplication;

    public PermissionController(PermissionApplication permissionApplication) {
        this.permissionApplication = permissionApplication;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public PermissionResponse create(@Valid @RequestBody CreatePermissionRequest request) {
        return PermissionResponse.from(permissionApplication.create(
            request.code(), request.name(), request.description(), request.enabled()
        ));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public OffsetPageResponse<PermissionResponse> list(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "0") @Min(0) long offset
    ) {
        PageView<io.github.chrisshi.mom.auth.application.model.PermissionView> page = permissionApplication.list(limit, offset);
        return new OffsetPageResponse<>(
            page.items().stream().map(PermissionResponse::from).toList(), page.total(), limit, offset
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public PermissionResponse get(@PathVariable String id) {
        return PermissionResponse.from(permissionApplication.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public PermissionResponse update(@PathVariable String id, @Valid @RequestBody UpdatePermissionRequest request) {
        return PermissionResponse.from(permissionApplication.update(
            id, request.name(), request.description(), request.enabled(), request.version()
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public void delete(@PathVariable String id) {
        permissionApplication.delete(id);
    }
}
