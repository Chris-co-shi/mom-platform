package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialCategoryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * MaterialCategory 动态分类树的 Level 1 用例与本地事务边界。
 *
 * <p>调用方向保持 Controller → Application → Mapper。该类负责父级存在与启用校验、移动循环检测、
 * 简单生命周期、默认建议值和乐观并发，不引入通用树框架、Domain、Repository、Material 或 UOM。
 * 循环检测在当前分类规模下逐级读取父链并使用 visited 集合防止脏数据造成无限循环；数据库不可用时
 * 操作失败并由 Spring 本地事务回滚，不做缓存或降级成功。</p>
 *
 * <p>该类无共享可变状态，Spring 单例可并发调用；Code 最终唯一性由 PostgreSQL 约束兜底，更新冲突由
 * BaseEntity Version 识别。分类默认值只持久化建议，不会触发任何 Material 副作用。</p>
 */
@Component
public class MaterialCategoryApplication {
    private static final String RESOURCE_NAME = "MaterialCategory";

    private final MaterialCategoryMapper materialCategoryMapper;

    /**
     * 创建 MaterialCategory 用例入口。
     *
     * @param materialCategoryMapper 分类单表 Mapper
     */
    public MaterialCategoryApplication(MaterialCategoryMapper materialCategoryMapper) {
        this.materialCategoryMapper = materialCategoryMapper;
    }

    /**
     * 创建根分类或已启用父分类下的直接子分类。
     *
     * @param code 平台唯一业务编码，保留调用方大小写
     * @param nameZh 必填中文名称
     * @param nameEn 可选英文名称
     * @param parentId 可选父分类 ID；null 表示根分类
     * @param sort 同级显示排序值
     * @param defaultBatchManaged 可选批次管理默认建议
     * @param defaultShelfLifeDays 可选非负保质期天数默认建议
     * @param status ENABLED 或 DISABLED
     * @return 已持久化分类视图
     * @throws MdmException 输入非法、父级不存在、父级停用或 Code 冲突时抛出
     *
     * <p>该方法不是幂等操作；成功时插入一行，失败时事务回滚且不产生其他业务副作用。</p>
     */
    @Transactional
    public MaterialCategoryView createMaterialCategory(
            String code,
            String nameZh,
            String nameEn,
            String parentId,
            Integer sort,
            Boolean defaultBatchManaged,
            Integer defaultShelfLifeDays,
            String status) {
        String validatedParentId = validateParent(null, normalizeParentId(parentId));
        MaterialCategoryEntity entity = new MaterialCategoryEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        entity.setNameZh(MdmMasterDataRules.nameZh(nameZh));
        entity.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        entity.setParentId(validatedParentId);
        entity.setSort(requireSort(sort));
        entity.setDefaultBatchManaged(defaultBatchManaged);
        entity.setDefaultShelfLifeDays(validateShelfLifeDays(defaultShelfLifeDays));
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(entity);
        return toView(entity);
    }

    /**
     * 更新分类可变字段，并在父级变化时完成存在性、启用状态和循环校验。
     *
     * @param id 待更新分类 ID
     * @param nameZh 必填中文名称
     * @param nameEn 可选英文名称
     * @param parentId 新父分类 ID；null 表示移动为根分类
     * @param sort 同级显示排序值
     * @param defaultBatchManaged 可选批次管理默认建议
     * @param defaultShelfLifeDays 可选非负保质期天数默认建议
     * @param version 调用方读取到的非负乐观锁版本
     * @return 更新后的分类视图，ID 与 Code 保持不变
     * @throws MdmException 分类或父级不存在、父级停用、形成循环、输入非法或版本冲突时抛出
     *
     * <p>该方法不是无条件幂等操作；成功时更新一行并推进 Version，不修改已有 Material 或子分类。</p>
     */
    @Transactional
    public MaterialCategoryView updateMaterialCategory(
            String id,
            String nameZh,
            String nameEn,
            String parentId,
            Integer sort,
            Boolean defaultBatchManaged,
            Integer defaultShelfLifeDays,
            Long version) {
        MaterialCategoryEntity entity = requireCategory(id);
        requireVersion(entity.getVersion(), version);
        String normalizedParentId = normalizeParentId(parentId);
        if (!Objects.equals(entity.getParentId(), normalizedParentId)) {
            entity.setParentId(validateParent(entity.getId(), normalizedParentId));
        }
        entity.setNameZh(MdmMasterDataRules.nameZh(nameZh));
        entity.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        entity.setSort(requireSort(sort));
        entity.setDefaultBatchManaged(defaultBatchManaged);
        entity.setDefaultShelfLifeDays(validateShelfLifeDays(defaultShelfLifeDays));
        requireUpdated(materialCategoryMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 按技术 ID 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情视图
     * @throws MdmException ID 非法或分类不存在时抛出
     *
     * <p>该只读操作幂等且不产生持久化副作用；数据库不可用时直接失败。</p>
     */
    @Transactional(readOnly = true)
    public MaterialCategoryView getMaterialCategory(String id) {
        return toView(requireCategory(id));
    }

    /**
     * 按可选父级和状态过滤分类，并按 sort、code、id 稳定分页。
     *
     * @param parentId 可选父分类 ID；提供时仅查询其直接子节点
     * @param status 可选 ENABLED/DISABLED 状态
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页条数
     * @return 通过 PageAdapter 转换的统一分页结果
     * @throws MdmException 过滤条件非法时抛出
     *
     * <p>该只读操作幂等且不构建递归树，不产生数据库写副作用。</p>
     */
    @Transactional(readOnly = true)
    public PageResult<MaterialCategoryView> pageMaterialCategories(
            String parentId, String status, long pageNo, long pageSize) {
        Page<MaterialCategoryEntity> page = PageAdapter.toPage(new PageQuery<>(parentId, pageNo, pageSize));
        LambdaQueryWrapper<MaterialCategoryEntity> query = new LambdaQueryWrapper<>();
        if (parentId != null && !parentId.isBlank()) {
            query.eq(MaterialCategoryEntity::getParentId, MdmMasterDataRules.id(parentId, "parentId"));
        }
        if (status != null && !status.isBlank()) {
            query.eq(MaterialCategoryEntity::getStatus, MdmMasterDataRules.status(status));
        }
        query.orderByAsc(MaterialCategoryEntity::getSort)
                .orderByAsc(MaterialCategoryEntity::getCode)
                .orderByAsc(MaterialCategoryEntity::getId);
        materialCategoryMapper.selectPage(page, query);
        return PageAdapter.toResult(page, MaterialCategoryApplication::toView);
    }

    /**
     * 启用分类；存在父级时要求父分类当前仍处于 ENABLED。
     *
     * @param id 分类 ID
     * @param version 调用方读取到的非负乐观锁版本
     * @return 启用后的分类视图
     * @throws MdmException 分类或父级不存在、父级停用、父链异常或版本冲突时抛出
     *
     * <p>重复启用在版本一致且父级合法时返回当前视图，不级联修改子分类或 Material。</p>
     */
    @Transactional
    public MaterialCategoryView enableMaterialCategory(String id, Long version) {
        return changeStatus(id, MdmMasterDataRules.ENABLED, version);
    }

    /**
     * 停用分类，不级联修改子分类或任何未来 Material。
     *
     * @param id 分类 ID
     * @param version 调用方读取到的非负乐观锁版本
     * @return 停用后的分类视图
     * @throws MdmException 分类不存在、ID/版本非法或版本冲突时抛出
     *
     * <p>重复停用在版本一致时返回当前视图，不产生额外持久化写入。</p>
     */
    @Transactional
    public MaterialCategoryView disableMaterialCategory(String id, Long version) {
        return changeStatus(id, MdmMasterDataRules.DISABLED, version);
    }

    /**
     * 执行简单状态变更；启用前即使当前已启用也重新验证父级，防止幂等短路绕过父级状态规则。
     */
    private MaterialCategoryView changeStatus(String id, String status, Long version) {
        MaterialCategoryEntity entity = requireCategory(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            validateParent(entity.getId(), entity.getParentId());
        }
        if (status.equals(entity.getStatus())) {
            return toView(entity);
        }
        entity.setStatus(status);
        requireUpdated(materialCategoryMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 校验候选父级和完整父链。
     *
     * <p>从候选父级逐级向上读取。遇到当前分类表示移动将形成循环；visited 重复表示数据库已存在脏循环，
     * 两种情况均拒绝。父级为空时直接返回，避免为根分类进行无意义查询。</p>
     */
    private String validateParent(String categoryId, String parentId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(categoryId)) {
            throw MdmException.invalidReference("MaterialCategory 不能将自己设为父分类");
        }

        MaterialCategoryEntity directParent = materialCategoryMapper.selectById(parentId);
        if (directParent == null) {
            throw MdmException.notFound("MaterialCategory parent");
        }
        MdmMasterDataRules.requireEnabled(directParent.getStatus(), "MaterialCategory parent");

        Set<String> visited = new HashSet<>();
        MaterialCategoryEntity current = directParent;
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw MdmException.invalidReference("MaterialCategory 现有父级链包含循环");
            }
            if (current.getId().equals(categoryId)) {
                throw MdmException.invalidReference("MaterialCategory 移动后不能形成循环");
            }
            String nextParentId = current.getParentId();
            if (nextParentId == null) {
                break;
            }
            current = materialCategoryMapper.selectById(nextParentId);
            if (current == null) {
                throw MdmException.invalidReference("MaterialCategory 现有父级链引用不存在");
            }
        }
        return directParent.getId();
    }

    /** 将 null 保留为根分类语义；非空值沿用统一 String ID 校验。 */
    private static String normalizeParentId(String parentId) {
        return parentId == null ? null : MdmMasterDataRules.id(parentId, "parentId");
    }

    /** 要求排序值显式提供，不对业务排序范围增加未确认限制。 */
    private static Integer requireSort(Integer sort) {
        if (sort == null) {
            throw validationFailed("sort 不能为空");
        }
        return sort;
    }

    /** 校验可空保质期默认建议；负数既在 Application 拒绝，也由数据库 Check 最终兜底。 */
    private static Integer validateShelfLifeDays(Integer days) {
        if (days != null && days < 0) {
            throw validationFailed("defaultShelfLifeDays 必须大于或等于 0");
        }
        return days;
    }

    /** 按 ID 加载逻辑未删除分类。 */
    private MaterialCategoryEntity requireCategory(String id) {
        MaterialCategoryEntity entity = materialCategoryMapper.selectById(MdmMasterDataRules.id(id, "categoryId"));
        if (entity == null) {
            throw MdmException.notFound(RESOURCE_NAME);
        }
        return entity;
    }

    /** 插入分类并将数据库唯一冲突转换为稳定的 MDM 409。 */
    private void insert(MaterialCategoryEntity entity) {
        try {
            materialCategoryMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict(RESOURCE_NAME);
        }
    }

    /** 将 affected rows 为零区分为资源不存在或乐观锁冲突。 */
    private void requireUpdated(int affected, String id) {
        if (affected == 1) {
            return;
        }
        if (materialCategoryMapper.selectById(id) == null) {
            throw MdmException.notFound(RESOURCE_NAME);
        }
        throw MdmException.versionConflict();
    }

    /** 创建统一的 MDM 输入校验异常，不泄露基础设施信息。 */
    private static MdmException validationFailed(String message) {
        return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", message);
    }

    /** 将持久化实体转换为不暴露逻辑删除等基础设施字段的 Application 视图。 */
    private static MaterialCategoryView toView(MaterialCategoryEntity entity) {
        return new MaterialCategoryView(
                entity.getId(), entity.getCode(), entity.getNameZh(), entity.getNameEn(), entity.getParentId(),
                entity.getSort(), entity.getDefaultBatchManaged(), entity.getDefaultShelfLifeDays(),
                entity.getStatus(), entity.getCreatedAt(), entity.getCreatedBy(), entity.getUpdatedAt(),
                entity.getUpdatedBy(), entity.getVersion());
    }
}
