package io.github.chrisshi.mom.i18n.validation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 各 Owner 共用的稳定键、Locale 与纯文本规则。
 *
 * <p>规则只做无状态输入校验，不访问数据库或业务服务，因而可并发复用。namespace
 * 的归属还必须由宿主 {@code I18nNamespacePolicy} 单独判断。</p>
 */
public final class I18nRules {
    private static final Pattern NAMESPACE = Pattern.compile(
            "^[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)*$");
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "^[a-zA-Z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)*$");
    private static final Pattern LOCALE = Pattern.compile(
            "^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|[0-9]{3}))?$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([0-9]+)}");

    private I18nRules() {
    }

    /** @return 规范化的小写点分段 namespace */
    public static String namespace(String value) {
        String normalized = required(value, "namespace", 128).toLowerCase(Locale.ROOT);
        if (!NAMESPACE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("namespace 格式非法");
        }
        return normalized;
    }

    /** @return 保留大小写的稳定 messageKey */
    public static String messageKey(String value) {
        String normalized = required(value, "messageKey", 160);
        if (!MESSAGE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("messageKey 格式非法");
        }
        return normalized;
    }

    /** @return JDK 规范大小写后的 BCP 47 Tag */
    public static String localeCode(String value) {
        String raw = required(value, "localeCode", 35);
        String normalized = Locale.forLanguageTag(raw).toLanguageTag();
        if ("und".equals(normalized) || !LOCALE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("localeCode 必须是受支持格式的 BCP 47 Tag");
        }
        return normalized;
    }

    /** @return 规范化的可空说明 */
    public static String description(String value) {
        return value == null || value.isBlank() ? null : required(value, "description", 1000);
    }

    /** @return 校验后的非负乐观锁版本 */
    public static long version(Long value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("version 不能小于 0");
        }
        return value;
    }

    /** @return 1～19 位当前服务技术 ID */
    public static String id(String value) {
        String normalized = required(value, "id", 19);
        if (!normalized.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("id 必须是数字字符串");
        }
        return normalized;
    }

    /**
     * 仅允许纯文本及数字位置占位符；各 Locale 的占位符集合必须相同。
     *
     * @return 已去重的位置编号集合
     */
    public static Set<Integer> placeholders(String value) {
        String text = required(value, "messageText", 4096);
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new IllegalArgumentException("messageText 不允许 HTML");
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        Set<Integer> positions = new LinkedHashSet<>();
        while (matcher.find()) {
            positions.add(Integer.parseInt(matcher.group(1)));
        }
        String remainder = PLACEHOLDER.matcher(text).replaceAll("");
        if (remainder.indexOf('{') >= 0 || remainder.indexOf('}') >= 0) {
            throw new IllegalArgumentException("placeholder 只支持 {0}、{1} 等数字位置格式");
        }
        return Set.copyOf(positions);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " 长度或内容非法");
        }
        return normalized;
    }
}
