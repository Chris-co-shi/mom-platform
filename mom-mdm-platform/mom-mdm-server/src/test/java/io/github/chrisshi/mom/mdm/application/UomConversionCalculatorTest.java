package io.github.chrisshi.mom.mdm.application;

import io.github.chrisshi.mom.mdm.infrastructure.entity.UomConversionRuleEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UOM 仿射计算器的快速单元测试。
 *
 * <p>测试不启动 Spring 或数据库，验证乘法单位和带偏移温度的双向算法；持久化版本、事务和约束由真实
 * PostgreSQL IT 覆盖。</p>
 */
class UomConversionCalculatorTest {
    private final UomConversionCalculator calculator = new UomConversionCalculator();

    /** 验证毫米到米再反算，使用规则自己的精度和舍入。 */
    @Test
    void shouldConvertMultiplicativeUnitBothWays() {
        UomConversionRuleEntity rule = rule("0.001", "0");
        assertThat(calculator.toReference(new BigDecimal("2500"), rule)).isEqualByComparingTo("2.5");
        assertThat(calculator.fromReference(new BigDecimal("2.5"), rule)).isEqualByComparingTo("2500");
    }

    /** 验证摄氏度仿射偏移的正向和反向运算。 */
    @Test
    void shouldConvertOffsetUnitBothWays() {
        UomConversionRuleEntity rule = rule("1", "273.15");
        assertThat(calculator.toReference(new BigDecimal("25"), rule)).isEqualByComparingTo("298.15");
        assertThat(calculator.fromReference(new BigDecimal("298.15"), rule)).isEqualByComparingTo("25");
    }

    /** 验证规则禁止舍入且结果无法精确表示时返回稳定业务错误，而不是泄漏算术异常。 */
    @Test
    void shouldRejectInexactConversionWhenRoundingIsUnnecessary() {
        UomConversionRuleEntity rule = rule("3", "0");
        rule.setCalculationPrecision(2);
        rule.setRoundingMode("UNNECESSARY");

        assertThatThrownBy(() -> calculator.toReference(new BigDecimal("1.23"), rule))
                .isInstanceOfSatisfying(MdmException.class,
                        exception -> assertThat(exception.code()).isEqualTo("mdm.conversion_inexact"));
    }

    /** 创建仅供纯算法测试使用的完整 AFFINE 规则。 */
    private static UomConversionRuleEntity rule(String multiplier, String offset) {
        UomConversionRuleEntity rule = new UomConversionRuleEntity();
        rule.setAlgorithmType("AFFINE");
        rule.setMultiplier(new BigDecimal(multiplier));
        rule.setOffset(new BigDecimal(offset));
        rule.setCalculationPrecision(34);
        rule.setRoundingMode("HALF_EVEN");
        return rule;
    }
}
