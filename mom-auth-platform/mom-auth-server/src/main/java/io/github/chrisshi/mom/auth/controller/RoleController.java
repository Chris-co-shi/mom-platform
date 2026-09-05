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

/**
 * 角色管理 HTTP API。
 *
 * <p>Controller 只做协议适配和权限入口，角色生命周期、引用保护以及 Role-Permission 关系事务
 * 统一由 {@link RoleApplication} 负责。授权端点始终基于 Permission，不为 PLATFORM_ADMIN 等角色硬编码旁路。</p>
 */
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleApplication roleApplication;

    public RoleController(RoleApplication roleApplication) {
        this.roleApplication = roleApplication;
    }

    /**
     * 创建角色。
     *
     * @param request 已校验的角色创建请求
     * @return 新建角色响应
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(RoleResponse.from(roleApplication.create(
            request.code(), request.name(), request.description(), request.enabled()
        )));
    }

    /**
     * 分页查询角色目录。
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页数量，最大 200
     * @return 统一分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('auth:role:read')")
    public Result<PageResult<RoleResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int pageSize
    ) {
        return Result.success(roleApplication.list(pageNo, pageSize).map(RoleResponse::from));
    }

    /**
     * 查询单个角色。
     *
     * @param id 角色主键
     * @return 角色响应
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public Result<RoleResponse> get(@PathVariable String id) {
        return Result.success(RoleResponse.from(roleApplication.get(id)));
    }

    /**
     * 更新角色基本信息。
     *
     * @param id 角色主键
     * @param request 包含乐观锁 version 的更新请求
     * @return 更新后的角色响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<RoleResponse> update(@PathVariable String id, @Valid @RequestBody UpdateRoleRequest request) {
        return Result.success(RoleResponse.from(roleApplication.update(
            id, request.name(), request.description(), request.enabled(), request.version()
        )));
    }

    /**
     * 删除未被用户或 Permission 关系引用的角色。
     *
     * @param id 角色主键
     * @return 空数据的统一成功结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public Result<Void> delete(@PathVariable String id) {
        roleApplication.delete(id);
        return Result.success();
    }

    /**
     * 查询角色当前拥有的 Permission。
     *
     * @param id 角色主键
     * @return Permission 列表
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public Result<List<PermissionResponse>> permissions(@PathVariable String id) {
        return Result.success(roleApplication.permissions(id).stream().map(PermissionResponse::from).toList());
    }

    /**
     * 整体替换角色 Permission 关系。
     *
     * <p>关系变更不会主动刷新已经签发的 V1 Token authority 快照。</p>
     *
     * @param id 角色主键
     * @param request 目标 Permission 主键集合
     * @return 替换后的 Permission 列表
     */
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
