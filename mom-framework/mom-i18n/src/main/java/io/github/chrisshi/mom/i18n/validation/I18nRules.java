package io.github.chrisshi.mom.i18n.validation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 各 Owner 共用的稳定键、Locale 与纯文本规则。
 *
 * <p>规则只做无状态输入校验，不访问数据库或业务服务，因而可并发复用。Owner 由注入的
 * 本地 Store 确定，不进入 Key。</p>
 */
public final class I18nRules {
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");
    private static final Pattern LOCALE = Pattern.compile(
            "^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|[0-9]{3}))?$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([0-9]+)}");

    private I18nRules() {
    }

    /** @return Locale.ROOT 小写化后的稳定 messageKey；空白和全角字符会被拒绝而非修剪 */
    public static String messageKey(String value) {
        if (value == null || value.isBlank() || value.length() > 160
                || !value.equals(value.strip()) || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("messageKey 不得为空、含空白或超过 160 个字符");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!MESSAGE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("messageKey 仅允许 ASCII 小写字母、数字及 . _ - 分段");
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

    /** @return 仅执行边界校验和首尾空白规范化后的译文 */
    public static String translationText(String value) {
        return required(value, "messageText", 4096);
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
