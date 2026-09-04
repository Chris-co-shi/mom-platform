package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.application.model.PageView;
import io.github.chrisshi.mom.auth.controller.request.CreateUserRequest;
import io.github.chrisshi.mom.auth.controller.request.ReplaceUserRolesRequest;
import io.github.chrisshi.mom.auth.controller.request.ResetUserPasswordRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdateUserRequest;
import io.github.chrisshi.mom.auth.controller.response.OffsetPageResponse;
import io.github.chrisshi.mom.auth.controller.response.RoleResponse;
import io.github.chrisshi.mom.auth.controller.response.UserResponse;
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
@RequestMapping("/users")
public class UserController {

    private final UserApplication userApplication;

    public UserController(UserApplication userApplication) {
        this.userApplication = userApplication;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:user:write')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userApplication.create(
            request.username(), request.password(), request.displayName(), request.enabled()
        ));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auth:user:read')")
    public OffsetPageResponse<UserResponse> list(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "0") @Min(0) long offset
    ) {
        PageView<io.github.chrisshi.mom.auth.application.model.UserView> page = userApplication.list(limit, offset);
        return new OffsetPageResponse<>(page.items().stream().map(UserResponse::from).toList(), page.total(), limit, offset);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:read')")
    public UserResponse get(@PathVariable String id) {
        return UserResponse.from(userApplication.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userApplication.update(id, request.displayName(), request.enabled(), request.version()));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public UserResponse resetPassword(@PathVariable String id, @Valid @RequestBody ResetUserPasswordRequest request) {
        return UserResponse.from(userApplication.resetPassword(id, request.newPassword(), request.version()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('auth:user:write')")
    public void delete(@PathVariable String id) {
        userApplication.delete(id);
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('auth:user:read')")
    public List<RoleResponse> roles(@PathVariable String id) {
        return userApplication.roles(id).stream().map(RoleResponse::from).toList();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public List<RoleResponse> replaceRoles(
        @PathVariable String id,
        @Valid @RequestBody ReplaceUserRolesRequest request
    ) {
        return userApplication.replaceRoles(id, request.roleIds()).stream().map(RoleResponse::from).toList();
    }
}
