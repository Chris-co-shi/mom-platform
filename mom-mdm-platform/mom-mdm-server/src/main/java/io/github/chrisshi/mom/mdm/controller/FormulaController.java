package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.FormulaApplication;
import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.mdm.application.model.FormulaModels;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.FormulaPageParams;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Formula 修订的 HTTP 协议边界；权限独立于前端导航，事务和量纲规则留在 Application。
 */
@RestController
@RequestMapping("/formulas")
public class FormulaController {
    private final FormulaApplication application;

    /**
     * 注入 MDM Formula 用例，不直接访问数据库 Entity。
     */
    public FormulaController(FormulaApplication application) {
        this.application = application;
    }

    /**
     * 原子创建配方及第一草稿修订，成功返回 201。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<FormulaModels.DetailView> create(@Valid @RequestBody CreateFormulaRequest request) {
        return Result.success(application.createFormula(request.code(), request.name(), request.plantId(),
            request.outputMaterialId(), decimalString(request.baseBatchQuantity()), request.baseUomId(),
            inputs(request.items())));
    }

    /**
     * 按组合条件分页 Formula 身份；详情端点再读取具体业务修订。
     */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<PageResult<FormulaModels.FormulaView>> search(@RequestBody PageQuery<FormulaPageParams> query) {
        return Result.success(application.pageFormulas(query));
    }

    /**
     * 查询 Formula 最新修订及其有序明细。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<FormulaModels.DetailView> get(@PathVariable String id) {
        return Result.success(application.getFormula(id));
    }

    /**
     * 按稳定修订 ID 读取历史定义。
     */
    @GetMapping("/revisions/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<FormulaModels.DetailView> getRevision(@PathVariable String id) {
        return Result.success(application.getRevision(id));
    }

    /**
     * 仅 DRAFT 修订可替换明细，技术锁版本冲突返回 409。
     */
    @PutMapping("/revisions/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<FormulaModels.DetailView> updateDraft(
        @PathVariable String id, @Valid @RequestBody UpdateDraftRequest request) {
        return Result.success(application.updateDraft(id, request.lockVersion(), inputs(request.items())));
    }

    /**
     * 从上一不可变修订复制下一 DRAFT，业务修订号由数据库行锁串行分配。
     */
    @PostMapping("/revisions/next/{formulaId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<FormulaModels.DetailView> nextRevision(@PathVariable String formulaId) {
        return Result.success(application.createNextRevision(formulaId));
    }

    /**
     * 发布前重新校验工厂、物料和单位量纲，发布后不可修改。
     */
    @PatchMapping("/revisions/release/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<FormulaModels.DetailView> release(@PathVariable String id,
                                                    @Valid @RequestBody VersionRequest request) {
        return Result.success(application.releaseRevision(id, request.lockVersion()));
    }

    /**
     * 废止已发布修订；历史内容和版本仍可读取。
     */
    @PatchMapping("/revisions/obsolete/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:write')")
    public Result<FormulaModels.DetailView> obsolete(@PathVariable String id,
                                                     @Valid @RequestBody VersionRequest request) {
        return Result.success(application.obsoleteRevision(id, request.lockVersion()));
    }

    private static List<FormulaModels.ItemInput> inputs(List<ItemRequest> items) {
        return items.stream().map(item -> new FormulaModels.ItemInput(
            item.sequenceNo(), item.materialId(), decimalString(item.quantity()), item.uomId())).toList();
    }

    private static String decimalString(JsonNode node) {
        if (node == null || !node.isString() || node.textValue().isBlank()) {
            throw MdmException.invalidReference();
        }
        return node.textValue();
    }

    /**
     * 创建请求数量均为 Decimal String，不接受 JSON Number 隐式精度损失。
     */
    public record CreateFormulaRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String plantId,
        @NotBlank String outputMaterialId,
        @NotNull JsonNode baseBatchQuantity,
        @NotBlank String baseUomId,
        @NotEmpty List<@Valid ItemRequest> items) {
    }

    /**
     * 草稿更新只接收明细和技术锁版本，不改变 Formula 身份及产出基准。
     */
    public record UpdateDraftRequest(
        @NotNull @PositiveOrZero Long lockVersion,
        @NotEmpty List<@Valid ItemRequest> items) {
    }

    /**
     * 单个有序物料用量，数量为精确 Decimal String。
     */
    public record ItemRequest(
        @NotNull @Positive Integer sequenceNo,
        @NotBlank String materialId,
        @NotNull JsonNode quantity,
        @NotBlank String uomId) {
    }

    /**
     * 发布、废止动作携带读取时的技术锁版本。
     */
    public record VersionRequest(@NotNull @PositiveOrZero Long lockVersion) {
    }

}
