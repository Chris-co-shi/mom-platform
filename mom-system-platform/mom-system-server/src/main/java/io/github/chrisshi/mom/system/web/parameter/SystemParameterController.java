package io.github.chrisshi.mom.system.web.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.CreateCommand;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageQuery;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageView;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.ParameterView;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.StatusCommand;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.UpdateCommand;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * System Parameter 的 HTTP 入站 Adapter。
 *
 * <p>Controller 只映射 HTTP 与 Application Command/Query，不依赖 Domain、Mapper、Entity 或 Repository。
 * 所有端点都需要认证；管理读取和有效值解析引用 IAM 的 system:parameter:read，写入引用
 * system:parameter:write。Permission 只存在 JWT Authority 中，System 数据库不保存其定义或分配。</p>
 */
@RestController
@RequestMapping("/api/system")
public class SystemParameterController {
    private final SystemParameterApplicationService service;

    public SystemParameterController(SystemParameterApplicationService service) {
        this.service = service;
    }

    /** 创建参数；成功返回 201，不接受客户端审计字段。 */
    @PostMapping("/admin/parameters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:parameter:write')")
    public ParameterView create(@RequestBody CreateParameterRequest request) {
        return service.create(new CreateCommand(request.scopeType(), request.scopeCode(), request.parameterKey(),
                request.valueType(), request.parameterValue(), request.description(), request.enabled()));
    }

    /** 使用 Version 更新类型化值与描述，Scope 和 Key 保持不可变。 */
    @PutMapping("/admin/parameters/{id}")
    @PreAuthorize("hasAuthority('system:parameter:write')")
    public ParameterView update(@PathVariable String id, @RequestBody UpdateParameterRequest request) {
        return service.update(id, new UpdateCommand(
                request.version(), request.valueType(), request.parameterValue(), request.description()));
    }

    /** 使用 Version 启用或禁用参数，不提供物理删除。 */
    @PatchMapping("/admin/parameters/{id}/status")
    @PreAuthorize("hasAuthority('system:parameter:write')")
    public ParameterView changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return service.changeStatus(id, new StatusCommand(request.enabled(), request.version()));
    }

    /** 按技术主键读取管理视图。 */
    @GetMapping("/admin/parameters/{id}")
    @PreAuthorize("hasAuthority('system:parameter:read')")
    public ParameterView get(@PathVariable String id) {
        return service.get(id);
    }

    /** 按固定排序和有限精确条件分页读取。 */
    @GetMapping("/admin/parameters")
    @PreAuthorize("hasAuthority('system:parameter:read')")
    public PageView page(
            @RequestParam(required = false) ParameterScopeType scopeType,
            @RequestParam(required = false) String scopeCode,
            @RequestParam(required = false) String parameterKey,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.page(new PageQuery(scopeType, scopeCode, parameterKey, enabled, page, size));
    }

    /** 解析可选应用覆盖后的有效参数；无有效值返回 404。 */
    @GetMapping("/parameters/{parameterKey}")
    @PreAuthorize("hasAuthority('system:parameter:read')")
    public ResolvedSystemParameter resolve(
            @PathVariable String parameterKey,
            @RequestParam(required = false) String applicationCode) {
        return service.resolve(parameterKey, applicationCode);
    }

    /** 创建请求；字段列表刻意不包含 createdBy/updatedBy/operator。 */
    public record CreateParameterRequest(
            ParameterScopeType scopeType,
            String scopeCode,
            String parameterKey,
            ParameterValueType valueType,
            String parameterValue,
            String description,
            Boolean enabled) {
    }

    /** 更新请求；Scope 和 Key 不在协议中，避免 Rename 语义。 */
    public record UpdateParameterRequest(
            long version,
            ParameterValueType valueType,
            String parameterValue,
            String description) {
    }

    /** 版本化启停请求。 */
    public record StatusRequest(boolean enabled, long version) {
    }
}
