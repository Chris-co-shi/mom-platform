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

/**
 * Permission 管理 HTTP API。
 *
 * <p>Controller 只负责 HTTP 协议和 `@PreAuthorize` 权限入口；Permission 生命周期、乐观锁和
 * Role-Permission 引用保护由 {@link PermissionApplication} 负责。分页响应复用 mom-core PageResult。</p>
 */
@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionApplication permissionApplication;

    public PermissionController(PermissionApplication permissionApplication) {
        this.permissionApplication = permissionApplication;
    }

    /**
     * 创建 Permission。
     *
     * @param request 已校验的 Permission 创建请求
     * @return 新建 Permission 响应
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request) {
        return Result.success(PermissionResponse.from(permissionApplication.create(
            request.code(), request.name(), request.description(), request.enabled()
        )));
    }

    /**
     * 分页查询 Permission 目录。
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页数量，最大 200
     * @return 统一分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public Result<PageResult<PermissionResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int pageSize
    ) {
        return Result.success(permissionApplication.list(pageNo, pageSize).map(PermissionResponse::from));
    }

    /**
     * 查询单个 Permission。
     *
     * @param id Permission 主键
     * @return Permission 响应
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public Result<PermissionResponse> get(@PathVariable String id) {
        return Result.success(PermissionResponse.from(permissionApplication.get(id)));
    }

    /**
     * 更新 Permission 基本信息。
     *
     * @param id Permission 主键
     * @param request 包含乐观锁 version 的更新请求
     * @return 更新后的 Permission 响应
     */
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

    /**
     * 删除未被角色引用的 Permission。
     *
     * @param id Permission 主键
     * @return 空数据的统一成功结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<Void> delete(@PathVariable String id) {
        permissionApplication.delete(id);
        return Result.success();
    }
}
