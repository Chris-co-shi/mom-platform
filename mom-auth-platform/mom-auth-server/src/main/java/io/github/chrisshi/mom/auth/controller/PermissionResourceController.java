package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.PermissionResourceApplication;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionResourcePageParams;
import io.github.chrisshi.mom.auth.controller.request.ChangeStatusRequest;
import io.github.chrisshi.mom.auth.controller.request.CreatePermissionResourceRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdatePermissionResourceRequest;
import io.github.chrisshi.mom.auth.controller.response.PermissionResourceResponse;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth Permission Resource 的 HTTP 边界。
 *
 * <p>只使用既有 auth:permission:read/write 保护目录，不引入新的运行时权限。
 * Controller 不接触 Mapper/Entity；写入失败由 Application 事务和统一异常边界处理。</p>
 */
@RestController
@RequestMapping("/permission-resources")
public class PermissionResourceController {
    private final PermissionResourceApplication application;

    /**
     * 注入目录用例，无副作用。
     *
     * @param application 资源用例
     */
    public PermissionResourceController(PermissionResourceApplication application) {
        this.application = application;
    }

    /**
     * 创建资源，HTTP 201；唯一冲突返回 409。
     *
     * @param request 已通过基础校验的输入
     * @return 新资源
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<PermissionResourceResponse> create(@Valid @RequestBody CreatePermissionResourceRequest request) {
        return Result.success(PermissionResourceResponse.from(application.create(
            request.domainCode(), request.resourceCode(), request.name(), request.description(),
            request.sortOrder(), request.enabled()
        )));
    }

    /**
     * 服务端分页查询资源。
     *
     * @param query 强类型过滤与分页请求
     * @return 资源页
     */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public Result<PageResult<PermissionResourceResponse>> list(
        @RequestBody PageQuery<PermissionResourcePageParams> query
    ) {
        return Result.success(application.list(query).map(PermissionResourceResponse::from));
    }

    /**
     * 按技术 ID 查询资源。
     *
     * @param id 资源主键
     * @return 资源详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:read')")
    public Result<PermissionResourceResponse> get(@PathVariable String id) {
        return Result.success(PermissionResourceResponse.from(application.get(id)));
    }

    /**
     * 修改展示信息与状态；编码不可变。
     *
     * @param id 资源主键
     * @param request 带乐观版本的更新输入
     * @return 更新后资源
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<PermissionResourceResponse> update(
        @PathVariable String id, @Valid @RequestBody UpdatePermissionResourceRequest request
    ) {
        return Result.success(PermissionResourceResponse.from(application.update(
            id, request.name(), request.description(), request.sortOrder(), request.enabled(), request.version()
        )));
    }

    /**
     * 启用资源，不级联权限。
     *
     * @param id 资源主键
     * @param request 乐观版本
     * @return 更新后资源
     */
    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<PermissionResourceResponse> enable(
        @PathVariable String id, @Valid @RequestBody ChangeStatusRequest request
    ) {
        return Result.success(PermissionResourceResponse.from(application.enable(id, request.version())));
    }

    /**
     * 停用资源，只阻止后续创建或启用子权限。
     *
     * @param id 资源主键
     * @param request 乐观版本
     * @return 更新后资源
     */
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<PermissionResourceResponse> disable(
        @PathVariable String id, @Valid @RequestBody ChangeStatusRequest request
    ) {
        return Result.success(PermissionResourceResponse.from(application.disable(id, request.version())));
    }

    /**
     * 删除无权限引用的资源，不级联删除。
     *
     * @param id 资源主键
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:permission:write')")
    public Result<Void> delete(@PathVariable String id) {
        application.delete(id);
        return Result.success();
    }
}
