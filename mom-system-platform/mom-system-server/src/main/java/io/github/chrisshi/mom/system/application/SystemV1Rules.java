package io.github.chrisshi.mom.system.application;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System V1 Dictionary、Locale 与动态 I18n 的无状态输入规则。
 *
 * <p>规则只依赖 JDK，可被 Application 和快速单元测试直接复用。所有方法无共享可变状态且线程安全；
 * 它们只做确定性规范化与拒绝，不访问数据库或外部基础设施。这里不实现 ICU、HTML 清洗、脚本执行或
 * 通用模板引擎，避免把 Translation 扩展成 CMS。</p>
 */
public final class SystemV1Rules {
    private static final Pattern DICTIONARY_CODE = Pattern.compile("^[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+$");
    private static final Pattern ITEM_KEY = Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");
    private static final Pattern LOCALE_CODE = Pattern.compile(
            "^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|[0-9]{3}))?$");
    private static final Pattern NAMESPACE = Pattern.compile("^system(?:\\.[a-z][a-z0-9-]*)*$");
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "^[a-zA-Z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)*$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([0-9]+)}");

    private SystemV1Rules() {
    }

    /** @return 规范小写点分段字典 Code */
    public static String dictionaryCode(String value) {
        String normalized = requiredText(value, "dictionaryCode", 128).toLowerCase(Locale.ROOT);
        if (!DICTIONARY_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("dictionaryCode 必须是小写点分段 Code");
        }
        return normalized;
    }

    /** @return 规范小写字典条目 Key */
    public static String itemKey(String value) {
        String normalized = requiredText(value, "key", 64).toLowerCase(Locale.ROOT);
        if (!ITEM_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("key 必须以小写字母开头且只能包含小写字母、数字、短横线和下划线");
        }
        return normalized;
    }

    /** @return 经长度与控制字符校验的必填显示文本 */
    public static String displayText(String value, String field, int maxLength) {
        return requiredText(value, field, maxLength);
    }

    /** @return 空白转 null 的可选说明 */
    public static String description(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, "description", 1000);
    }

    /** @return 0～1,000,000 的固定排序值 */
    public static int sortOrder(Integer value) {
        if (value == null || value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException("sortOrder 必须在 0～1000000 之间");
        }
        return value;
    }

    /**
     * 规范化并严格校验 BCP 47 的当前 V1 子集。
     *
     * @param value 输入 Locale Tag
     * @return JDK 规范大小写后的 Tag
     */
    public static String localeCode(String value) {
        String raw = requiredText(value, "localeCode", 35);
        Locale locale = Locale.forLanguageTag(raw);
        String normalized = locale.toLanguageTag();
        if ("und".equals(normalized) || !LOCALE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("localeCode 必须是受支持格式的 BCP 47 Tag");
        }
        return normalized;
    }

    /** @return 只允许 System ownership 的 namespace */
    public static String namespace(String value) {
        String normalized = requiredText(value, "namespace", 128).toLowerCase(Locale.ROOT);
        if (!NAMESPACE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("System 只允许 system 或 system.* namespace");
        }
        return normalized;
    }

    /** @return namespace 内稳定且大小写保留的消息键 */
    public static String messageKey(String value) {
        String normalized = requiredText(value, "messageKey", 160);
        if (!MESSAGE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("messageKey 格式非法");
        }
        return normalized;
    }

    /**
     * 校验普通文本并返回其中有序去重的位置占位符集合。
     *
     * @param value Translation 文本
     * @return 例如文本含 {0}/{1} 时返回 [0,1]
     */
    public static Set<Integer> placeholders(String value) {
        String text = requiredText(value, "messageText", 4096);
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new IllegalArgumentException("messageText 只允许普通文本，不允许 HTML");
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        Set<Integer> result = new LinkedHashSet<>();
        StringBuilder remainder = new StringBuilder(text);
        while (matcher.find()) {
            result.add(Integer.parseInt(matcher.group(1)));
        }
        String withoutValidPlaceholders = PLACEHOLDER.matcher(remainder).replaceAll("");
        if (withoutValidPlaceholders.indexOf('{') >= 0 || withoutValidPlaceholders.indexOf('}') >= 0) {
            throw new IllegalArgumentException("V1 placeholder 只支持 {0}、{1} 等数字位置格式");
        }
        return Set.copyOf(result);
    }

    /** @return 1～19 位数据库技术 ID */
    public static String id(String value) {
        String normalized = requiredText(value, "id", 19);
        if (!normalized.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("id 必须是数字字符串");
        }
        return normalized;
    }

    /** 校验客户端乐观版本。 */
    public static long version(Long value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("version 不能小于 0");
        }
        return value;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " 不允许控制字符");
        }
        return normalized;
    }
}
