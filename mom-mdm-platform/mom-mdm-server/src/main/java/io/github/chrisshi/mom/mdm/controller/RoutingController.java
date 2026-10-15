package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.RoutingApplication;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.RoutingPageParams;
import io.github.chrisshi.mom.mdm.application.model.RoutingModels;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Routing 的 HTTP 协议、DTO 和权限边界；状态、并发和引用校验由 Application 承担。 */
@RestController
@RequestMapping("/routings")
public class RoutingController {
    private final RoutingApplication application;

    /** 注入工艺路线用例，不让 Controller 直接访问持久化 Entity。 */
    public RoutingController(RoutingApplication application) { this.application = application; }

    /** 创建路线和第一草稿修订。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<RoutingModels.DetailView> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(application.create(request.code(), request.name(), request.plantId(),
                inputs(request.steps())));
    }

    /** 按工厂、编码稳定分页查询路线身份。 */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<PageResult<RoutingModels.RoutingView>> search(@RequestBody PageQuery<RoutingPageParams> query) {
        return Result.success(application.search(query));
    }

    /** 返回最新修订及其步骤。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<RoutingModels.DetailView> get(@PathVariable String id) {
        return Result.success(application.get(id));
    }

    /** 按稳定修订 ID 查询历史定义。 */
    @GetMapping("/revisions/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<RoutingModels.DetailView> getRevision(@PathVariable String id) {
        return Result.success(application.getRevision(id));
    }

    /** 只允许草稿在技术锁匹配时替换工序。 */
    @PutMapping("/revisions/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<RoutingModels.DetailView> update(@PathVariable String id,
            @Valid @RequestBody UpdateRequest request) {
        return Result.success(application.updateDraft(id, request.lockVersion(), inputs(request.steps())));
    }

    /** 克隆上一修订为新草稿。 */
    @PostMapping("/revisions/next/{routingId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<RoutingModels.DetailView> next(@PathVariable String routingId) {
        return Result.success(application.createNextRevision(routingId));
    }

    /** 发布前复验资源父链及工厂归属。 */
    @PatchMapping("/revisions/release/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<RoutingModels.DetailView> release(@PathVariable String id,
            @Valid @RequestBody VersionRequest request) {
        return Result.success(application.release(id, request.lockVersion()));
    }

    /** 废止已发布修订但保留历史。 */
    @PatchMapping("/revisions/obsolete/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<RoutingModels.DetailView> obsolete(@PathVariable String id,
            @Valid @RequestBody VersionRequest request) {
        return Result.success(application.obsolete(id, request.lockVersion()));
    }

    private static List<RoutingModels.StepInput> inputs(List<StepRequest> steps) {
        return steps.stream().map(step -> new RoutingModels.StepInput(step.sequenceNo(),
                step.operationCode(), step.name(), step.resourceType(), step.resourceId())).toList();
    }

    /** 创建路线请求；Plant 和 Code 后续不可通过草稿入口改变。 */
    public record CreateRequest(@NotBlank @Size(max=64) String code,
            @NotBlank @Size(max=200) String name, @NotBlank String plantId,
            @NotEmpty List<@Valid StepRequest> steps) { }

    /** 草稿更新只携带步骤和技术锁版本。 */
    public record UpdateRequest(@NotNull @PositiveOrZero Long lockVersion,
            @NotEmpty List<@Valid StepRequest> steps) { }

    /** 一个有序工序和单个制造资源目录引用。 */
    public record StepRequest(@NotNull @Positive Integer sequenceNo,
            @NotBlank String operationCode, @NotBlank String name,
            @NotBlank String resourceType, @NotBlank String resourceId) { }

    /** 生命周期动作的技术锁版本。 */
    public record VersionRequest(@NotNull @PositiveOrZero Long lockVersion) { }
}
