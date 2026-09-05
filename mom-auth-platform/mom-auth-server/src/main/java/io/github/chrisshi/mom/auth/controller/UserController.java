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

/**
 * 用户管理 HTTP API。
 *
 * <p>该 Controller 只负责 HTTP 参数校验、权限入口和 Request/View/Response 转换；
 * 用户业务规则、事务、密码编码与 User-Role 关系完整性由 {@link UserApplication} 负责。
 * Controller 禁止直接访问 Mapper、Entity、PasswordEncoder 或 TokenStore。</p>
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserApplication userApplication;

    public UserController(UserApplication userApplication) {
        this.userApplication = userApplication;
    }

    /**
     * 创建用户。
     *
     * @param request 已通过 Bean Validation 的创建请求
     * @return 统一 Result 包装的新建用户
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(UserResponse.from(userApplication.create(
            request.username(), request.password(), request.displayName(), request.enabled()
        )));
    }

    /**
     * 分页查询用户目录。
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页数量，最大 200
     * @return 统一 Result 包装的 PageResult
     */
    @GetMapping
    @PreAuthorize("hasAuthority('auth:user:read')")
    public Result<PageResult<UserResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int pageSize
    ) {
        return Result.success(userApplication.list(pageNo, pageSize).map(UserResponse::from));
    }

    /**
     * 查询单个用户。
     *
     * @param id 用户主键
     * @return 用户响应
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:read')")
    public Result<UserResponse> get(@PathVariable String id) {
        return Result.success(UserResponse.from(userApplication.get(id)));
    }

    /**
     * 更新用户展示信息和启用状态。
     *
     * @param id 用户主键
     * @param request 包含乐观锁 version 的更新请求
     * @return 更新后的用户响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<UserResponse> update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.success(UserResponse.from(
            userApplication.update(id, request.displayName(), request.enabled(), request.version())
        ));
    }

    /**
     * 重置用户密码。
     *
     * <p>该端点只修改密码摘要，不承诺撤销该用户已经签发的其他 V1 Token。</p>
     *
     * @param id 用户主键
     * @param request 新密码与乐观锁 version
     * @return 更新后的用户响应
     */
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

    /**
     * 删除未被 User-Role 关系引用的用户。
     *
     * @param id 用户主键
     * @return 空数据的统一成功结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:user:write')")
    public Result<Void> delete(@PathVariable String id) {
        userApplication.delete(id);
        return Result.success();
    }

    /**
     * 查询用户当前角色。
     *
     * @param id 用户主键
     * @return 用户角色列表
     */
    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('auth:user:read')")
    public Result<List<RoleResponse>> roles(@PathVariable String id) {
        return Result.success(userApplication.roles(id).stream().map(RoleResponse::from).toList());
    }

    /**
     * 整体替换用户角色关系。
     *
     * @param id 用户主键
     * @param request 目标角色主键集合
     * @return 替换后的角色列表
     */
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
