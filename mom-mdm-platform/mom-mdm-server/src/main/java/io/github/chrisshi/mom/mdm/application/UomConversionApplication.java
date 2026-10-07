package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomConversionRuleEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.DimensionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomConversionRuleMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.chrisshi.mom.mdm.application.model.UomMasterDataViews.CompatibilityView;
import static io.github.chrisshi.mom.mdm.application.model.UomMasterDataViews.ConversionView;

/**
 * 当前规则换算、历史重放与兼容性识别的只读 Application。
 *
 * <p>所有换算先验证两个单位属于同一类别，再经类别唯一基准单位形成星型路径，不搜索任意图。当前换算
 * 在一个只读本地事务内一次读取两侧当前规则；历史重放按指定不可变版本读取。服务无本地缓存，MDM 或
 * PostgreSQL 不可用时 fail-closed，调用方不得把失败当作原值成功。</p>
 */
@Component
public class UomConversionApplication {
    private final UomMapper uomMapper;
    private final UomCategoryMapper categoryMapper;
    private final DimensionMapper dimensionMapper;
    private final UomConversionRuleMapper ruleMapper;
    private final UomConversionCalculator calculator;

    /** 注入目录 Mapper 与纯计算组件；不依赖 HTTP Result。 */
    public UomConversionApplication(UomMapper uomMapper, UomCategoryMapper categoryMapper,
                                    DimensionMapper dimensionMapper, UomConversionRuleMapper ruleMapper,
                                    UomConversionCalculator calculator) {
        this.uomMapper = uomMapper;
        this.categoryMapper = categoryMapper;
        this.dimensionMapper = dimensionMapper;
        this.ruleMapper = ruleMapper;
        this.calculator = calculator;
    }

    /**
     * 使用两侧当前启用规则换算。
     *
     * @param value 十进制字符串，避免 JSON 浮点精度丢失
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     * @return 换算值及两侧规则身份；只读且相同数据快照下结果稳定
     * @throws MdmException 单位不存在、状态不可用、类别不兼容或规则缺失时抛出
     */
    @Transactional(readOnly = true)
    public ConversionView convertCurrent(String value, String sourceUomId, String targetUomId) {
        UomEntity source = requireById(sourceUomId, "sourceUomId");
        UomEntity target = requireById(targetUomId, "targetUomId");
        UomCategoryEntity category = requireCompatible(source, target);
        requireAvailable(source, target, category);
        BigDecimal input = decimal(value);
        if (source.getId().equals(target.getId())) {
            return identity(input, source);
        }
        Map<String, UomConversionRuleEntity> rules = currentRules(source, target);
        return calculate(input, source, target, rules.get(source.getId()), rules.get(target.getId()));
    }

    /**
     * 使用调用方记录的规则版本重放历史换算。
     *
     * @param value 十进制字符串，避免 JSON 浮点精度丢失
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     * @param sourceRuleVersion 源非基准单位规则版本；源为基准单位时必须为 null
     * @param targetRuleVersion 目标非基准单位规则版本；目标为基准单位时必须为 null
     * @return 历史规则计算结果；不要求主数据当前仍启用且无写副作用
     * @throws MdmException 单位、兼容性、输入值或指定规则版本非法时抛出
     */
    @Transactional(readOnly = true)
    public ConversionView replay(String value, String sourceUomId, String targetUomId,
                                 Integer sourceRuleVersion, Integer targetRuleVersion) {
        UomEntity source = requireById(sourceUomId, "sourceUomId");
        UomEntity target = requireById(targetUomId, "targetUomId");
        requireCompatible(source, target);
        BigDecimal input = decimal(value);
        if (source.getId().equals(target.getId())) {
            if (sourceRuleVersion != null || targetRuleVersion != null) {
                throw validation("同一单位重放不应提供换算规则版本");
            }
            return identity(input, source);
        }
        UomConversionRuleEntity sourceRule = historicalRule(source, sourceRuleVersion);
        UomConversionRuleEntity targetRule = historicalRule(target, targetRuleVersion);
        return calculate(input, source, target, sourceRule, targetRule);
    }

    /**
     * 判断两个单位是否处于同一业务换算类别。
     *
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     * @return 兼容时带回类别和量纲 Code；跨类别返回 compatible=false 而非执行换算
     * @throws MdmException 任一单位或其类别、量纲不存在时抛出
     */
    @Transactional(readOnly = true)
    public CompatibilityView compatibility(String sourceUomId, String targetUomId) {
        UomEntity source = requireById(sourceUomId, "sourceUomId");
        UomEntity target = requireById(targetUomId, "targetUomId");
        if (!source.getCategoryId().equals(target.getCategoryId())) {
            return new CompatibilityView(source.getId(), target.getId(), false, null, null);
        }
        UomCategoryEntity category = requireCategory(source.getCategoryId());
        DimensionEntity dimension = requireDimension(category.getDimensionId());
        return new CompatibilityView(source.getId(), target.getId(), true, category.getCode(), dimension.getCode());
    }

    /** 通过 String 技术主键加载单位；业务调用不依赖可读 Code。 */
    private UomEntity requireById(String id, String field) {
        UomEntity unit = uomMapper.selectById(MdmMasterDataRules.id(id, field));
        if (unit == null) throw MdmException.notFound("Uom");
        return unit;
    }

    /** 验证并返回共同类别，跨类别直接拒绝。 */
    private UomCategoryEntity requireCompatible(UomEntity source, UomEntity target) {
        if (!source.getCategoryId().equals(target.getCategoryId())) throw MdmException.incompatibleUom();
        return requireCategory(source.getCategoryId());
    }

    /** 当前换算要求单位、类别和量纲全部启用；历史重放不使用该校验。 */
    private void requireAvailable(UomEntity source, UomEntity target, UomCategoryEntity category) {
        MdmMasterDataRules.requireEnabled(source.getStatus(), "SourceUom");
        MdmMasterDataRules.requireEnabled(target.getStatus(), "TargetUom");
        MdmMasterDataRules.requireEnabled(category.getStatus(), "UomCategory");
        MdmMasterDataRules.requireEnabled(requireDimension(category.getDimensionId()).getStatus(), "Dimension");
    }

    /** 一次查询取得两侧当前规则，避免两次读取之间发生换版造成组合快照。 */
    private Map<String, UomConversionRuleEntity> currentRules(UomEntity source, UomEntity target) {
        List<String> ids = java.util.stream.Stream.of(source, target)
                .filter(unit -> !Boolean.TRUE.equals(unit.getReferenceUnit())).map(UomEntity::getId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        List<UomConversionRuleEntity> rows = ruleMapper.selectList(new LambdaQueryWrapper<UomConversionRuleEntity>()
                .in(UomConversionRuleEntity::getUomId, ids)
                .eq(UomConversionRuleEntity::getStatus, MdmMasterDataRules.ENABLED));
        Map<String, UomConversionRuleEntity> result = new HashMap<>();
        rows.forEach(rule -> result.put(rule.getUomId(), rule));
        if (result.size() != ids.size()) throw MdmException.notFound("UomConversionRule");
        return result;
    }

    /** 基准单位不接受版本；非基准单位必须精确命中指定不可变版本。 */
    private UomConversionRuleEntity historicalRule(UomEntity unit, Integer version) {
        if (Boolean.TRUE.equals(unit.getReferenceUnit())) {
            if (version != null) throw validation("基准单位不应提供规则版本");
            return null;
        }
        if (version == null || version <= 0) throw validation("非基准单位必须提供正规则版本");
        UomConversionRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<UomConversionRuleEntity>()
                .eq(UomConversionRuleEntity::getUomId, unit.getId())
                .eq(UomConversionRuleEntity::getVersionNo, version));
        if (rule == null) throw MdmException.notFound("UomConversionRule");
        return rule;
    }

    /** 通过基准单位完成最多两步仿射计算，并保留实际使用的规则 ID/版本。 */
    private ConversionView calculate(BigDecimal value, UomEntity source, UomEntity target,
                                     UomConversionRuleEntity sourceRule, UomConversionRuleEntity targetRule) {
        BigDecimal reference = sourceRule == null ? value : calculator.toReference(value, sourceRule);
        BigDecimal result = targetRule == null ? reference : calculator.fromReference(reference, targetRule);
        return new ConversionView(plain(value), source.getId(), plain(result), target.getId(),
                sourceRule == null ? null : sourceRule.getId(), sourceRule == null ? null : sourceRule.getVersionNo(),
                targetRule == null ? null : targetRule.getId(), targetRule == null ? null : targetRule.getVersionNo());
    }

    /**
     * 同一技术主键不执行先乘后除，避免规则精度和舍入破坏换算恒等性。
     *
     * <p>没有执行任何换算规则，因此返回结果中的规则 ID 和版本均为 {@code null}。</p>
     */
    private ConversionView identity(BigDecimal value, UomEntity unit) {
        String normalized = plain(value);
        return new ConversionView(normalized, unit.getId(), normalized, unit.getId(), null, null, null, null);
    }

    private UomCategoryEntity requireCategory(String id) { var e = categoryMapper.selectById(id); if (e == null) throw MdmException.notFound("UomCategory"); return e; }
    private DimensionEntity requireDimension(String id) { var e = dimensionMapper.selectById(id); if (e == null) throw MdmException.notFound("Dimension"); return e; }
    private static BigDecimal decimal(String value) { try { return new BigDecimal(value); } catch (RuntimeException e) { throw validation("value 必须是十进制字符串"); } }
    private static String plain(BigDecimal value) { BigDecimal normalized = value.stripTrailingZeros(); return normalized.signum() == 0 ? "0" : normalized.toPlainString(); }
    private static MdmException validation(String message) { return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", "mdm", "error.validation_failed"); }
}
