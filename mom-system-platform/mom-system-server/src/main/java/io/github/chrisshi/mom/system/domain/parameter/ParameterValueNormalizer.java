package io.github.chrisshi.mom.system.domain.parameter;

import io.github.chrisshi.mom.system.api.ParameterValueType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * 类型化参数值的校验与规范化器。
 *
 * <p>该领域服务复用应用统一注入的 Jackson {@link ObjectMapper}，不自行创建 Parser。所有结果都是非空
 * 规范字符串；JSON 仅校验并压缩，不执行 Schema、脚本或表达式。实例只持有线程安全配置完成后的
 * ObjectMapper 引用，可并发复用；解析失败直接拒绝写入。</p>
 */
public final class ParameterValueNormalizer {
    private static final int MAX_STRING_LENGTH = 4096;
    private static final int MAX_JSON_LENGTH = 16384;
    private static final int MAX_NUMERIC_INPUT_LENGTH = 1024;
    private final ObjectMapper objectMapper;

    public ParameterValueNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 校验并规范化类型值。
     *
     * @param valueType 值类型
     * @param value 原始非空字符串
     * @return 规范字符串
     * @throws IllegalArgumentException 类型为空、长度越界、格式非法或包含控制字符
     */
    public String normalize(ParameterValueType valueType, String value) {
        if (valueType == null) {
            throw new IllegalArgumentException("valueType 不能为空");
        }
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("parameterValue 不能为空");
        }
        return switch (valueType) {
            case STRING -> normalizeString(value);
            case INTEGER -> normalizeInteger(value);
            case DECIMAL -> normalizeDecimal(value);
            case BOOLEAN -> normalizeBoolean(value);
            case JSON -> normalizeJson(value);
        };
    }

    private static String normalizeString(String value) {
        if (value.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("STRING 长度不能超过 " + MAX_STRING_LENGTH);
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("STRING 不允许控制字符");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("STRING 不能只包含空白");
        }
        return value;
    }

    private static String normalizeInteger(String value) {
        String candidate = numericCandidate(value);
        try {
            return new BigInteger(candidate).toString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("INTEGER 值非法", exception);
        }
    }

    private static String normalizeDecimal(String value) {
        String candidate = numericCandidate(value);
        try {
            BigDecimal decimal = new BigDecimal(candidate).stripTrailingZeros();
            return decimal.signum() == 0 ? "0" : decimal.toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("DECIMAL 值非法", exception);
        }
    }

    private static String normalizeBoolean(String value) {
        return switch (value) {
            case "true" -> "true";
            case "false" -> "false";
            default -> throw new IllegalArgumentException("BOOLEAN 只接受 true 或 false");
        };
    }

    private String normalizeJson(String value) {
        if (value.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("JSON 长度不能超过 " + MAX_JSON_LENGTH);
        }
        try {
            String normalized = objectMapper.writeValueAsString(objectMapper.readTree(value));
            if (normalized.length() > MAX_JSON_LENGTH) {
                throw new IllegalArgumentException("规范化 JSON 长度不能超过 " + MAX_JSON_LENGTH);
            }
            return normalized;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("JSON 值非法", exception);
        }
    }

    private static String numericCandidate(String value) {
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("数值不能为空");
        }
        if (candidate.length() > MAX_NUMERIC_INPUT_LENGTH) {
            throw new IllegalArgumentException("数值长度不能超过 " + MAX_NUMERIC_INPUT_LENGTH);
        }
        return candidate;
    }
}
