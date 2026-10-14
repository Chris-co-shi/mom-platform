package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.model.MaterialCategoryView;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.MaterialCategoryPageParams;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * MaterialCategory 动态分类树的 Level 1 用例与本地事务边界。
 *
 * <p>调用方向保持 Controller → Application → Mapper。该类负责父级状态、移动循环、叶子分类引用保护、
 * 级联停用和乐观并发，不引入通用树框架或 Domain。创建或移动子级会锁定候选父级并拒绝已有 Material
 * 的父级；级联停用按稳定顺序锁定子树并在写入前检查全部 Material 引用，任何失败由同一本地事务回滚。</p>
 *
 * <p>循环检测使用 visited 集合防止脏父链无限遍历；级联单次最多处理 1000 个分类，避免无界事务持锁。
 * 数据库不可用或锁等待失败时 fail-closed，不做缓存或降级成功。</p>
 */
@Component
public class MaterialCategoryApplication {
    private static final String RESOURCE_NAME = "MaterialCategory";
    private static final int MAX_CASCADE_CATEGORIES = 1000;

    private final MaterialCategoryMapper materialCategoryMapper;
    private final MaterialMapper materialMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入分类、物料引用保护 Mapper 与统一分页适配器。
     *
     * @param materialCategoryMapper 分类单表 Mapper
     * @param materialMapper Material 引用保护 Mapper
     * @param pageAdapter 配置化分页适配器
     */
    public MaterialCategoryApplication(MaterialCategoryMapper materialCategoryMapper,
                                       MaterialMapper materialMapper, PageAdapter pageAdapter) {
        this.materialCategoryMapper = materialCategoryMapper;
        this.materialMapper = materialMapper;
        this.pageAdapter = pageAdapter;
    }

    /**
     * 创建根分类或已启用、未被物料引用的父分类下的直接子分类。
     *
     * @param code 平台唯一业务编码
     * @param name 必填业务名称
     * @param parentId 可选父分类 ID；null 表示根分类
     * @param sort 同级显示排序值
     * @param defaultShelfLifeDays 可选非负保质期天数默认建议
     * @param status ENABLED 或 DISABLED
     * @return 已持久化分类视图
     * @throws MdmException 输入、父级、引用或唯一性校验失败时抛出
     *
     * <p>该方法不是幂等操作；成功插入一行，不修改父分类或 Material。</p>
     */
    @Transactional
    public MaterialCategoryView createMaterialCategory(String code, String name, String parentId, Integer sort,
                                                       Integer defaultShelfLifeDays, String status) {
        String validatedParentId = validateParent(null, normalizeParentId(parentId), true);
        MaterialCategoryEntity entity = new MaterialCategoryEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        entity.setName(MdmMasterDataRules.name(name));
        entity.setParentId(validatedParentId);
        entity.setSort(requireSort(sort));
        entity.setDefaultShelfLifeDays(validateShelfLifeDays(defaultShelfLifeDays));
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(entity);
        return toView(entity);
    }

    /**
     * 更新分类名称、父级、排序和默认保质期，不接收 Code 或 Status。
     *
     * @param id 分类 ID
     * @param name 必填业务名称
     * @param parentId 新父分类 ID；null 表示移动为根分类
     * @param sort 同级显示排序值
     * @param defaultShelfLifeDays 可空非负保质期默认建议
     * @param version 调用方读取到的非负乐观锁版本
     * @return 更新后的分类视图
     * @throws MdmException 资源、循环、引用、输入或版本校验失败时抛出
     *
     * <p>移动到新父级时锁定候选父级；成功只更新当前分类，不级联修改后代或 Material。</p>
     */
    @Transactional
    public MaterialCategoryView updateMaterialCategory(String id, String name, String parentId, Integer sort,
                                                       Integer defaultShelfLifeDays, Long version) {
        MaterialCategoryEntity entity = requireCategoryForUpdate(id);
        requireVersion(entity.getVersion(), version);
        String normalizedParentId = normalizeParentId(parentId);
        if (!Objects.equals(entity.getParentId(), normalizedParentId)) {
            entity.setParentId(validateParent(entity.getId(), normalizedParentId, true));
        }
        entity.setName(MdmMasterDataRules.name(name));
        entity.setSort(requireSort(sort));
        entity.setDefaultShelfLifeDays(validateShelfLifeDays(defaultShelfLifeDays));
        requireUpdated(materialCategoryMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 按技术 ID 查询分类详情。
     * @param id 分类 ID
     * @return 分类详情视图
     * @throws MdmException ID 非法或分类不存在时抛出
     * <p>该操作只读、幂等且无持久化副作用。</p>
     */
    @Transactional(readOnly = true)
    public MaterialCategoryView getMaterialCategory(String id) {
        return toView(requireCategory(id));
    }

    /**
     * 按直接父级和状态稳定分页分类。
     *
     * @param pageQuery 包含过滤对象和分页信息的唯一业务入参
     * @return 复用 PageAdapter 转换的统一分页结果
     * @throws MdmException 过滤条件非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<MaterialCategoryView> pageMaterialCategories(PageQuery<MaterialCategoryPageParams> pageQuery) {
        MaterialCategoryPageParams params = pageQuery.params();
        Page<MaterialCategoryEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<MaterialCategoryEntity> query = new LambdaQueryWrapper<>();
        if (hasText(params.parentId())) {
            query.eq(MaterialCategoryEntity::getParentId, MdmMasterDataRules.id(params.parentId(), "parentId"));
        }
        if (hasText(params.status())) {
            query.eq(MaterialCategoryEntity::getStatus, MdmMasterDataRules.status(params.status()));
        }
        query.orderByAsc(MaterialCategoryEntity::getSort)
                .orderByAsc(MaterialCategoryEntity::getCode)
                .orderByAsc(MaterialCategoryEntity::getId);
        materialCategoryMapper.selectPage(page, query);
        return pageAdapter.toResult(page, MaterialCategoryApplication::toView);
    }

    /**
     * 启用单个分类；不级联启用后代。
     *
     * @param id 分类 ID
     * @param version 调用方读取到的非负乐观锁版本
     * @return 启用后的分类视图
     * @throws MdmException 父级、资源或版本校验失败时抛出
     */
    @Transactional
    public MaterialCategoryView enableMaterialCategory(String id, Long version) {
        MaterialCategoryEntity entity = requireCategoryForUpdate(id);
        requireVersion(entity.getVersion(), version);
        validateParent(entity.getId(), entity.getParentId(), false);
        if (MdmMasterDataRules.ENABLED.equals(entity.getStatus())) {
            return toView(entity);
        }
        entity.setStatus(MdmMasterDataRules.ENABLED);
        requireUpdated(materialCategoryMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 原子停用分类及其全部后代。
     *
     * @param id 级联根分类 ID
     * @param version 根分类当前乐观锁版本
     * @return 停用后的根分类视图
     * @throws MdmException 子树过大、任一节点被 Material 引用、资源或版本校验失败时抛出
     *
     * <p>先完成全量引用检查再写入；任何节点更新失败时整笔回滚，不会形成部分停用。</p>
     */
    @Transactional
    public MaterialCategoryView disableMaterialCategory(String id, Long version) {
        String validatedId = MdmMasterDataRules.id(id, "categoryId");
        List<MaterialCategoryEntity> subtree = materialCategoryMapper.selectSubtreeForUpdate(
                validatedId, MAX_CASCADE_CATEGORIES + 1);
        if (subtree.isEmpty()) throw MdmException.notFound(RESOURCE_NAME);
        if (subtree.size() > MAX_CASCADE_CATEGORIES) {
            throw MdmException.categoryCascadeTooLarge(MAX_CASCADE_CATEGORIES);
        }
        MaterialCategoryEntity root = subtree.stream().filter(item -> item.getId().equals(validatedId))
                .findFirst().orElseThrow(() -> MdmException.notFound(RESOURCE_NAME));
        requireVersion(root.getVersion(), version);
        List<String> categoryIds = subtree.stream().map(MaterialCategoryEntity::getId).toList();
        if (materialMapper.existsByCategoryIds(categoryIds)) {
            throw MdmException.resourceReferenced(RESOURCE_NAME);
        }
        for (MaterialCategoryEntity category : subtree) {
            if (!MdmMasterDataRules.DISABLED.equals(category.getStatus())) {
                category.setStatus(MdmMasterDataRules.DISABLED);
                requireUpdated(materialCategoryMapper.updateById(category), category.getId());
            }
        }
        return toView(root);
    }

    /** 校验候选父级与完整父链；新建父子关系时同时拒绝被 Material 使用的父级。 */
    private String validateParent(String categoryId, String parentId, boolean rejectMaterialReference) {
        if (parentId == null) return null;
        if (parentId.equals(categoryId)) {
            throw MdmException.invalidReference();
        }
        MaterialCategoryEntity directParent = materialCategoryMapper.selectByIdForUpdate(parentId);
        if (directParent == null) throw MdmException.notFound("MaterialCategory parent");
        MdmMasterDataRules.requireEnabled(directParent.getStatus(), "MaterialCategory parent");
        if (rejectMaterialReference && materialMapper.existsByCategoryIds(List.of(directParent.getId()))) {
            throw MdmException.resourceReferenced("MaterialCategory parent");
        }
        Set<String> visited = new HashSet<>();
        MaterialCategoryEntity current = directParent;
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw MdmException.invalidReference();
            }
            if (current.getId().equals(categoryId)) {
                throw MdmException.invalidReference();
            }
            if (current.getParentId() == null) break;
            current = materialCategoryMapper.selectById(current.getParentId());
            if (current == null) {
                throw MdmException.invalidReference();
            }
        }
        return directParent.getId();
    }

    /** 将 null 保留为根分类语义；非空值沿用统一 String ID 校验。 */
    private static String normalizeParentId(String parentId) {
        return parentId == null ? null : MdmMasterDataRules.id(parentId, "parentId");
    }

    /** 要求排序值显式提供，不增加未确认的正负范围限制。 */
    private static Integer requireSort(Integer sort) {
        if (sort == null) throw validationFailed("sort 不能为空");
        return sort;
    }

    /** 校验可空保质期默认建议。 */
    private static Integer validateShelfLifeDays(Integer days) {
        if (days != null && days < 0) throw validationFailed("defaultShelfLifeDays 必须大于或等于 0");
        return days;
    }

    /** 按 ID 读取有效分类。 */
    private MaterialCategoryEntity requireCategory(String id) {
        MaterialCategoryEntity entity = materialCategoryMapper.selectById(MdmMasterDataRules.id(id, "categoryId"));
        if (entity == null) throw MdmException.notFound(RESOURCE_NAME);
        return entity;
    }

    /** 按 ID 锁定有效分类，保证单行更新与并发父子关系操作串行化。 */
    private MaterialCategoryEntity requireCategoryForUpdate(String id) {
        MaterialCategoryEntity entity = materialCategoryMapper.selectByIdForUpdate(
                MdmMasterDataRules.id(id, "categoryId"));
        if (entity == null) throw MdmException.notFound(RESOURCE_NAME);
        return entity;
    }

    /** 插入分类并将唯一冲突转换为稳定业务异常。 */
    private void insert(MaterialCategoryEntity entity) {
        try { materialCategoryMapper.insert(entity); }
        catch (DuplicateKeyException exception) { throw MdmException.codeConflict(RESOURCE_NAME); }
    }

    /** 将零 affected rows 区分为资源不存在或乐观锁冲突。 */
    private void requireUpdated(int affected, String id) {
        if (affected == 1) return;
        if (materialCategoryMapper.selectById(id) == null) throw MdmException.notFound(RESOURCE_NAME);
        throw MdmException.versionConflict();
    }

    /** 创建统一输入校验异常。 */
    private static MdmException validationFailed(String message) {
        return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", "mdm.error.validation_failed");
    }

    /** 判断可选文本是否具有非空白内容。 */
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    /** 将实体转换为不暴露逻辑删除字段的 Application 视图。 */
    private static MaterialCategoryView toView(MaterialCategoryEntity entity) {
        return new MaterialCategoryView(entity.getId(), entity.getCode(), entity.getName(), entity.getParentId(),
                entity.getSort(), entity.getDefaultShelfLifeDays(), entity.getStatus(), entity.getCreatedAt(),
                entity.getCreatedBy(), entity.getUpdatedAt(), entity.getUpdatedBy(), entity.getVersion());
    }
}
