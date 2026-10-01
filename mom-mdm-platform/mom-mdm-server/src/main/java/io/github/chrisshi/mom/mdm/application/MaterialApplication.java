package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.model.MaterialView;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.MaterialPageParams;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.DimensionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * Material 主数据的 Level 1 用例与本地事务边界。
 *
 * <p>调用方向保持 Controller → Application → Mapper。该类负责唯一分类、叶子分类、完整启用单位链、
 * 保质期取值、生命周期和乐观并发，不引入包装、批次、库存、动态属性或 Repository 抽象。创建、修改
 * 和启用会按 Dimension → UOM Category → UOM 的稳定顺序加行锁，并锁定 MaterialCategory，和父数据停用
 * 操作串行化；数据库不可用或锁等待失败时 fail-closed，由 Spring 本地事务整体回滚。</p>
 *
 * <p>类型没有共享可变状态，Spring 单例可并发使用。Code 最终唯一性和字段范围由 PostgreSQL 约束兜底；
 * 更新及启停通过 BaseEntity Version 防止丢失更新。</p>
 */
@Component
public class MaterialApplication {
    private static final String RESOURCE_NAME = "Material";

    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper materialCategoryMapper;
    private final UomMapper uomMapper;
    private final UomCategoryMapper uomCategoryMapper;
    private final DimensionMapper dimensionMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入 Material 单表 Mapper、引用主数据 Mapper 和统一分页适配器。
     *
     * @param materialMapper Material 单表 Mapper
     * @param materialCategoryMapper MaterialCategory 单表 Mapper
     * @param uomMapper UOM 单表 Mapper
     * @param uomCategoryMapper UOM Category 单表 Mapper
     * @param dimensionMapper Dimension 单表 Mapper
     * @param pageAdapter 配置化分页适配器
     */
    public MaterialApplication(
            MaterialMapper materialMapper,
            MaterialCategoryMapper materialCategoryMapper,
            UomMapper uomMapper,
            UomCategoryMapper uomCategoryMapper,
            DimensionMapper dimensionMapper,
            PageAdapter pageAdapter) {
        this.materialMapper = materialMapper;
        this.materialCategoryMapper = materialCategoryMapper;
        this.uomMapper = uomMapper;
        this.uomCategoryMapper = uomCategoryMapper;
        this.dimensionMapper = dimensionMapper;
        this.pageAdapter = pageAdapter;
    }

    /**
     * 创建物料并固化本次选择得到的保质期天数。
     *
     * @param code 平台全局唯一业务编码
     * @param name 必填业务名称
     * @param categoryId 已启用叶子物料分类 ID
     * @param baseUomId 已启用且父链完整启用的基础单位 ID
     * @param shelfLifeSource 保质期取值来源
     * @param shelfLifeDays 显式来源时使用的可空非负天数
     * @param status 初始 ENABLED 或 DISABLED 状态
     * @return 已持久化物料视图
     * @throws MdmException 输入、引用、叶子规则或唯一性校验失败时抛出
     *
     * <p>该操作不是幂等创建；成功插入一行，不修改分类或单位。任一校验或写入失败时事务整体回滚。</p>
     */
    @Transactional
    public MaterialView createMaterial(
            String code,
            String name,
            String categoryId,
            String baseUomId,
            ShelfLifeSource shelfLifeSource,
            Integer shelfLifeDays,
            String status) {
        MaterialCategoryEntity category = lockEnabledLeafCategory(categoryId);
        UomEntity baseUom = lockEnabledUomChain(baseUomId);

        MaterialEntity entity = new MaterialEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        entity.setName(MdmMasterDataRules.name(name));
        entity.setCategoryId(category.getId());
        entity.setBaseUomId(baseUom.getId());
        entity.setShelfLifeDays(resolveShelfLife(category, shelfLifeSource, shelfLifeDays));
        entity.setStatus(MdmMasterDataRules.status(status));
        try {
            materialMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict(RESOURCE_NAME);
        }
        return toView(entity);
    }

    /**
     * 修正物料允许变化的名称、分类、基础单位和保质期。
     *
     * @param id 物料 ID
     * @param name 必填业务名称
     * @param categoryId 新的已启用叶子分类 ID
     * @param baseUomId 新的已启用基础单位 ID
     * @param shelfLifeDays 可空非负保质期天数；null 表示明确清空
     * @param version 调用方读取到的非负乐观锁版本
     * @return 更新后的物料视图，ID 与 Code 保持不变
     * @throws MdmException 资源、引用、叶子规则、输入或版本校验失败时抛出
     *
     * <p>成功只更新当前物料且推进 Version，不回写历史业务事实，也不自动执行单位换算。</p>
     */
    @Transactional
    public MaterialView updateMaterial(
            String id,
            String name,
            String categoryId,
            String baseUomId,
            Integer shelfLifeDays,
            Long version) {
        MaterialEntity entity = requireMaterialForUpdate(id);
        requireVersion(entity.getVersion(), version);
        MaterialCategoryEntity category = lockEnabledLeafCategory(categoryId);
        UomEntity baseUom = lockEnabledUomChain(baseUomId);

        entity.setName(MdmMasterDataRules.name(name));
        entity.setCategoryId(category.getId());
        entity.setBaseUomId(baseUom.getId());
        entity.setShelfLifeDays(validateShelfLifeDays(shelfLifeDays));
        requireUpdated(materialMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 按技术 ID 查询物料详情。
     *
     * @param id 物料 ID
     * @return 物料详情
     * @throws MdmException ID 非法或物料不存在时抛出
     *
     * <p>该只读操作幂等且无持久化副作用。</p>
     */
    @Transactional(readOnly = true)
    public MaterialView getMaterial(String id) {
        return toView(requireMaterial(id));
    }

    /**
     * 按物料编码、关键字、直接分类、基础单位和状态组合分页。
     *
     * @param pageQuery 包含过滤对象和分页信息的唯一业务入参
     * @return 复用 PageAdapter 转换的统一分页结果
     * @throws MdmException 过滤 ID、Code 或状态非法时抛出
     *
     * <p>关键字对 Code 与 Name 使用包含匹配；V1 不递归展开分类子树且不创建额外读模型。</p>
     */
    @Transactional(readOnly = true)
    public PageResult<MaterialView> pageMaterials(PageQuery<MaterialPageParams> pageQuery) {
        MaterialPageParams params = pageQuery.params();
        Page<MaterialEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<MaterialEntity> query = new LambdaQueryWrapper<>();
        if (hasText(params.code())) {
            query.eq(MaterialEntity::getCode, MdmMasterDataRules.code(params.code()));
        }
        if (hasText(params.keyword())) {
            String keyword = params.keyword().strip();
            if (keyword.length() > 200) {
                throw validationFailed("keyword 长度不能超过 200");
            }
            query.and(group -> group.like(MaterialEntity::getCode, keyword)
                    .or().like(MaterialEntity::getName, keyword));
        }
        if (hasText(params.categoryId())) {
            query.eq(MaterialEntity::getCategoryId,
                    MdmMasterDataRules.id(params.categoryId(), "categoryId"));
        }
        if (hasText(params.baseUomId())) {
            query.eq(MaterialEntity::getBaseUomId,
                    MdmMasterDataRules.id(params.baseUomId(), "baseUomId"));
        }
        if (hasText(params.status())) {
            query.eq(MaterialEntity::getStatus, MdmMasterDataRules.status(params.status()));
        }
        query.orderByAsc(MaterialEntity::getCode).orderByAsc(MaterialEntity::getId);
        materialMapper.selectPage(page, query);
        return pageAdapter.toResult(page, MaterialApplication::toView);
    }

    /**
     * 启用物料并重新验证分类与完整单位链。
     *
     * @param id 物料 ID
     * @param version 调用方读取到的非负乐观锁版本
     * @return 启用后的物料视图
     * @throws MdmException 资源、父级、叶子规则或版本校验失败时抛出
     *
     * <p>重复启用仍会复核引用链；校验成功且状态未变化时不产生写入。</p>
     */
    @Transactional
    public MaterialView enableMaterial(String id, Long version) {
        MaterialEntity entity = requireMaterialForUpdate(id);
        requireVersion(entity.getVersion(), version);
        lockEnabledLeafCategory(entity.getCategoryId());
        lockEnabledUomChain(entity.getBaseUomId());
        if (MdmMasterDataRules.ENABLED.equals(entity.getStatus())) {
            return toView(entity);
        }
        entity.setStatus(MdmMasterDataRules.ENABLED);
        requireUpdated(materialMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 显式停用物料，不级联修改分类、单位或未来业务事实。
     *
     * @param id 物料 ID
     * @param version 调用方读取到的非负乐观锁版本
     * @return 停用后的物料视图
     * @throws MdmException 物料不存在、版本非法或发生并发冲突时抛出
     *
     * <p>重复停用且版本一致时不产生额外写入。</p>
     */
    @Transactional
    public MaterialView disableMaterial(String id, Long version) {
        MaterialEntity entity = requireMaterialForUpdate(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.DISABLED.equals(entity.getStatus())) {
            return toView(entity);
        }
        entity.setStatus(MdmMasterDataRules.DISABLED);
        requireUpdated(materialMapper.updateById(entity), entity.getId());
        return toView(entity);
    }

    /**
     * 锁定并校验启用叶子分类。父分类锁使并发新增子分类必须等待，从而避免检查后插入竞态。
     */
    private MaterialCategoryEntity lockEnabledLeafCategory(String id) {
        String validatedId = MdmMasterDataRules.id(id, "categoryId");
        MaterialCategoryEntity category = materialCategoryMapper.selectByIdForUpdate(validatedId);
        if (category == null) {
            throw MdmException.notFound("MaterialCategory");
        }
        MdmMasterDataRules.requireEnabled(category.getStatus(), "MaterialCategory");
        long children = materialCategoryMapper.selectCount(new LambdaQueryWrapper<MaterialCategoryEntity>()
                .eq(MaterialCategoryEntity::getParentId, category.getId()));
        if (children > 0) {
            throw MdmException.categoryNotLeaf();
        }
        return category;
    }

    /**
     * 按稳定顺序锁定并复核 Dimension、UOM Category 与 UOM 的启用链。
     *
     * <p>先做无锁读取只为定位不可变父链 ID，随后依次获取父到子的行锁并重新校验关系与状态；因此并发
     * 停用要么先完成并使本操作失败，要么等待本事务提交后看到新 Material 引用。</p>
     */
    private UomEntity lockEnabledUomChain(String id) {
        String validatedId = MdmMasterDataRules.id(id, "baseUomId");
        UomEntity snapshotUom = uomMapper.selectById(validatedId);
        if (snapshotUom == null) {
            throw MdmException.notFound("Uom");
        }
        UomCategoryEntity snapshotCategory = uomCategoryMapper.selectById(snapshotUom.getCategoryId());
        if (snapshotCategory == null) {
            throw MdmException.notFound("UomCategory");
        }

        DimensionEntity dimension = dimensionMapper.selectByIdForUpdate(snapshotCategory.getDimensionId());
        UomCategoryEntity category = uomCategoryMapper.selectByIdForUpdate(snapshotCategory.getId());
        UomEntity uom = uomMapper.selectByIdForUpdate(validatedId);
        if (dimension == null || category == null || uom == null) {
            throw MdmException.invalidReference("基础单位引用链不存在");
        }
        if (!uom.getCategoryId().equals(category.getId())
                || !category.getDimensionId().equals(dimension.getId())) {
            throw MdmException.invalidReference("基础单位引用链在并发修改中发生变化");
        }
        MdmMasterDataRules.requireEnabled(dimension.getStatus(), "Dimension");
        MdmMasterDataRules.requireEnabled(category.getStatus(), "UomCategory");
        MdmMasterDataRules.requireEnabled(uom.getStatus(), "Uom");
        return uom;
    }

    /** 根据创建请求选择并校验最终持久化的保质期天数。 */
    private static Integer resolveShelfLife(
            MaterialCategoryEntity category, ShelfLifeSource source, Integer explicitDays) {
        if (source == null) {
            throw validationFailed("shelfLifeSource 不能为空");
        }
        if (source == ShelfLifeSource.CATEGORY_DEFAULT) {
            if (explicitDays != null) {
                throw validationFailed("CATEGORY_DEFAULT 不能同时提交 shelfLifeDays");
            }
            return category.getDefaultShelfLifeDays();
        }
        return validateShelfLifeDays(explicitDays);
    }

    /** 校验可空保质期天数；负数由 Application 和数据库 Check 双重拒绝。 */
    private static Integer validateShelfLifeDays(Integer days) {
        if (days != null && days < 0) {
            throw validationFailed("shelfLifeDays 必须大于或等于 0");
        }
        return days;
    }

    /** 按 ID 读取有效物料。 */
    private MaterialEntity requireMaterial(String id) {
        MaterialEntity entity = materialMapper.selectById(MdmMasterDataRules.id(id, "materialId"));
        if (entity == null) {
            throw MdmException.notFound(RESOURCE_NAME);
        }
        return entity;
    }

    /** 按 ID 锁定有效物料，保证同一行的修改和启停串行执行。 */
    private MaterialEntity requireMaterialForUpdate(String id) {
        MaterialEntity entity = materialMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "materialId"));
        if (entity == null) {
            throw MdmException.notFound(RESOURCE_NAME);
        }
        return entity;
    }

    /** 将零 affected rows 区分为资源不存在或乐观锁冲突。 */
    private void requireUpdated(int affected, String id) {
        if (affected == 1) {
            return;
        }
        if (materialMapper.selectById(id) == null) {
            throw MdmException.notFound(RESOURCE_NAME);
        }
        throw MdmException.versionConflict();
    }

    /** 创建统一输入校验异常。 */
    private static MdmException validationFailed(String message) {
        return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", message);
    }

    /** 判断可选文本是否具有非空白内容。 */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 将实体转换为不暴露逻辑删除字段的 Application 视图。 */
    private static MaterialView toView(MaterialEntity entity) {
        return new MaterialView(
                entity.getId(), entity.getCode(), entity.getName(), entity.getCategoryId(), entity.getBaseUomId(),
                entity.getShelfLifeDays(), entity.getStatus(), entity.getCreatedAt(), entity.getCreatedBy(),
                entity.getUpdatedAt(), entity.getUpdatedBy(), entity.getVersion());
    }

    /**
     * Material 创建时的保质期取值方式。
     *
     * <p>该枚举是稳定用例协议，不是动态主数据：CATEGORY_DEFAULT 在创建事务内复制分类当前默认值，
     * EXPLICIT 使用请求显式值并允许 null。Material 只保存最终结果，分类后续变化不会反向传播。</p>
     */
    public enum ShelfLifeSource {
        CATEGORY_DEFAULT,
        EXPLICIT
    }
}
