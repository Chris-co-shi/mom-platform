package io.github.chrisshi.mom.system.domain.preference;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 单个受限 Saved Filter。
 *
 * <p>值只允许最长 512 字符的标量字符串；Operator/ValueType 为闭集，不支持 Object、Script、Regex、SQL
 * 或 Java 类型信息。Filter 仅恢复客户端状态，业务查询必须再次校验字段、Operator 与数据权限。</p>
 */
public record FilterSetting(String fieldKey, Operator operator, ValueType valueType, List<String> values) {
    public FilterSetting {
        fieldKey = PreferenceRules.requireFieldKey(fieldKey, "invalid_filter_setting");
        if (operator == null || valueType == null || values == null || values.stream().anyMatch(value -> value == null
                || value.length() > 512 || PreferenceRules.looksSensitiveValue(value))) {
            throw new PreferenceValidationException("invalid_filter_setting", "Filter 类型或值非法");
        }
        values = List.copyOf(values);
        requireCardinality(operator, values.size());
        values.forEach(value -> validateScalar(valueType, value));
    }

    private static void requireCardinality(Operator operator, int size) {
        boolean valid = switch (operator) {
            case IS_NULL, IS_NOT_NULL -> size == 0;
            case BETWEEN -> size == 2;
            case IN -> size >= 1;
            default -> size == 1;
        };
        if (!valid) {
            throw new PreferenceValidationException("invalid_filter_setting", "Filter values 数量非法");
        }
    }

    private static void validateScalar(ValueType type, String value) {
        try {
            switch (type) {
                case STRING -> { }
                case INTEGER -> new BigInteger(value);
                case DECIMAL -> new BigDecimal(value);
                case BOOLEAN -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new PreferenceValidationException(
                                "invalid_filter_setting", "Filter 标量格式非法");
                    }
                }
                case DATE -> LocalDate.parse(value);
                case INSTANT -> Instant.parse(value);
            }
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new PreferenceValidationException("invalid_filter_setting", "Filter 标量格式非法", exception);
        }
    }

    /** Saved Filter Operator 白名单。 */
    public enum Operator { EQ, NE, CONTAINS, STARTS_WITH, IN, BETWEEN, IS_NULL, IS_NOT_NULL }

    /** Saved Filter 标量类型白名单。 */
    public enum ValueType { STRING, INTEGER, DECIMAL, BOOLEAN, DATE, INSTANT }
}
