package io.github.chrisshi.mom.system.domain.dictionary;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 非权威通用字典的 Code、Label、排序与说明规则。
 *
 * <p>该纯领域类型不依赖 Spring、Web 或持久化。Code 在创建时规范为小写且之后不可修改；Label 只承载
 * fallback 展示文本。规则不提供 Tree、Metadata、Alias、Locale 或任意扩展属性，且无共享可变状态。</p>
 */
public final class SystemDictionaryRules {
    public static final int MAX_SORT_ORDER = 1_000_000;
    private static final Pattern DICTIONARY_CODE =
            Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+");
    private static final Pattern ITEM_CODE = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private SystemDictionaryRules() {
    }

    /**
     * 规范化带命名空间的全局字典 Code。
     *
     * @param value 原始字典 Code
     * @return 小写规范 Code
     * @throws IllegalArgumentException 为空、超长或不符合点分段格式
     */
    public static String normalizeDictionaryCode(String value) {
        String normalized = requireText(value, "dictionaryCode", 128).toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || !DICTIONARY_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("dictionaryCode 必须是 3～128 位小写点分段 Code");
        }
        return normalized;
    }

    /**
     * 规范化字典内唯一的 Item Code。
     *
     * @param value 原始 Item Code
     * @return 小写规范 Code
     * @throws IllegalArgumentException 为空、超长或格式非法
     */
    public static String normalizeItemCode(String value) {
        String normalized = requireText(value, "itemCode", 64).toLowerCase(Locale.ROOT);
        if (!ITEM_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("itemCode 必须以小写字母开头，且只能包含小写字母、数字、短横线和下划线");
        }
        return normalized;
    }

    /** 验证并保留单一 fallback 字典名称中的普通空格。 */
    public static String normalizeDictionaryName(String value) {
        return requireText(value, "dictionaryName", 200);
    }

    /** 验证并保留单一 fallback 条目 Label 中的普通空格。 */
    public static String normalizeItemLabel(String value) {
        return requireText(value, "itemLabel", 200);
    }

    /** 验证固定排序值，禁止负数或无界大值。 */
    public static int requireSortOrder(Integer value) {
        if (value == null || value < 0 || value > MAX_SORT_ORDER) {
            throw new IllegalArgumentException("sortOrder 必须在 0～" + MAX_SORT_ORDER + " 之间");
        }
        return value;
    }

    /** 规范化可选说明；空白转为 null，非空内容拒绝控制字符。 */
    public static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 1000) {
            throw new IllegalArgumentException("description 长度不能超过 1000");
        }
        rejectControlCharacters(value, "description");
        return value;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + maxLength);
        }
        rejectControlCharacters(normalized, fieldName);
        return normalized;
    }

    private static void rejectControlCharacters(String value, String fieldName) {
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " 不允许控制字符");
        }
    }
}
