package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomConversionRuleEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.DimensionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomConversionRuleMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import static io.github.chrisshi.mom.mdm.application.UomMasterDataViews.*;

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

    /** 注入当前 bounded context 的四个单表 Mapper。 */
    public UomMasterDataApplication(DimensionMapper dimensionMapper, UomCategoryMapper categoryMapper,
                                    UomMapper uomMapper, UomConversionRuleMapper ruleMapper) {
        this.dimensionMapper = dimensionMapper;
        this.categoryMapper = categoryMapper;
        this.uomMapper = uomMapper;
        this.ruleMapper = ruleMapper;
    }

    /**
     * 创建固定向量量纲；Code 与向量创建后不可普通修改。
     *
     * @param code 唯一业务编码
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
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
    public DimensionView createDimension(String code, String nameZh, String nameEn, Integer t, Integer l,
                                         Integer m, Integer i, Integer theta, Integer n, Integer j, String status) {
        DimensionEntity entity = new DimensionEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        entity.setNameZh(MdmMasterDataRules.nameZh(nameZh));
        entity.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        entity.setTimeExponent(requiredExponent(t)); entity.setLengthExponent(requiredExponent(l));
        entity.setMassExponent(requiredExponent(m)); entity.setElectricCurrentExponent(requiredExponent(i));
        entity.setTemperatureExponent(requiredExponent(theta)); entity.setAmountExponent(requiredExponent(n));
        entity.setLuminousIntensityExponent(requiredExponent(j)); entity.setStatus(MdmMasterDataRules.status(status));
        try { dimensionMapper.insert(entity); } catch (DuplicateKeyException e) { throw MdmException.codeConflict("Dimension"); }
        return dimensionView(entity);
    }

    /**
     * 更新量纲名称；不会修改 Code 或七维向量。
     * @param id 量纲 ID
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
     * @param version 乐观锁版本
     * @return 更新后视图；成功推进版本且无跨表副作用
     * @throws MdmException 不存在、输入或版本冲突时抛出
     */
    @Transactional
    public DimensionView updateDimension(String id, String nameZh, String nameEn, Long version) {
        DimensionEntity entity = requireDimension(id); requireVersion(entity.getVersion(), version);
        entity.setNameZh(MdmMasterDataRules.nameZh(nameZh)); entity.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        requireUpdated(dimensionMapper.updateById(entity), "Dimension"); return dimensionView(entity);
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
     * @param status 可选状态
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 统一分页结果；只读且无副作用
     */
    @Transactional(readOnly = true)
    public PageResult<DimensionView> pageDimensions(String status, long pageNo, long pageSize) {
        Page<DimensionEntity> page = PageAdapter.toPage(new PageQuery<>(status, pageNo, pageSize));
        var query = new LambdaQueryWrapper<DimensionEntity>();
        if (hasText(status)) query.eq(DimensionEntity::getStatus, MdmMasterDataRules.status(status));
        query.orderByAsc(DimensionEntity::getCode).orderByAsc(DimensionEntity::getId);
        dimensionMapper.selectPage(page, query); return PageAdapter.toResult(page, UomMasterDataApplication::dimensionView);
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
     * @param nameZh 类别中文名
     * @param nameEn 类别英文名
     * @param dimensionId 启用量纲 ID
     * @param status 类别状态
     * @param referenceCode 基准单位编码
     * @param referenceNameZh 基准单位中文名
     * @param referenceNameEn 基准单位英文名
     * @param referenceSymbol 基准单位符号
     * @param referenceReason 企业扩展编码说明
     * @return 类别视图；非幂等，任一插入失败则两行都回滚
     * @throws MdmException 输入、父级、唯一性或数据库失败时抛出
     */
    @Transactional
    public CategoryView createCategory(String code, String nameZh, String nameEn, String dimensionId, String status,
                                       String referenceCode, String referenceNameZh, String referenceNameEn,
                                       String referenceSymbol, String referenceReason) {
        DimensionEntity dimension = requireDimension(dimensionId);
        MdmMasterDataRules.requireEnabled(dimension.getStatus(), "Dimension");
        UomCategoryEntity category = new UomCategoryEntity();
        category.setCode(MdmMasterDataRules.code(code)); category.setNameZh(MdmMasterDataRules.nameZh(nameZh));
        category.setNameEn(MdmMasterDataRules.nameEn(nameEn)); category.setDimensionId(dimension.getId());
        category.setStatus(MdmMasterDataRules.status(status));
        try { categoryMapper.insert(category); } catch (DuplicateKeyException e) { throw MdmException.codeConflict("UomCategory"); }
        UomEntity reference = newUom(referenceCode, referenceNameZh, referenceNameEn, referenceSymbol,
                category.getId(), true, referenceReason, category.getStatus());
        insertUom(reference);
        return categoryView(category, reference.getId());
    }

    /**
     * 更新类别名称；量纲、Code 与基准单位身份不可修改。
     * @param id 类别 ID
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
     * @param version 乐观锁版本
     * @return 更新后类别视图
     * @throws MdmException 不存在、输入或并发冲突时抛出
     */
    @Transactional
    public CategoryView updateCategory(String id, String nameZh, String nameEn, Long version) {
        UomCategoryEntity category = requireCategory(id); requireVersion(category.getVersion(), version);
        category.setNameZh(MdmMasterDataRules.nameZh(nameZh)); category.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        requireUpdated(categoryMapper.updateById(category), "UomCategory"); return categoryView(category, requireReference(category.getId()).getId());
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
     * @param dimensionId 可选量纲 ID
     * @param status 可选状态
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 类别统一分页结果；只读且无副作用
     */
    @Transactional(readOnly = true)
    public PageResult<CategoryView> pageCategories(String dimensionId, String status, long pageNo, long pageSize) {
        Page<UomCategoryEntity> page = PageAdapter.toPage(new PageQuery<>(dimensionId, pageNo, pageSize));
        var query = new LambdaQueryWrapper<UomCategoryEntity>();
        if (hasText(dimensionId)) query.eq(UomCategoryEntity::getDimensionId, MdmMasterDataRules.id(dimensionId, "dimensionId"));
        if (hasText(status)) query.eq(UomCategoryEntity::getStatus, MdmMasterDataRules.status(status));
        query.orderByAsc(UomCategoryEntity::getCode).orderByAsc(UomCategoryEntity::getId);
        categoryMapper.selectPage(page, query);
        return PageAdapter.toResult(page, c -> categoryView(c, requireReference(c.getId()).getId()));
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
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
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
    public UomView createUom(String code, String nameZh, String nameEn, String symbol, String categoryId,
                             String reason, String status, BigDecimal multiplier, BigDecimal offset,
                             Integer precision, String roundingMode) {
        UomCategoryEntity category = requireEnabledCategory(categoryId);
        UomEntity unit = newUom(code, nameZh, nameEn, symbol, category.getId(), false, reason, status);
        insertUom(unit); insertRule(newRule(unit.getId(), 1, multiplier, offset, precision, roundingMode));
        return uomView(unit);
    }

    /**
     * 更新单位中允许修正的中英文名称；不接受 Code、Symbol、类别和基准身份。
     * @param id 单位 ID
     * @param nameZh 中文名称
     * @param nameEn 可选英文名称
     * @param version 乐观锁版本
     * @return 更新后单位视图
     * @throws MdmException 不存在、输入或并发冲突时抛出
     */
    @Transactional
    public UomView updateUom(String id, String nameZh, String nameEn, Long version) {
        UomEntity unit = requireUom(id); requireVersion(unit.getVersion(), version);
        unit.setNameZh(MdmMasterDataRules.nameZh(nameZh)); unit.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        requireUpdated(uomMapper.updateById(unit), "Uom"); return uomView(unit);
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
     * @param categoryId 可选类别 ID
     * @param status 可选状态
     * @param reference 可选基准标记
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 单位统一分页结果；只读且无副作用
     */
    @Transactional(readOnly = true)
    public PageResult<UomView> pageUoms(String categoryId, String status, Boolean reference, long pageNo, long pageSize) {
        Page<UomEntity> page = PageAdapter.toPage(new PageQuery<>(categoryId, pageNo, pageSize));
        var query = new LambdaQueryWrapper<UomEntity>();
        if (hasText(categoryId)) query.eq(UomEntity::getCategoryId, MdmMasterDataRules.id(categoryId, "categoryId"));
        if (hasText(status)) query.eq(UomEntity::getStatus, MdmMasterDataRules.status(status));
        if (reference != null) query.eq(UomEntity::getReferenceUnit, reference);
        query.orderByDesc(UomEntity::getReferenceUnit).orderByAsc(UomEntity::getCode).orderByAsc(UomEntity::getId);
        uomMapper.selectPage(page, query); return PageAdapter.toResult(page, UomMasterDataApplication::uomView);
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
        requireUpdated(ruleMapper.updateById(current), "UomConversionRule");
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
     * @param uomId 单位 ID
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 规则历史统一分页结果；只读且无副作用
     * @throws MdmException 单位不存在时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<RuleView> pageRules(String uomId, long pageNo, long pageSize) {
        String id = requireUom(uomId).getId();
        Page<UomConversionRuleEntity> page = PageAdapter.toPage(new PageQuery<>(id, pageNo, pageSize));
        var query = new LambdaQueryWrapper<UomConversionRuleEntity>().eq(UomConversionRuleEntity::getUomId, id)
                .orderByDesc(UomConversionRuleEntity::getVersionNo);
        ruleMapper.selectPage(page, query); return PageAdapter.toResult(page, UomMasterDataApplication::ruleView);
    }

    private DimensionView changeDimensionStatus(String id, String status, Long version) {
        DimensionEntity e = requireDimension(id); requireVersion(e.getVersion(), version);
        if (!status.equals(e.getStatus())) { e.setStatus(status); requireUpdated(dimensionMapper.updateById(e), "Dimension"); }
        return dimensionView(e);
    }

    /** 类别生命周期是基准单位可用性的唯一管理入口。 */
    private CategoryView changeCategoryStatus(String id, String status, Long version) {
        UomCategoryEntity category = requireCategory(id); requireVersion(category.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) MdmMasterDataRules.requireEnabled(requireDimension(category.getDimensionId()).getStatus(), "Dimension");
        UomEntity reference = requireReference(category.getId());
        if (!status.equals(category.getStatus())) { category.setStatus(status); requireUpdated(categoryMapper.updateById(category), "UomCategory"); }
        if (!status.equals(reference.getStatus())) { reference.setStatus(status); requireUpdated(uomMapper.updateById(reference), "ReferenceUom"); }
        return categoryView(category, reference.getId());
    }

    private UomView changeUomStatus(String id, String status, Long version) {
        UomEntity unit = requireUom(id); requireVersion(unit.getVersion(), version);
        if (Boolean.TRUE.equals(unit.getReferenceUnit())) throw MdmException.immutable("基准单位只能随计量单位类别启停");
        if (MdmMasterDataRules.ENABLED.equals(status)) requireEnabledCategory(unit.getCategoryId());
        if (!status.equals(unit.getStatus())) { unit.setStatus(status); requireUpdated(uomMapper.updateById(unit), "Uom"); }
        return uomView(unit);
    }

    private UomEntity newUom(String code, String nameZh, String nameEn, String symbol, String categoryId,
                             boolean reference, String reason, String status) {
        UomEntity e = new UomEntity(); e.setCode(MdmMasterDataRules.code(code));
        e.setNameZh(MdmMasterDataRules.nameZh(nameZh)); e.setNameEn(MdmMasterDataRules.nameEn(nameEn));
        e.setSymbol(required(symbol, "symbol", 32)); e.setCategoryId(categoryId); e.setReferenceUnit(reference);
        e.setUcumNotApplicableReason(validateReason(e.getCode(), reason)); e.setStatus(MdmMasterDataRules.status(status)); return e;
    }

    private UomConversionRuleEntity newRule(String uomId, int versionNo, BigDecimal multiplier, BigDecimal offset,
                                            Integer precision, String roundingMode) {
        validateDecimal(multiplier, "multiplier"); validateDecimal(offset, "offset");
        if (multiplier.signum() <= 0) throw validation("multiplier 必须大于 0");
        if (precision == null || precision < 1 || precision > 34) throw validation("calculationPrecision 必须在 1..34");
        try { RoundingMode.valueOf(roundingMode); } catch (RuntimeException e) { throw validation("roundingMode 无效"); }
        UomConversionRuleEntity r = new UomConversionRuleEntity(); r.setUomId(uomId); r.setVersionNo(versionNo);
        r.setAlgorithmType("AFFINE"); r.setMultiplier(multiplier); r.setOffset(offset); r.setCalculationPrecision(precision);
        r.setRoundingMode(roundingMode); r.setStatus(MdmMasterDataRules.ENABLED); r.setLockVersion(0L); return r;
    }

    private DimensionEntity requireDimension(String id) { var e = dimensionMapper.selectById(MdmMasterDataRules.id(id, "dimensionId")); if (e == null) throw MdmException.notFound("Dimension"); return e; }
    private UomCategoryEntity requireCategory(String id) { var e = categoryMapper.selectById(MdmMasterDataRules.id(id, "categoryId")); if (e == null) throw MdmException.notFound("UomCategory"); return e; }
    private UomEntity requireUom(String id) { var e = uomMapper.selectById(MdmMasterDataRules.id(id, "uomId")); if (e == null) throw MdmException.notFound("Uom"); return e; }
    private UomCategoryEntity requireEnabledCategory(String id) { var c = requireCategory(id); MdmMasterDataRules.requireEnabled(c.getStatus(), "UomCategory"); MdmMasterDataRules.requireEnabled(requireDimension(c.getDimensionId()).getStatus(), "Dimension"); return c; }
    private UomEntity requireReference(String categoryId) { var e = uomMapper.selectOne(new LambdaQueryWrapper<UomEntity>().eq(UomEntity::getCategoryId, categoryId).eq(UomEntity::getReferenceUnit, true)); if (e == null) throw MdmException.notFound("ReferenceUom"); return e; }
    private UomConversionRuleEntity requireCurrentRule(String uomId) { var r = ruleMapper.selectOne(new LambdaQueryWrapper<UomConversionRuleEntity>().eq(UomConversionRuleEntity::getUomId, uomId).eq(UomConversionRuleEntity::getStatus, MdmMasterDataRules.ENABLED)); if (r == null) throw MdmException.notFound("UomConversionRule"); return r; }

    private void insertUom(UomEntity e) { try { uomMapper.insert(e); } catch (DuplicateKeyException x) { throw MdmException.codeConflict("Uom"); } }
    private void insertRule(UomConversionRuleEntity r) { try { ruleMapper.insert(r); } catch (DuplicateKeyException x) { throw MdmException.versionConflict(); } }
    private static void requireVersion(Long actual, Long expected) { long e = MdmMasterDataRules.version(expected); if (actual == null || actual != e) throw MdmException.versionConflict(); }
    private static void requireUpdated(int affected, String resource) { if (affected != 1) throw new MdmException(MdmException.Kind.CONFLICT, "mdm.update_conflict", resource + "更新冲突"); }
    private static Integer requiredExponent(Integer value) { if (value == null) throw validation("量纲指数不能为空"); return value; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String required(String value, String field, int max) { if (!hasText(value)) throw validation(field + "不能为空"); String v = value.strip(); if (v.length() > max) throw validation(field + "长度不能超过" + max); return v; }
    private static String validateReason(String code, String reason) { if (code.startsWith("mom:")) return required(reason, "ucumNotApplicableReason", 500); if (hasText(reason)) throw validation("标准单位不应填写 UCUM 不适用原因"); return null; }
    /** 在进入 PostgreSQL 前按 numeric(50,30) 同时限制小数位和最多二十位整数。 */
    private static void validateDecimal(BigDecimal value, String field) {
        int integerDigits = value == null ? Integer.MAX_VALUE : Math.max(0, value.precision() - value.scale());
        if (value == null || value.scale() > 30 || integerDigits > 20) {
            throw validation(field + "必须符合 numeric(50,30)");
        }
    }
    private static MdmException validation(String message) { return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", message); }

    static DimensionView dimensionView(DimensionEntity e) { return new DimensionView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getTimeExponent(), e.getLengthExponent(), e.getMassExponent(), e.getElectricCurrentExponent(), e.getTemperatureExponent(), e.getAmountExponent(), e.getLuminousIntensityExponent(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    static CategoryView categoryView(UomCategoryEntity e, String referenceId) { return new CategoryView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getDimensionId(), referenceId, e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    static UomView uomView(UomEntity e) { return new UomView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getSymbol(), e.getCategoryId(), e.getReferenceUnit(), e.getUcumNotApplicableReason(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    static RuleView ruleView(UomConversionRuleEntity e) { return new RuleView(e.getId(), e.getUomId(), e.getVersionNo(), e.getAlgorithmType(), e.getMultiplier(), e.getOffset(), e.getCalculationPrecision(), e.getRoundingMode(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getLockVersion()); }
}
