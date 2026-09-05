package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.controller.request.CreateUserRequest;
import io.github.chrisshi.mom.auth.controller.request.ReplaceUserRolesRequest;
import io.github.chrisshi.mom.auth.controller.request.ResetUserPasswordRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdateUserRequest;
import io.github.chrisshi.mom.auth.controller.response.RoleResponse;
import io.github.chrisshi.mom.auth.controller.response.UserResponse;
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

/** 用户管理 HTTP API；不直接访问 Mapper、Entity、PasswordEncoder 或 TokenStore。 */
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
    public Result<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(UserResponse.from(userApplication.create(
            request.username(), request.password(), request.displayName(), request.enabled()
        )));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auth:user:read')")
    public Result<PageResult<UserResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int pageSize
    ) {
        return Result.success(userApplication.list(pageNo, pageSize).map(UserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:read')")
    public Result<UserResponse> get(@PathVariable String id) {
        return Result.success(UserResponse.from(userApplication.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<UserResponse> update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.success(UserResponse.from(
            userApplication.update(id, request.displayName(), request.enabled(), request.version())
        ));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<UserResponse> resetPassword(
        @PathVariable String id,
        @Valid @RequestBody ResetUserPasswordRequest request
    ) {
        return Result.success(UserResponse.from(
            userApplication.resetPassword(id, request.newPassword(), request.version())
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<Void> delete(@PathVariable String id) {
        userApplication.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('auth:user:read')")
    public Result<List<RoleResponse>> roles(@PathVariable String id) {
        return Result.success(userApplication.roles(id).stream().map(RoleResponse::from).toList());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<List<RoleResponse>> replaceRoles(
        @PathVariable String id,
        @Valid @RequestBody ReplaceUserRolesRequest request
    ) {
        return Result.success(
            userApplication.replaceRoles(id, request.roleIds()).stream().map(RoleResponse::from).toList()
        );
    }
}
