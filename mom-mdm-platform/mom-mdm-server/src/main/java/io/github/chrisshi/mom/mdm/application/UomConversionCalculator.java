package io.github.chrisshi.mom.mdm.application;

import io.github.chrisshi.mom.mdm.infrastructure.entity.UomConversionRuleEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * UOM V1 仿射换算的纯计算组件，属于 Application 算法能力。
 *
 * <p>算法固定为 {@code reference = value * multiplier + offset}，不解释脚本或动态表达式。组件无共享
 * 可变状态，可被并发调用；精度与舍入来自所使用的规则版本，计算失败时 fail-closed，不返回近似成功。</p>
 */
@Component
public class UomConversionCalculator {
    /**
     * 将源单位数值换算到类别基准单位。
     *
     * @param value 源值
     * @param rule 源单位规则版本
     * @return 基准单位值
     * @throws MdmException 规则算法或数值非法时抛出；方法无副作用且相同输入结果稳定
     */
    public BigDecimal toReference(BigDecimal value, UomConversionRuleEntity rule) {
        requireAffine(rule);
        MathContext context = context(rule);
        try {
            return value.multiply(rule.getMultiplier(), context).add(rule.getOffset(), context);
        } catch (ArithmeticException exception) {
            throw MdmException.conversionInexact();
        }
    }

    /**
     * 将类别基准单位数值反算到目标单位。
     *
     * @param referenceValue 基准单位值
     * @param rule 目标单位规则版本
     * @return 目标单位值
     * @throws MdmException 规则算法或数值非法时抛出；方法无持久化副作用
     */
    public BigDecimal fromReference(BigDecimal referenceValue, UomConversionRuleEntity rule) {
        requireAffine(rule);
        MathContext context = context(rule);
        try {
            return referenceValue.subtract(rule.getOffset(), context).divide(rule.getMultiplier(), context);
        } catch (ArithmeticException exception) {
            throw MdmException.conversionInexact();
        }
    }

    /** 拒绝未受控算法以及不完整规则，避免基础数据异常产生静默错误结果。 */
    private static void requireAffine(UomConversionRuleEntity rule) {
        if (rule == null || !"AFFINE".equals(rule.getAlgorithmType()) || rule.getMultiplier() == null
                || rule.getMultiplier().signum() <= 0 || rule.getOffset() == null) {
            throw new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.invalid_conversion_rule", "mdm", "error.invalid_conversion_rule");
        }
    }

    /** 从规则建立本次运算专用 MathContext；无全局舍入状态。 */
    private static MathContext context(UomConversionRuleEntity rule) {
        try {
            return new MathContext(rule.getCalculationPrecision(), RoundingMode.valueOf(rule.getRoundingMode()));
        } catch (RuntimeException exception) {
            throw new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.invalid_conversion_rule", "mdm", "error.invalid_conversion_rule");
        }
    }
}
