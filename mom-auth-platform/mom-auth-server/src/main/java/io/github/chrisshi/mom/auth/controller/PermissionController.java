package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.PermissionApplication;
import io.github.chrisshi.mom.auth.controller.request.CreatePermissionRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdatePermissionRequest;
import io.github.chrisshi.mom.auth.controller.response.PermissionResponse;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
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

/** Permission 管理 HTTP API；统一返回 Result，分页复用 mom-core PageResult。 */
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
    public Result<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request) {
        return Result.success(PermissionResponse.from(permissionApplication.create(
            request.code(), request.name(), request.description(), request.enabled()
        )));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public Result<PageResult<PermissionResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int pageSize
    ) {
        return Result.success(permissionApplication.list(pageNo, pageSize).map(PermissionResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public Result<PermissionResponse> get(@PathVariable String id) {
        return Result.success(PermissionResponse.from(permissionApplication.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<PermissionResponse> update(
        @PathVariable String id,
        @Valid @RequestBody UpdatePermissionRequest request
    ) {
        return Result.success(PermissionResponse.from(permissionApplication.update(
            id, request.name(), request.description(), request.enabled(), request.version()
        )));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<Void> delete(@PathVariable String id) {
        permissionApplication.delete(id);
        return Result.success();
    }
}
