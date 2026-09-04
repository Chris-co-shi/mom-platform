package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.RoleApplication;
import io.github.chrisshi.mom.auth.application.model.PageView;
import io.github.chrisshi.mom.auth.controller.request.CreateRoleRequest;
import io.github.chrisshi.mom.auth.controller.request.ReplaceRolePermissionsRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdateRoleRequest;
import io.github.chrisshi.mom.auth.controller.response.OffsetPageResponse;
import io.github.chrisshi.mom.auth.controller.response.PermissionResponse;
import io.github.chrisshi.mom.auth.controller.response.RoleResponse;
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
    public RoleResponse create(@Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(roleApplication.create(
            request.code(), request.name(), request.description(), request.enabled()
        ));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auth:role:read')")
    public OffsetPageResponse<RoleResponse> list(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "0") @Min(0) long offset
    ) {
        PageView<io.github.chrisshi.mom.auth.application.model.RoleView> page = roleApplication.list(limit, offset);
        return new OffsetPageResponse<>(page.items().stream().map(RoleResponse::from).toList(), page.total(), limit, offset);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public RoleResponse get(@PathVariable String id) {
        return RoleResponse.from(roleApplication.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public RoleResponse update(@PathVariable String id, @Valid @RequestBody UpdateRoleRequest request) {
        return RoleResponse.from(roleApplication.update(
            id, request.name(), request.description(), request.enabled(), request.version()
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('auth:role:write')")
    public void delete(@PathVariable String id) {
        roleApplication.delete(id);
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public List<PermissionResponse> permissions(@PathVariable String id) {
        return roleApplication.permissions(id).stream().map(PermissionResponse::from).toList();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public List<PermissionResponse> replacePermissions(
        @PathVariable String id,
        @Valid @RequestBody ReplaceRolePermissionsRequest request
    ) {
        return roleApplication.replacePermissions(id, request.permissionIds()).stream()
            .map(PermissionResponse::from)
            .toList();
    }
}
