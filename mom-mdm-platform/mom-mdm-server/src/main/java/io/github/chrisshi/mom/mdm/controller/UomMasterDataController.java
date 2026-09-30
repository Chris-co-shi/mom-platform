package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.UomMasterDataApplication;
import io.github.chrisshi.mom.mdm.application.UomMasterDataViews.*;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 量纲、计量单位类别、单位及规则版本的 HTTP 管理边界。
 *
 * <p>该 Controller 只做参数绑定、权限、Bean Validation 与 Result 包装，不访问 Mapper 或 Entity。
 * 读写分别要求 {@code mdm:uom:read/write} 权限；事务、不变量和数据库失败策略均由 Application 负责。</p>
 */
@RestController
@RequestMapping("/api/mdm")
public class UomMasterDataController {
    private final UomMasterDataApplication application;

    /** 注入 UOM 主数据用例入口。 @param application 不返回 Result 的 Application */
    public UomMasterDataController(UomMasterDataApplication application) { this.application = application; }

    /** 创建量纲，成功返回 201；非幂等。 @param r 创建协议 @return 统一量纲结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 校验或冲突时抛出 */
    @PostMapping("/dimensions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<DimensionView> createDimension(@Valid @RequestBody CreateDimensionRequest r) {
        return Result.success(application.createDimension(r.code(), r.nameZh(), r.nameEn(), r.timeExponent(),
                r.lengthExponent(), r.massExponent(), r.electricCurrentExponent(), r.temperatureExponent(),
                r.amountExponent(), r.luminousIntensityExponent(), r.status()));
    }

    /** 更新量纲名称，不接受 Code 或向量。 @param id 量纲 ID @param r 名称及版本 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在或冲突时抛出 */
    @PutMapping("/dimensions/{id}") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<DimensionView> updateDimension(@PathVariable String id, @Valid @RequestBody NameVersionRequest r) { return Result.success(application.updateDimension(id, r.nameZh(), r.nameEn(), r.version())); }

    /** 查询量纲详情；幂等且无写副作用。 @param id 量纲 ID @return 统一详情 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在时抛出 */
    @GetMapping("/dimensions/{id}") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<DimensionView> getDimension(@PathVariable String id) { return Result.success(application.getDimension(id)); }

    /** 按状态分页量纲且无写副作用。 @param status 可选状态 @param pageNo 页码 @param pageSize 每页条数 @return 统一分页结果 */
    @GetMapping("/dimensions") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<PageResult<DimensionView>> pageDimensions(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") @Positive long pageNo, @RequestParam(defaultValue="20") @Positive long pageSize) { return Result.success(application.pageDimensions(status, pageNo, pageSize)); }

    /** 启用量纲且不级联类别。 @param id 量纲 ID @param r 版本请求 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 冲突时抛出 */
    @PatchMapping("/dimensions/{id}/enable") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<DimensionView> enableDimension(@PathVariable String id, @Valid @RequestBody VersionRequest r) { return Result.success(application.enableDimension(id, r.version())); }

    /** 停用量纲且不级联类别。 @param id 量纲 ID @param r 版本请求 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 冲突时抛出 */
    @PatchMapping("/dimensions/{id}/disable") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<DimensionView> disableDimension(@PathVariable String id, @Valid @RequestBody VersionRequest r) { return Result.success(application.disableDimension(id, r.version())); }

    /** 原子创建类别和基准单位，成功返回 201。 @param r 创建协议 @return 统一类别结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 校验或冲突时抛出 */
    @PostMapping("/uom-categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<CategoryView> createCategory(@Valid @RequestBody CreateCategoryRequest r) { return Result.success(application.createCategory(r.code(), r.nameZh(), r.nameEn(), r.dimensionId(), r.status(), r.referenceCode(), r.referenceNameZh(), r.referenceNameEn(), r.referenceSymbol(), r.referenceUcumNotApplicableReason())); }

    /** 更新类别名称。 @param id 类别 ID @param r 名称及版本 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在或冲突时抛出 */
    @PutMapping("/uom-categories/{id}") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<CategoryView> updateCategory(@PathVariable String id, @Valid @RequestBody NameVersionRequest r) { return Result.success(application.updateCategory(id, r.nameZh(), r.nameEn(), r.version())); }

    /** 查询类别及基准单位 ID。 @param id 类别 ID @return 统一详情 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在时抛出 */
    @GetMapping("/uom-categories/{id}") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<CategoryView> getCategory(@PathVariable String id) { return Result.success(application.getCategory(id)); }

    /** 按量纲和状态分页类别且无写副作用。 @param dimensionId 可选量纲 @param status 可选状态 @param pageNo 页码 @param pageSize 每页条数 @return 统一分页 */
    @GetMapping("/uom-categories") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<PageResult<CategoryView>> pageCategories(@RequestParam(required=false) String dimensionId, @RequestParam(required=false) String status, @RequestParam(defaultValue="1") @Positive long pageNo, @RequestParam(defaultValue="20") @Positive long pageSize) { return Result.success(application.pageCategories(dimensionId, status, pageNo, pageSize)); }

    /** 启用类别并恢复基准单位。 @param id 类别 ID @param r 版本请求 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 父级或冲突时抛出 */
    @PatchMapping("/uom-categories/{id}/enable") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<CategoryView> enableCategory(@PathVariable String id, @Valid @RequestBody VersionRequest r) { return Result.success(application.enableCategory(id, r.version())); }

    /** 停用类别并同步停用基准单位。 @param id 类别 ID @param r 版本请求 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 冲突时抛出 */
    @PatchMapping("/uom-categories/{id}/disable") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<CategoryView> disableCategory(@PathVariable String id, @Valid @RequestBody VersionRequest r) { return Result.success(application.disableCategory(id, r.version())); }

    /** 原子创建普通单位及规则 v1。 @param r 创建协议 @return 统一单位结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 校验或冲突时抛出 */
    @PostMapping("/uoms") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<UomView> createUom(@Valid @RequestBody CreateUomRequest r) { return Result.success(application.createUom(r.code(), r.nameZh(), r.nameEn(), r.symbol(), r.categoryId(), r.ucumNotApplicableReason(), r.status(), r.multiplier(), r.offset(), r.calculationPrecision(), r.roundingMode())); }

    /** 更新单位名称。 @param id 单位 ID @param r 名称及版本 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在或冲突时抛出 */
    @PutMapping("/uoms/{id}") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<UomView> updateUom(@PathVariable String id, @Valid @RequestBody NameVersionRequest r) { return Result.success(application.updateUom(id, r.nameZh(), r.nameEn(), r.version())); }

    /** 查询单位详情且无写副作用。 @param id 单位 ID @return 统一详情 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在时抛出 */
    @GetMapping("/uoms/{id}") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<UomView> getUom(@PathVariable String id) { return Result.success(application.getUom(id)); }

    /** 分页单位。 @param categoryId 可选类别 @param status 可选状态 @param referenceUnit 可选基准标记 @param pageNo 页码 @param pageSize 每页条数 @return 统一分页 */
    @GetMapping("/uoms") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<PageResult<UomView>> pageUoms(@RequestParam(required=false) String categoryId, @RequestParam(required=false) String status, @RequestParam(required=false) Boolean referenceUnit, @RequestParam(defaultValue="1") @Positive long pageNo, @RequestParam(defaultValue="20") @Positive long pageSize) { return Result.success(application.pageUoms(categoryId, status, referenceUnit, pageNo, pageSize)); }

    /** 启用普通单位。 @param id 单位 ID @param r 版本请求 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 父级、基准身份或冲突时抛出 */
    @PatchMapping("/uoms/{id}/enable") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<UomView> enableUom(@PathVariable String id, @Valid @RequestBody VersionRequest r) { return Result.success(application.enableUom(id, r.version())); }

    /** 停用普通单位。 @param id 单位 ID @param r 版本请求 @return 统一结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 基准身份或冲突时抛出 */
    @PatchMapping("/uoms/{id}/disable") @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<UomView> disableUom(@PathVariable String id, @Valid @RequestBody VersionRequest r) { return Result.success(application.disableUom(id, r.version())); }

    /** 发布新规则版本并停用旧版。 @param id 单位 ID @param r 新规则协议 @return 统一规则结果 @throws io.github.chrisshi.mom.mdm.application.MdmException 校验或并发冲突时抛出 */
    @PostMapping("/uoms/{id}/conversion-rules") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mdm:uom:write')")
    public Result<RuleView> publishRule(@PathVariable String id, @Valid @RequestBody PublishRuleRequest r) { return Result.success(application.publishRule(id, r.expectedCurrentVersion(), r.multiplier(), r.offset(), r.calculationPrecision(), r.roundingMode())); }

    /** 查询当前启用规则。 @param id 单位 ID @return 统一规则详情 @throws io.github.chrisshi.mom.mdm.application.MdmException 不存在时抛出 */
    @GetMapping("/uoms/{id}/conversion-rules/current") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<RuleView> getCurrentRule(@PathVariable String id) { return Result.success(application.getCurrentRule(id)); }

    /** 分页查询不可变规则历史。 @param id 单位 ID @param pageNo 页码 @param pageSize 每页条数 @return 统一分页 @throws io.github.chrisshi.mom.mdm.application.MdmException 单位不存在时抛出 */
    @GetMapping("/uoms/{id}/conversion-rules") @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<PageResult<RuleView>> pageRules(@PathVariable String id, @RequestParam(defaultValue="1") @Positive long pageNo, @RequestParam(defaultValue="20") @Positive long pageSize) { return Result.success(application.pageRules(id, pageNo, pageSize)); }

    /**
     * 量纲创建协议，七个指数按 T/L/M/I/Theta/N/J 顺序表达。
     *
     * @param code 唯一业务编码
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
     * @param timeExponent 时间指数
     * @param lengthExponent 长度指数
     * @param massExponent 质量指数
     * @param electricCurrentExponent 电流指数
     * @param temperatureExponent 温度指数
     * @param amountExponent 物质的量指数
     * @param luminousIntensityExponent 发光强度指数
     * @param status 初始生命周期状态
     */
    public record CreateDimensionRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=200) String nameZh,
            @Size(max=200) String nameEn, @NotNull Integer timeExponent, @NotNull Integer lengthExponent,
            @NotNull Integer massExponent, @NotNull Integer electricCurrentExponent, @NotNull Integer temperatureExponent,
            @NotNull Integer amountExponent, @NotNull Integer luminousIntensityExponent, @NotBlank String status) { }

    /**
     * 类别和基准单位原子创建协议；基准身份由服务端固定。
     *
     * @param code 类别唯一编码
     * @param nameZh 类别中文名称
     * @param nameEn 类别可选英文名称
     * @param dimensionId 所属启用量纲 ID
     * @param status 类别初始状态
     * @param referenceCode 基准单位唯一编码
     * @param referenceNameZh 基准单位中文名称
     * @param referenceNameEn 基准单位可选英文名称
     * @param referenceSymbol 基准单位显示符号
     * @param referenceUcumNotApplicableReason mom: 扩展编码的治理说明
     */
    public record CreateCategoryRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=200) String nameZh,
            @Size(max=200) String nameEn, @NotBlank @Size(max=19) String dimensionId, @NotBlank String status,
            @NotBlank @Size(max=64) String referenceCode, @NotBlank @Size(max=200) String referenceNameZh,
            @Size(max=200) String referenceNameEn, @NotBlank @Size(max=32) String referenceSymbol,
            @Size(max=500) String referenceUcumNotApplicableReason) { }

    /**
     * 普通单位和首个 AFFINE 规则的原子创建协议。
     *
     * @param code 单位唯一编码
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
     * @param symbol 显示符号
     * @param categoryId 唯一所属类别 ID
     * @param ucumNotApplicableReason mom: 扩展编码治理说明
     * @param status 初始状态
     * @param multiplier 到基准单位的正乘数
     * @param offset 乘法后的偏移量
     * @param calculationPrecision 计算有效数字精度
     * @param roundingMode BigDecimal 舍入模式
     */
    public record CreateUomRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=200) String nameZh,
            @Size(max=200) String nameEn, @NotBlank @Size(max=32) String symbol, @NotBlank @Size(max=19) String categoryId,
            @Size(max=500) String ucumNotApplicableReason, @NotBlank String status, @NotNull BigDecimal multiplier,
            @NotNull BigDecimal offset, @NotNull @Min(1) @Max(34) Integer calculationPrecision,
            @NotBlank String roundingMode) { }

    /**
     * 仅允许名称修正并携带乐观锁版本的更新协议。
     *
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
     * @param version 调用方读取到的乐观锁版本
     */
    public record NameVersionRequest(@NotBlank @Size(max=200) String nameZh, @Size(max=200) String nameEn, @NotNull @PositiveOrZero Long version) { }

    /**
     * 生命周期变更协议，仅携带调用方读取到的版本。
     *
     * @param version 调用方读取到的乐观锁版本
     */
    public record VersionRequest(@NotNull @PositiveOrZero Long version) { }

    /**
     * 新规则发布协议，expectedCurrentVersion 防止并发覆盖。
     *
     * @param expectedCurrentVersion 调用方看到的当前业务版本号
     * @param multiplier 新版本正乘数
     * @param offset 新版本偏移量
     * @param calculationPrecision 新版本有效数字精度
     * @param roundingMode 新版本舍入模式
     */
    public record PublishRuleRequest(@NotNull @Positive Integer expectedCurrentVersion, @NotNull BigDecimal multiplier,
            @NotNull BigDecimal offset, @NotNull @Min(1) @Max(34) Integer calculationPrecision,
            @NotBlank String roundingMode) { }
}
