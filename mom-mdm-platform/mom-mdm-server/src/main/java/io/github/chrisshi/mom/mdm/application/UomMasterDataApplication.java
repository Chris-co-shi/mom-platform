package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.MdmPageParams.DimensionPageParams;
import io.github.chrisshi.mom.mdm.application.MdmPageParams.UomCategoryPageParams;
import io.github.chrisshi.mom.mdm.application.MdmPageParams.UomConversionRulePageParams;
import io.github.chrisshi.mom.mdm.application.MdmPageParams.UomPageParams;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomConversionRuleEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.DimensionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomConversionRuleMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.github.chrisshi.mom.mdm.application.UomMasterDataViews.*;
import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * Dimension、UomCategory、Uom 与换算规则的 Level 1 用例和本地事务边界。
 *
 * <p>调用方向保持 Controller → Application → Mapper。该类负责引用状态、基准单位不变量、Code 治理、
 * 简单生命周期与不可变规则换版，不引入 Repository Port、动态图算法、缓存或消息。所有写入使用唯一
 * MDM DataSource 的本地事务；唯一约束与乐观锁处理并发，数据库不可用时 fail-closed。</p>
 */
@Component
public class UomMasterDataApplication {
    private final DimensionMapper dimensionMapper;
    private final UomCategoryMapper categoryMapper;
    private final UomMapper uomMapper;
    private final UomConversionRuleMapper ruleMapper;
    private final MaterialMapper materialMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入当前 bounded context 的四个单表 Mapper 与统一分页适配器。
     *
     * @param dimensionMapper Dimension 单表 Mapper
     * @param categoryMapper UOM Category 单表 Mapper
     * @param uomMapper UOM 单表 Mapper
     * @param ruleMapper 换算规则单表 Mapper
     * @param materialMapper Material 引用保护 Mapper
     * @param pageAdapter 配置化分页适配器
     */
    public UomMasterDataApplication(DimensionMapper dimensionMapper, UomCategoryMapper categoryMapper,
                                    UomMapper uomMapper, UomConversionRuleMapper ruleMapper,
                                    MaterialMapper materialMapper, PageAdapter pageAdapter) {
        this.dimensionMapper = dimensionMapper;
        this.categoryMapper = categoryMapper;
        this.uomMapper = uomMapper;
        this.ruleMapper = ruleMapper;
        this.materialMapper = materialMapper;
        this.pageAdapter = pageAdapter;
    }

    /**
     * 创建固定向量量纲；Code 与向量创建后不可普通修改。
     *
     * @param code 唯一业务编码
     * @param name 业务名称
     * @param t 时间指数
     * @param l 长度指数
     * @param m 质量指数
     * @param i 电流指数
     * @param theta 温度指数
     * @param n 物质的量指数
     * @param j 发光强度指数
     * @param status 初始状态
     * @return 已持久化视图；非幂等，重复 Code 或向量抛出冲突且事务无副作用
     * @throws MdmException 输入、唯一性或数据库约束校验失败时抛出
     */
    @Transactional
    public DimensionView createDimension(String code, String name, Integer t, Integer l,
                                         Integer m, Integer i, Integer theta, Integer n, Integer j, String status) {
        DimensionEntity entity = new DimensionEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        entity.setName(MdmMasterDataRules.name(name));
        entity.setTimeExponent(requiredExponent(t)); entity.setLengthExponent(requiredExponent(l));
        entity.setMassExponent(requiredExponent(m)); entity.setElectricCurrentExponent(requiredExponent(i));
        entity.setTemperatureExponent(requiredExponent(theta)); entity.setAmountExponent(requiredExponent(n));
        entity.setLuminousIntensityExponent(requiredExponent(j)); entity.setStatus(MdmMasterDataRules.status(status));
        try {
            dimensionMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw MdmException.dimensionConflict();
        }
        return dimensionView(entity);
    }

    /**
     * 更新量纲名称；不会修改 Code 或七维向量。
     * @param id 量纲 ID
     * @param name 业务名称
     * @param version 乐观锁版本
     * @return 更新后视图；成功推进版本且无跨表副作用
     * @throws MdmException 不存在、输入或版本冲突时抛出
     */
    @Transactional
    public DimensionView updateDimension(String id, String name, Long version) {
        DimensionEntity entity = requireDimension(id);
        requireVersion(entity.getVersion(), version);
        entity.setName(MdmMasterDataRules.name(name));
        requireUpdated(dimensionMapper.updateById(entity), () -> dimensionMapper.selectById(entity.getId()), "Dimension");
        return dimensionView(entity);
    }

    /**
     * 按 ID 查询量纲；只读、幂等且无副作用。
     * @param id 量纲 ID
     * @return 量纲视图
     * @throws MdmException ID 非法或资源不存在时抛出
     */
    @Transactional(readOnly = true)
    public DimensionView getDimension(String id) { return dimensionView(requireDimension(id)); }

    /**
     * 按可选状态稳定分页量纲，复用统一 PageAdapter。
     * @param pageQuery 包含可选状态和分页信息的唯一业务入参
     * @return 统一分页结果；只读且无副作用
     */
    @Transactional(readOnly = true)
    public PageResult<DimensionView> pageDimensions(PageQuery<DimensionPageParams> pageQuery) {
        DimensionPageParams params = pageQuery.params();
        Page<DimensionEntity> page = pageAdapter.toPage(pageQuery);
        var query = new LambdaQueryWrapper<DimensionEntity>();
        String status = params.status();
        if (hasText(status)) query.eq(DimensionEntity::getStatus, MdmMasterDataRules.status(status));
        query.orderByAsc(DimensionEntity::getCode).orderByAsc(DimensionEntity::getId);
        dimensionMapper.selectPage(page, query); return pageAdapter.toResult(page, UomMasterDataApplication::dimensionView);
    }

    /**
     * 启用量纲；版本一致时重复启用不写库。
     * @param id 量纲 ID
     * @param version 乐观锁版本
     * @return 当前量纲视图
     * @throws MdmException 不存在或版本冲突时抛出
     */
    @Transactional
    public DimensionView enableDimension(String id, Long version) { return changeDimensionStatus(id, MdmMasterDataRules.ENABLED, version); }

    /**
     * 停用量纲且不级联类别或单位。
     * @param id 量纲 ID
     * @param version 乐观锁版本
     * @return 当前量纲视图
     * @throws MdmException 不存在或版本冲突时抛出
     */
    @Transactional
    public DimensionView disableDimension(String id, Long version) { return changeDimensionStatus(id, MdmMasterDataRules.DISABLED, version); }

    /**
     * 原子创建类别及其唯一基准单位。
     *
     * @param code 类别编码
     * @param name 类别业务名称
     * @param dimensionId 启用量纲 ID
     * @param status 类别状态
     * @param referenceCode 基准单位编码
     * @param referenceName 基准单位业务名称
     * @param referenceSymbol 基准单位符号
     * @param referenceReason 企业扩展编码说明
     * @return 类别视图；非幂等，任一插入失败则两行都回滚
     * @throws MdmException 输入、父级、唯一性或数据库失败时抛出
     */
    @Transactional
    public CategoryView createCategory(String code, String name, String dimensionId, String status,
                                       String referenceCode, String referenceName,
                                       String referenceSymbol, String referenceReason) {
        DimensionEntity dimension = requireDimension(dimensionId);
        MdmMasterDataRules.requireEnabled(dimension.getStatus(), "Dimension");
        UomCategoryEntity category = new UomCategoryEntity();
        category.setCode(MdmMasterDataRules.code(code)); category.setName(MdmMasterDataRules.name(name));
        category.setDimensionId(dimension.getId());
        category.setStatus(MdmMasterDataRules.status(status));
        try { categoryMapper.insert(category); } catch (DuplicateKeyException e) { throw MdmException.codeConflict("UomCategory"); }
        UomEntity reference = newUom(referenceCode, referenceName, referenceSymbol,
                category.getId(), true, referenceReason, category.getStatus());
        insertUom(reference);
        return categoryView(category, reference.getId());
    }

    /**
     * 更新类别名称；量纲、Code 与基准单位身份不可修改。
     * @param id 类别 ID
     * @param name 业务名称
     * @param version 乐观锁版本
     * @return 更新后类别视图
     * @throws MdmException 不存在、输入或并发冲突时抛出
     */
    @Transactional
    public CategoryView updateCategory(String id, String name, Long version) {
        UomCategoryEntity category = requireCategory(id);
        requireVersion(category.getVersion(), version);
        category.setName(MdmMasterDataRules.name(name));
        requireUpdated(categoryMapper.updateById(category), () -> categoryMapper.selectById(category.getId()),
                "UomCategory");
        return categoryView(category, requireReference(category.getId()).getId());
    }

    /**
     * 按 ID 查询类别及其基准单位 ID。
     * @param id 类别 ID
     * @return 类别视图；只读且无副作用
     * @throws MdmException 类别或基准单位不存在时抛出
     */
    @Transactional(readOnly = true)
    public CategoryView getCategory(String id) { var c = requireCategory(id); return categoryView(c, requireReference(c.getId()).getId()); }

    /**
     * 按量纲和状态分页类别，结果复用统一 PageResult 转换。
     * @param pageQuery 包含可选量纲、状态和分页信息的唯一业务入参
     * @return 类别统一分页结果；只读且无副作用
     */
    @Transactional(readOnly = true)
    public PageResult<CategoryView> pageCategories(PageQuery<UomCategoryPageParams> pageQuery) {
        UomCategoryPageParams params = pageQuery.params();
        Page<UomCategoryEntity> page = pageAdapter.toPage(pageQuery);
        var query = new LambdaQueryWrapper<UomCategoryEntity>();
        String dimensionId = params.dimensionId();
        String status = params.status();
        if (hasText(dimensionId)) query.eq(UomCategoryEntity::getDimensionId, MdmMasterDataRules.id(dimensionId, "dimensionId"));
        if (hasText(status)) query.eq(UomCategoryEntity::getStatus, MdmMasterDataRules.status(status));
        query.orderByAsc(UomCategoryEntity::getCode).orderByAsc(UomCategoryEntity::getId);
        categoryMapper.selectPage(page, query);
        Map<String, String> referenceIds = referenceIdsByCategory(page.getRecords());
        return pageAdapter.toResult(page, category ->
                categoryView(category, requireReferenceId(referenceIds, category.getId())));
    }

    /**
     * 启用类别前重新校验量纲；同时恢复基准单位可用性。
     * @param id 类别 ID
     * @param version 乐观锁版本
     * @return 启用后类别视图
     * @throws MdmException 量纲停用、不存在或版本冲突时抛出
     */
    @Transactional
    public CategoryView enableCategory(String id, Long version) { return changeCategoryStatus(id, MdmMasterDataRules.ENABLED, version); }

    /**
     * 停用类别并同步停用其基准单位，不级联普通单位。
     * @param id 类别 ID
     * @param version 乐观锁版本
     * @return 停用后类别视图
     * @throws MdmException 不存在或版本冲突时抛出
     */
    @Transactional
    public CategoryView disableCategory(String id, Long version) { return changeCategoryStatus(id, MdmMasterDataRules.DISABLED, version); }

    /**
     * 创建非基准单位及首个不可变换算规则版本。
     *
     * @param code 单位编码
     * @param name 业务名称
     * @param symbol 显示符号
     * @param categoryId 启用类别 ID
     * @param reason 企业扩展编码说明
     * @param status 初始状态
     * @param multiplier 到基准单位的乘数
     * @param offset 换算偏移量
     * @param precision 计算精度
     * @param roundingMode 舍入模式
     * @return 已创建单位；非幂等，任一写失败全部回滚
     * @throws MdmException 输入、父级、唯一性或数据库失败时抛出
     */
    @Transactional
    public UomView createUom(String code, String name, String symbol, String categoryId,
                             String reason, String status, BigDecimal multiplier, BigDecimal offset,
                             Integer precision, String roundingMode) {
        UomCategoryEntity category = requireEnabledCategory(categoryId);
        UomEntity unit = newUom(code, name, symbol, category.getId(), false, reason, status);
        insertUom(unit); insertRule(newRule(unit.getId(), 1, multiplier, offset, precision, roundingMode));
        return uomView(unit);
    }

    /**
     * 更新单位中允许修正的名称；不接受 Code、Symbol、类别和基准身份。
     * @param id 单位 ID
     * @param name 业务名称
     * @param version 乐观锁版本
     * @return 更新后单位视图
     * @throws MdmException 不存在、输入或并发冲突时抛出
     */
    @Transactional
    public UomView updateUom(String id, String name, Long version) {
        UomEntity unit = requireUom(id);
        requireVersion(unit.getVersion(), version);
        unit.setName(MdmMasterDataRules.name(name));
        requireUpdated(uomMapper.updateById(unit), () -> uomMapper.selectById(unit.getId()), "Uom");
        return uomView(unit);
    }

    /**
     * 按 ID 查询单位详情。
     * @param id 单位 ID
     * @return 单位视图；只读且无副作用
     * @throws MdmException ID 非法或资源不存在时抛出
     */
    @Transactional(readOnly = true)
    public UomView getUom(String id) { return uomView(requireUom(id)); }

    /**
     * 按类别、状态和基准标记分页单位，复用统一分页转换。
     * @param pageQuery 包含可选类别、状态、基准标记和分页信息的唯一业务入参
     * @return 单位统一分页结果；只读且无副作用
     */
    @Transactional(readOnly = true)
    public PageResult<UomView> pageUoms(PageQuery<UomPageParams> pageQuery) {
        UomPageParams params = pageQuery.params();
        Page<UomEntity> page = pageAdapter.toPage(pageQuery);
        var query = new LambdaQueryWrapper<UomEntity>();
        String categoryId = params.categoryId();
        String status = params.status();
        Boolean reference = params.referenceUnit();
        if (hasText(categoryId)) query.eq(UomEntity::getCategoryId, MdmMasterDataRules.id(categoryId, "categoryId"));
        if (hasText(status)) query.eq(UomEntity::getStatus, MdmMasterDataRules.status(status));
        if (reference != null) query.eq(UomEntity::getReferenceUnit, reference);
        query.orderByDesc(UomEntity::getReferenceUnit).orderByAsc(UomEntity::getCode).orderByAsc(UomEntity::getId);
        uomMapper.selectPage(page, query); return pageAdapter.toResult(page, UomMasterDataApplication::uomView);
    }

    /**
     * 启用普通单位前验证类别和量纲均启用；基准单位拒绝独立操作。
     * @param id 单位 ID
     * @param version 乐观锁版本
     * @return 启用后单位视图
     * @throws MdmException 父级停用、基准身份或版本冲突时抛出
     */
    @Transactional
    public UomView enableUom(String id, Long version) { return changeUomStatus(id, MdmMasterDataRules.ENABLED, version); }

    /**
     * 停用普通单位；基准单位必须通过类别生命周期管理。
     * @param id 单位 ID
     * @param version 乐观锁版本
     * @return 停用后单位视图
     * @throws MdmException 基准身份、不存在或版本冲突时抛出
     */
    @Transactional
    public UomView disableUom(String id, Long version) { return changeUomStatus(id, MdmMasterDataRules.DISABLED, version); }

    /**
     * 发布新规则版本：乐观锁停用当前版本，再插入新版本，不修改历史内容。
     * @param uomId 非基准单位 ID
     * @param expectedVersionNo 当前业务规则版本
     * @param multiplier 新乘数
     * @param offset 新偏移量
     * @param precision 新计算精度
     * @param roundingMode 新舍入模式
     * @return 新规则版本；非幂等并产生停旧建新两项写入
     * @throws MdmException 基准单位、输入、父级或并发校验失败时抛出并回滚
     */
    @Transactional
    public RuleView publishRule(String uomId, Integer expectedVersionNo, BigDecimal multiplier, BigDecimal offset,
                                Integer precision, String roundingMode) {
        UomEntity unit = requireUom(uomId);
        if (Boolean.TRUE.equals(unit.getReferenceUnit())) throw MdmException.immutable("基准单位不需要换算规则");
        requireEnabledCategory(unit.getCategoryId());
        UomConversionRuleEntity current = requireCurrentRule(unit.getId());
        if (expectedVersionNo == null || !Objects.equals(current.getVersionNo(), expectedVersionNo)) throw MdmException.versionConflict();
        current.setStatus(MdmMasterDataRules.DISABLED);
        requireUpdated(ruleMapper.updateById(current), () -> ruleMapper.selectById(current.getId()),
                "UomConversionRule");
        UomConversionRuleEntity next = newRule(unit.getId(), current.getVersionNo() + 1, multiplier, offset, precision, roundingMode);
        insertRule(next); return ruleView(next);
    }

    /**
     * 查询非基准单位当前启用规则。
     * @param uomId 单位 ID
     * @return 当前规则视图；只读且无副作用
     * @throws MdmException 规则不存在时抛出
     */
    @Transactional(readOnly = true)
    public RuleView getCurrentRule(String uomId) { return ruleView(requireCurrentRule(MdmMasterDataRules.id(uomId, "uomId"))); }

    /**
     * 按版本倒序分页查询不可变规则历史。
     * @param pageQuery 包含必填单位 ID 和分页信息的唯一业务入参
     * @return 规则历史统一分页结果；只读且无副作用
     * @throws MdmException 单位不存在时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<RuleView> pageRules(PageQuery<UomConversionRulePageParams> pageQuery) {
        String uomId = pageQuery.params().uomId();
        String id = requireUom(uomId).getId();
        Page<UomConversionRuleEntity> page = pageAdapter.toPage(pageQuery);
        var query = new LambdaQueryWrapper<UomConversionRuleEntity>().eq(UomConversionRuleEntity::getUomId, id)
                .orderByDesc(UomConversionRuleEntity::getVersionNo);
        ruleMapper.selectPage(page, query); return pageAdapter.toResult(page, UomMasterDataApplication::ruleView);
    }

    /** 量纲启停只修改目标行；停用前拒绝任何 Material 的间接引用。 */
    private DimensionView changeDimensionStatus(String id, String status, Long version) {
        DimensionEntity entity = dimensionMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "dimensionId"));
        if (entity == null) throw MdmException.notFound("Dimension");
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.DISABLED.equals(status) && materialMapper.existsByDimensionId(entity.getId())) {
            throw MdmException.resourceReferenced("Dimension");
        }
        if (!status.equals(entity.getStatus())) {
            entity.setStatus(status);
            requireUpdated(dimensionMapper.updateById(entity), () -> dimensionMapper.selectById(entity.getId()),
                    "Dimension");
        }
        return dimensionView(entity);
    }

    /** 类别生命周期是基准单位可用性的唯一管理入口。 */
    private CategoryView changeCategoryStatus(String id, String status, Long version) {
        UomCategoryEntity category = categoryMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "categoryId"));
        if (category == null) throw MdmException.notFound("UomCategory");
        requireVersion(category.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(requireDimension(category.getDimensionId()).getStatus(), "Dimension");
        }
        if (MdmMasterDataRules.DISABLED.equals(status)
                && materialMapper.existsByUomCategoryId(category.getId())) {
            throw MdmException.resourceReferenced("UomCategory");
        }
        UomEntity referenceSnapshot = requireReference(category.getId());
        UomEntity reference = uomMapper.selectByIdForUpdate(referenceSnapshot.getId());
        if (reference == null) throw MdmException.notFound("ReferenceUom");
        if (!status.equals(category.getStatus())) {
            category.setStatus(status);
            requireUpdated(categoryMapper.updateById(category), () -> categoryMapper.selectById(category.getId()),
                    "UomCategory");
        }
        if (!status.equals(reference.getStatus())) {
            reference.setStatus(status);
            requireUpdated(uomMapper.updateById(reference), () -> uomMapper.selectById(reference.getId()),
                    "ReferenceUom");
        }
        return categoryView(category, reference.getId());
    }

    /** 普通单位启停只修改目标行；重新启用时必须再次验证完整父链。 */
    private UomView changeUomStatus(String id, String status, Long version) {
        UomEntity unit = uomMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "uomId"));
        if (unit == null) throw MdmException.notFound("Uom");
        requireVersion(unit.getVersion(), version);
        if (Boolean.TRUE.equals(unit.getReferenceUnit())) {
            throw MdmException.immutable("基准单位只能随计量单位类别启停");
        }
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            requireEnabledCategory(unit.getCategoryId());
        }
        if (MdmMasterDataRules.DISABLED.equals(status) && materialMapper.existsByUomId(unit.getId())) {
            throw MdmException.resourceReferenced("Uom");
        }
        if (!status.equals(unit.getStatus())) {
            unit.setStatus(status);
            requireUpdated(uomMapper.updateById(unit), () -> uomMapper.selectById(unit.getId()), "Uom");
        }
        return uomView(unit);
    }

    /** 只构造通过字段规则校验的单位实体；基准身份由调用用例明确传入。 */
    private UomEntity newUom(String code, String name, String symbol, String categoryId,
                             boolean reference, String reason, String status) {
        UomEntity entity = new UomEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        entity.setName(MdmMasterDataRules.name(name));
        entity.setSymbol(required(symbol, "symbol", 32));
        entity.setCategoryId(categoryId);
        entity.setReferenceUnit(reference);
        entity.setUcumNotApplicableReason(validateReason(entity.getCode(), reason));
        entity.setStatus(MdmMasterDataRules.status(status));
        return entity;
    }

    /** 创建尚未持久化的不可变 AFFINE 规则版本，并在写库前完成数值范围校验。 */
    private UomConversionRuleEntity newRule(String uomId, int versionNo, BigDecimal multiplier, BigDecimal offset,
                                            Integer precision, String roundingMode) {
        validateDecimal(multiplier, "multiplier");
        validateDecimal(offset, "offset");
        if (multiplier.signum() <= 0) {
            throw validation("multiplier 必须大于 0");
        }
        if (precision == null || precision < 1 || precision > 34) {
            throw validation("calculationPrecision 必须在 1..34");
        }
        try {
            RoundingMode.valueOf(roundingMode);
        } catch (RuntimeException exception) {
            throw validation("roundingMode 无效");
        }
        UomConversionRuleEntity rule = new UomConversionRuleEntity();
        rule.setUomId(uomId);
        rule.setVersionNo(versionNo);
        rule.setAlgorithmType("AFFINE");
        rule.setMultiplier(multiplier);
        rule.setOffset(offset);
        rule.setCalculationPrecision(precision);
        rule.setRoundingMode(roundingMode);
        rule.setStatus(MdmMasterDataRules.ENABLED);
        rule.setLockVersion(0L);
        return rule;
    }

    /** 按受控 String ID 读取量纲，逻辑删除行由 MyBatis-Plus 自动排除。 */
    private DimensionEntity requireDimension(String id) {
        DimensionEntity entity = dimensionMapper.selectById(MdmMasterDataRules.id(id, "dimensionId"));
        if (entity == null) {
            throw MdmException.notFound("Dimension");
        }
        return entity;
    }

    /** 按受控 String ID 读取计量单位类别。 */
    private UomCategoryEntity requireCategory(String id) {
        UomCategoryEntity entity = categoryMapper.selectById(MdmMasterDataRules.id(id, "categoryId"));
        if (entity == null) {
            throw MdmException.notFound("UomCategory");
        }
        return entity;
    }

    /** 按受控 String ID 读取单位目录行。 */
    private UomEntity requireUom(String id) {
        UomEntity entity = uomMapper.selectById(MdmMasterDataRules.id(id, "uomId"));
        if (entity == null) {
            throw MdmException.notFound("Uom");
        }
        return entity;
    }

    /** 创建或启用单位前同时要求类别及其量纲处于启用状态。 */
    private UomCategoryEntity requireEnabledCategory(String id) {
        UomCategoryEntity category = requireCategory(id);
        MdmMasterDataRules.requireEnabled(category.getStatus(), "UomCategory");
        MdmMasterDataRules.requireEnabled(requireDimension(category.getDimensionId()).getStatus(), "Dimension");
        return category;
    }

    /** 查询类别唯一基准单位；缺失时按目录完整性错误显式失败。 */
    private UomEntity requireReference(String categoryId) {
        UomEntity entity = uomMapper.selectOne(new LambdaQueryWrapper<UomEntity>()
                .eq(UomEntity::getCategoryId, categoryId)
                .eq(UomEntity::getReferenceUnit, true));
        if (entity == null) {
            throw MdmException.notFound("ReferenceUom");
        }
        return entity;
    }

    /** 查询单位唯一启用规则；部分唯一索引保证正常数据最多返回一行。 */
    private UomConversionRuleEntity requireCurrentRule(String uomId) {
        UomConversionRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<UomConversionRuleEntity>()
                .eq(UomConversionRuleEntity::getUomId, uomId)
                .eq(UomConversionRuleEntity::getStatus, MdmMasterDataRules.ENABLED));
        if (rule == null) {
            throw MdmException.notFound("UomConversionRule");
        }
        return rule;
    }

    /** 本页类别一次批量加载基准单位，避免在 PageAdapter 映射阶段逐行查询。 */
    private Map<String, String> referenceIdsByCategory(List<UomCategoryEntity> categories) {
        if (categories.isEmpty()) {
            return Map.of();
        }
        List<String> categoryIds = categories.stream().map(UomCategoryEntity::getId).toList();
        return uomMapper.selectList(new LambdaQueryWrapper<UomEntity>()
                        .in(UomEntity::getCategoryId, categoryIds)
                        .eq(UomEntity::getReferenceUnit, true))
                .stream()
                .collect(Collectors.toUnmodifiableMap(UomEntity::getCategoryId, UomEntity::getId));
    }

    /** 缺失基准单位代表目录完整性已损坏，分页必须显式失败而不能返回不完整视图。 */
    private static String requireReferenceId(Map<String, String> referenceIds, String categoryId) {
        String referenceId = referenceIds.get(categoryId);
        if (referenceId == null) {
            throw MdmException.notFound("ReferenceUom");
        }
        return referenceId;
    }

    /** 插入单位并将数据库唯一冲突转换为稳定的 MDM 409。 */
    private void insertUom(UomEntity entity) {
        try {
            uomMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict("Uom");
        }
    }

    /** 插入新规则版本；版本号或当前版本竞争统一表现为版本冲突并回滚外层事务。 */
    private void insertRule(UomConversionRuleEntity rule) {
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException exception) {
            throw MdmException.versionConflict();
        }
    }
    /** 将零行更新稳定地区分为资源已不存在或乐观锁版本冲突。 */
    private static void requireUpdated(int affected, Supplier<?> lookup, String resource) {
        if (affected == 1) {
            return;
        }
        if (lookup.get() == null) {
            throw MdmException.notFound(resource);
        }
        throw MdmException.versionConflict();
    }
    /** 七维向量的每一个指数都必须显式提供。 */
    private static Integer requiredExponent(Integer value) {
        if (value == null) {
            throw validation("量纲指数不能为空");
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 校验必填字符串并返回去除首尾空白后的值。 */
    private static String required(String value, String field, int max) {
        if (!hasText(value)) {
            throw validation(field + "不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() > max) {
            throw validation(field + "长度不能超过" + max);
        }
        return normalized;
    }

    /** 企业扩展编码必须说明 UCUM 不适用原因，标准 UCUM 编码则禁止携带该说明。 */
    private static String validateReason(String code, String reason) {
        if (code.startsWith("mom:")) {
            return required(reason, "ucumNotApplicableReason", 500);
        }
        if (hasText(reason)) {
            throw validation("标准单位不应填写 UCUM 不适用原因");
        }
        return null;
    }
    /** 在进入 PostgreSQL 前按 numeric(50,30) 同时限制小数位和最多二十位整数。 */
    private static void validateDecimal(BigDecimal value, String field) {
        int integerDigits = value == null ? Integer.MAX_VALUE : Math.max(0, value.precision() - value.scale());
        if (value == null || value.scale() > 30 || integerDigits > 20) {
            throw validation(field + "必须符合 numeric(50,30)");
        }
    }
    private static MdmException validation(String message) { return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", message); }

    static DimensionView dimensionView(DimensionEntity e) { return new DimensionView(e.getId(), e.getCode(), e.getName(), e.getTimeExponent(), e.getLengthExponent(), e.getMassExponent(), e.getElectricCurrentExponent(), e.getTemperatureExponent(), e.getAmountExponent(), e.getLuminousIntensityExponent(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    static CategoryView categoryView(UomCategoryEntity e, String referenceId) { return new CategoryView(e.getId(), e.getCode(), e.getName(), e.getDimensionId(), referenceId, e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    static UomView uomView(UomEntity e) { return new UomView(e.getId(), e.getCode(), e.getName(), e.getSymbol(), e.getCategoryId(), e.getReferenceUnit(), e.getUcumNotApplicableReason(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    static RuleView ruleView(UomConversionRuleEntity e) { return new RuleView(e.getId(), e.getUomId(), e.getVersionNo(), e.getAlgorithmType(), e.getMultiplier(), e.getOffset(), e.getCalculationPrecision(), e.getRoundingMode(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getLockVersion()); }
}
