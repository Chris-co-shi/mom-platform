package io.github.chrisshi.mom.mdm.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.ProductionVersionApplication;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.ProductionVersionPageParams;
import io.github.chrisshi.mom.mdm.application.model.ProductionVersionModels;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.time.LocalDate;

/** ProductionVersion 的 HTTP DTO、状态动作和权限边界。 */
@RestController
@RequestMapping("/production-versions")
@RequiredArgsConstructor
public class ProductionVersionController {
    private final ProductionVersionApplication application;

    

    /** 创建 DRAFT 生产版本。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<ProductionVersionModels.View> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(application.create(request.code(), input(request)));
    }

    /** 条件分页查询业务版本。 */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<PageResult<ProductionVersionModels.View>> search(
            @RequestBody PageQuery<ProductionVersionPageParams> query) {
        return Result.success(application.search(query));
    }

    /** 按稳定 ID 查询生产版本。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<ProductionVersionModels.View> get(@PathVariable String id) {
        return Result.success(application.get(id));
    }

    /** 仅 DRAFT 可修改，技术锁冲突返回 409。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<ProductionVersionModels.View> update(@PathVariable String id,
            @Valid @RequestBody UpdateRequest request) {
        return Result.success(application.updateDraft(id, request.lockVersion(), input(request)));
    }

    /** 发布并检查 `[validFrom, validTo)` 不重叠。 */
    @PatchMapping("/release/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<ProductionVersionModels.View> release(@PathVariable String id,
            @Valid @RequestBody VersionRequest request) {
        return Result.success(application.release(id, request.lockVersion()));
    }

    /** 废止已发布版本，历史记录保持可读。 */
    @PatchMapping("/obsolete/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<ProductionVersionModels.View> obsolete(@PathVariable String id,
            @Valid @RequestBody VersionRequest request) {
        return Result.success(application.obsolete(id, request.lockVersion()));
    }

    private static ProductionVersionModels.DraftInput input(DraftFields request) {
        return new ProductionVersionModels.DraftInput(request.name(), request.businessVersion(),
                request.plantId(), request.outputMaterialId(), request.formulaRevisionId(),
                request.routingRevisionId(), request.validFrom(), request.validTo());
    }

    /** 草稿字段共同契约。 */
    private interface DraftFields {
        String name(); String businessVersion(); String plantId(); String outputMaterialId();
        String formulaRevisionId(); String routingRevisionId(); LocalDate validFrom(); LocalDate validTo();
    }

    /** 新建请求；Code 是稳定身份。 */
    public record CreateRequest(@NotBlank @Size(max=64) String code,
            @NotBlank @Size(max=200) String name, @NotBlank @Size(max=64) String businessVersion,
            @NotBlank String plantId, @NotBlank String outputMaterialId,
            @NotBlank String formulaRevisionId, @NotBlank String routingRevisionId,
            @NotNull LocalDate validFrom, LocalDate validTo) implements DraftFields { }

    /** 草稿修改请求；Code 不允许改变。 */
    public record UpdateRequest(@NotBlank @Size(max=200) String name,
            @NotBlank @Size(max=64) String businessVersion, @NotBlank String plantId,
            @NotBlank String outputMaterialId, @NotBlank String formulaRevisionId,
            @NotBlank String routingRevisionId, @NotNull LocalDate validFrom, LocalDate validTo,
            @NotNull @PositiveOrZero Long lockVersion) implements DraftFields { }

    /** 发布和废止动作只接收技术锁版本。 */
    public record VersionRequest(@NotNull @PositiveOrZero Long lockVersion) { }
}
