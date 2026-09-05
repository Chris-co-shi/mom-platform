package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.RoleApplication;
import io.github.chrisshi.mom.auth.controller.request.CreateRoleRequest;
import io.github.chrisshi.mom.auth.controller.request.ReplaceRolePermissionsRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdateRoleRequest;
import io.github.chrisshi.mom.auth.controller.response.PermissionResponse;
import io.github.chrisshi.mom.auth.controller.response.RoleResponse;
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

import java.util.List;

/** 角色管理 HTTP API；业务规则和关系事务由 RoleApplication 负责。 */
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleApplication roleApplication;

    public RoleController(RoleApplication roleApplication) {
        this.roleApplication = roleApplication;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(RoleResponse.from(roleApplication.create(
            request.code(), request.name(), request.description(), request.enabled()
        )));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auth:role:read')")
    public Result<PageResult<RoleResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int pageSize
    ) {
        return Result.success(roleApplication.list(pageNo, pageSize).map(RoleResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public Result<RoleResponse> get(@PathVariable String id) {
        return Result.success(RoleResponse.from(roleApplication.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<RoleResponse> update(@PathVariable String id, @Valid @RequestBody UpdateRoleRequest request) {
        return Result.success(RoleResponse.from(roleApplication.update(
            id, request.name(), request.description(), request.enabled(), request.version()
        )));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<Void> delete(@PathVariable String id) {
        roleApplication.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public Result<List<PermissionResponse>> permissions(@PathVariable String id) {
        return Result.success(roleApplication.permissions(id).stream().map(PermissionResponse::from).toList());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<List<PermissionResponse>> replacePermissions(
        @PathVariable String id,
        @Valid @RequestBody ReplaceRolePermissionsRequest request
    ) {
        return Result.success(
            roleApplication.replacePermissions(id, request.permissionIds()).stream()
                .map(PermissionResponse::from)
                .toList()
        );
    }
}
