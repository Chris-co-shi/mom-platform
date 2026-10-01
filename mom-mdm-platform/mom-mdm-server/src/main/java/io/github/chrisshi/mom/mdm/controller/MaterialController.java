package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.MaterialApplication;
import io.github.chrisshi.mom.mdm.application.MaterialApplication.ShelfLifeSource;
import io.github.chrisshi.mom.mdm.application.model.MaterialView;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.MaterialPageParams;
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

/**
 * Material 主数据的 HTTP 协议边界。
 *
 * <p>该 Controller 只负责请求绑定、Bean Validation、Result 包装和 Application 调用，不依赖 Mapper、
 * Entity 或事务实现。分类、单位链、叶子约束和并发规则全部由 Application 负责；数据库不可用时异常
 * 交给统一处理器，不伪造成功或暴露 SQL 信息。</p>
 */
@RestController
@RequestMapping("/api/mdm/materials")
@PreAuthorize("isAuthenticated()")
public class MaterialController {
    private final MaterialApplication application;

    /**
     * 注入 Material 用例入口。
     *
     * @param application Material Application
     */
    public MaterialController(MaterialApplication application) {
        this.application = application;
    }

    /**
     * 创建 Material。
     *
     * @param request 已通过协议字段校验的创建请求
     * @return 统一成功结果，内部包含已创建物料
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 业务或持久化校验失败时抛出
     *
     * <p>该端点不是幂等创建；成功返回 HTTP 201，失败不产生部分写入。</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<MaterialView> createMaterial(@Valid @RequestBody CreateMaterialRequest request) {
        return Result.success(application.createMaterial(
                request.code(), request.name(), request.categoryId(), request.baseUomId(),
                request.shelfLifeSource(), request.shelfLifeDays(), request.status()));
    }

    /**
     * 更新 Material 的可变字段，不接收 Code 或 Status。
     *
     * @param id Material String 技术主键
     * @param request 已通过协议字段校验的更新请求
     * @return 统一成功结果，内部包含更新后的物料
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 引用、输入或版本校验失败时抛出
     *
     * <p>成功推进 Version，不自动转换历史数量或修改下游事实。</p>
     */
    @PutMapping("/{id}")
    public Result<MaterialView> updateMaterial(
            @PathVariable String id, @Valid @RequestBody UpdateMaterialRequest request) {
        return Result.success(application.updateMaterial(
                id, request.name(), request.categoryId(), request.baseUomId(),
                request.shelfLifeDays(), request.version()));
    }

    /**
     * 查询 Material 详情。
     *
     * @param id Material String 技术主键
     * @return 统一成功结果，内部包含物料详情
     * @throws io.github.chrisshi.mom.mdm.application.MdmException ID 非法或资源不存在时抛出
     */
    @GetMapping("/{id}")
    public Result<MaterialView> getMaterial(@PathVariable String id) {
        return Result.success(application.getMaterial(id));
    }

    /**
     * 按组合条件分页查询 Material。
     *
     * @param pageQuery 包含过滤对象和分页信息的唯一请求体
     * @return 统一成功结果，分页元数据由 PageAdapter 转换
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 过滤条件非法时抛出
     */
    @PostMapping("/search")
    public Result<PageResult<MaterialView>> pageMaterials(
            @RequestBody PageQuery<MaterialPageParams> pageQuery) {
        return Result.success(application.pageMaterials(pageQuery));
    }

    /**
     * 启用 Material，并重新校验完整引用链。
     *
     * @param id Material String 技术主键
     * @param request 当前乐观锁版本
     * @return 启用后的物料
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 引用或版本校验失败时抛出
     */
    @PatchMapping("/{id}/enable")
    public Result<MaterialView> enableMaterial(
            @PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableMaterial(id, request.version()));
    }

    /**
     * 停用 Material，不产生级联副作用。
     *
     * @param id Material String 技术主键
     * @param request 当前乐观锁版本
     * @return 停用后的物料
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 资源或版本校验失败时抛出
     */
    @PatchMapping("/{id}/disable")
    public Result<MaterialView> disableMaterial(
            @PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableMaterial(id, request.version()));
    }

    /**
     * Material 创建协议。
     *
     * <p>该记录只属于 Controller 入站边界；CATEGORY_DEFAULT 不得同时提交 shelfLifeDays，EXPLICIT
     * 允许 shelfLifeDays 为 null。更深层引用和并发规则由 Application 处理。</p>
     *
     * @param code 平台全局唯一业务编码
     * @param name 必填业务名称
     * @param categoryId 唯一物料分类 ID
     * @param baseUomId 基础计量单位 ID
     * @param shelfLifeSource CATEGORY_DEFAULT 或 EXPLICIT
     * @param shelfLifeDays 显式来源时的可空非负天数
     * @param status ENABLED 或 DISABLED
     */
    public record CreateMaterialRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 19) String categoryId,
            @NotBlank @Size(max = 19) String baseUomId,
            @NotNull ShelfLifeSource shelfLifeSource,
            @PositiveOrZero Integer shelfLifeDays,
            @NotBlank String status) {
    }

    /**
     * Material 更新协议，不包含不可普通修改的 Code 与生命周期 Status。
     *
     * @param name 必填业务名称
     * @param categoryId 新的唯一分类 ID
     * @param baseUomId 新的基础计量单位 ID
     * @param shelfLifeDays 可空非负天数；null 表示清空
     * @param version 调用方读取到的乐观锁版本
     */
    public record UpdateMaterialRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 19) String categoryId,
            @NotBlank @Size(max = 19) String baseUomId,
            @PositiveOrZero Integer shelfLifeDays,
            @NotNull @PositiveOrZero Long version) {
    }

    /**
     * Material 启停协议，只携带调用方读取到的乐观锁版本。
     *
     * @param version 非负乐观锁版本
     */
    public record VersionRequest(@NotNull @PositiveOrZero Long version) {
    }
}
