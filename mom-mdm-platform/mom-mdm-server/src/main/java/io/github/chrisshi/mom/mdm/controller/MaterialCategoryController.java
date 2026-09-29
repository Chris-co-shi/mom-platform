package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.MaterialCategoryApplication;
import io.github.chrisshi.mom.mdm.application.MaterialCategoryView;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * MaterialCategory 的 HTTP 协议边界。
 *
 * <p>该 Controller 只执行参数绑定、Bean Validation、Result 包装和 Application 调用，不依赖 Mapper、
 * Entity 或事务实现。所有端点沿用 MDM 已有认证基线；树完整性、循环检测、父级状态和乐观并发全部由
 * Application 负责。数据库不可用或事务失败时不伪造成功，也不泄露 SQL 与约束细节。</p>
 */
@RestController
@RequestMapping("/api/mdm/material-categories")
@PreAuthorize("isAuthenticated()")
public class MaterialCategoryController {
    private final MaterialCategoryApplication application;

    /**
     * 注入物料分类用例入口。
     *
     * @param application MaterialCategory Application
     */
    public MaterialCategoryController(MaterialCategoryApplication application) {
        this.application = application;
    }

    /**
     * 创建根分类或子分类。
     *
     * @param request 已通过协议字段校验的创建请求
     * @return 统一成功结果，内部包含已创建分类
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 业务校验或持久化冲突时抛出并由统一 Advice 映射
     *
     * <p>该端点不是幂等创建；成功返回 HTTP 201，失败不产生部分写入。</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<MaterialCategoryView> createMaterialCategory(
            @Valid @RequestBody CreateMaterialCategoryRequest request) {
        return Result.success(application.createMaterialCategory(
                request.code(), request.nameZh(), request.nameEn(), request.parentId(), request.sort(),
                request.defaultBatchManaged(), request.defaultShelfLifeDays(), request.status()));
    }

    /**
     * 更新分类名称、父级、排序、默认建议值和乐观锁版本，不接收 Code。
     *
     * @param id 分类 String 技术主键
     * @param request 已通过协议字段校验的更新请求
     * @return 统一成功结果，内部包含更新后的分类
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 资源、树规则或并发校验失败时抛出
     *
     * <p>成功会推进 Version；不会修改子分类或任何未来 Material。</p>
     */
    @PutMapping("/{id}")
    public Result<MaterialCategoryView> updateMaterialCategory(
            @PathVariable String id, @Valid @RequestBody UpdateMaterialCategoryRequest request) {
        return Result.success(application.updateMaterialCategory(
                id, request.nameZh(), request.nameEn(), request.parentId(), request.sort(),
                request.defaultBatchManaged(), request.defaultShelfLifeDays(), request.version()));
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 String 技术主键
     * @return 统一成功结果，内部包含分类详情
     * @throws io.github.chrisshi.mom.mdm.application.MdmException ID 非法或分类不存在时抛出
     *
     * <p>该端点幂等且无写副作用。</p>
     */
    @GetMapping("/{id}")
    public Result<MaterialCategoryView> getMaterialCategory(@PathVariable String id) {
        return Result.success(application.getMaterialCategory(id));
    }

    /**
     * 按父级和状态分页查询分类。
     *
     * @param parentId 可选父分类 ID；提供时只返回直接子节点
     * @param status 可选 ENABLED/DISABLED 状态
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页条数
     * @return 统一成功结果，分页内容由 PageAdapter 转换
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 过滤参数非法时抛出
     *
     * <p>该端点幂等且不递归构建整棵树。</p>
     */
    @GetMapping
    public Result<PageResult<MaterialCategoryView>> pageMaterialCategories(
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Positive long pageNo,
            @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pageMaterialCategories(parentId, status, pageNo, pageSize));
    }

    /**
     * 启用分类，存在父级时要求父分类已启用。
     *
     * @param id 分类 String 技术主键
     * @param request 当前乐观锁版本
     * @return 统一成功结果，内部包含启用后的分类
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 父级状态、资源或版本校验失败时抛出
     *
     * <p>不会级联启用子分类或 Material。</p>
     */
    @PatchMapping("/{id}/enable")
    public Result<MaterialCategoryView> enableMaterialCategory(
            @PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableMaterialCategory(id, request.version()));
    }

    /**
     * 停用分类，不级联修改子分类或 Material。
     *
     * @param id 分类 String 技术主键
     * @param request 当前乐观锁版本
     * @return 统一成功结果，内部包含停用后的分类
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 资源或版本校验失败时抛出
     *
     * <p>重复停用且版本一致时不产生额外写入。</p>
     */
    @PatchMapping("/{id}/disable")
    public Result<MaterialCategoryView> disableMaterialCategory(
            @PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableMaterialCategory(id, request.version()));
    }

    /**
     * MaterialCategory 创建协议；parentId 为 null 表示根分类，默认建议值均可为空。
     *
     * <p>该不可变记录只属于 Controller 入站边界，不包含事务、Entity 或树遍历逻辑；Bean Validation
     * 拒绝基础格式错误，父级与业务约束由 Application 再校验。</p>
     *
     * @param code 平台唯一业务编码
     * @param nameZh 必填中文名称
     * @param nameEn 可选英文名称
     * @param parentId 可选父分类 ID
     * @param sort 同级显示排序值
     * @param defaultBatchManaged 可选批次管理默认建议
     * @param defaultShelfLifeDays 可选非负保质期天数默认建议
     * @param status ENABLED 或 DISABLED
     */
    public record CreateMaterialCategoryRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String nameZh,
            @Size(max = 200) String nameEn,
            @Size(max = 19) String parentId,
            @NotNull Integer sort,
            Boolean defaultBatchManaged,
            @PositiveOrZero Integer defaultShelfLifeDays,
            @NotBlank String status) {
    }

    /**
     * MaterialCategory 更新协议；不包含 ID 与 Code，parentId 为 null 表示移动为根分类。
     *
     * <p>该不可变记录只表达允许普通修改的字段，避免客户端借更新入口改变业务身份；树移动、并发和
     * 父级状态仍由 Application 在本地事务中校验。</p>
     *
     * @param nameZh 必填中文名称
     * @param nameEn 可选英文名称
     * @param parentId 新父分类 ID；null 表示根分类
     * @param sort 同级显示排序值
     * @param defaultBatchManaged 可选批次管理默认建议
     * @param defaultShelfLifeDays 可选非负保质期天数默认建议
     * @param version 调用方读取到的乐观锁版本
     */
    public record UpdateMaterialCategoryRequest(
            @NotBlank @Size(max = 200) String nameZh,
            @Size(max = 200) String nameEn,
            @Size(max = 19) String parentId,
            @NotNull Integer sort,
            Boolean defaultBatchManaged,
            @PositiveOrZero Integer defaultShelfLifeDays,
            @NotNull Long version) {
    }

    /**
     * MaterialCategory 启停协议，只携带调用方读取到的乐观锁版本。
     *
     * <p>该不可变记录属于 Controller 边界，不允许客户端提交状态值，具体目标状态由 enable/disable
     * 路由决定；版本合法性和冲突语义由 Application 处理。</p>
     *
     * @param version 调用方读取到的乐观锁版本
     */
    public record VersionRequest(@NotNull Long version) {
    }
}
