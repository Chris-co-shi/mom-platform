package io.github.chrisshi.mom.system.domain.i18n;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dynamic I18n 的纯领域校验、占位符分析与确定性快照构建规则。
 *
 * <p>该类型属于 System Domain，不依赖 Spring、Web、数据库或 Jackson。V1 Locale 固定为
 * zh-CN/en-US；消息只作为普通文本保存，拒绝表达式式占位符和非法控制字符。快照按 Key 字典序构建，
 * 相同输入在任意 JVM Locale 下产生相同 UTF-8 JSON 与 SHA-256。类型无共享可变状态，可并发调用；
 * 校验失败时直接拒绝，不做静默清洗或降级。</p>
 */
public final class SystemI18nRules {
    public static final String ZH_CN = "zh-CN";
    public static final String EN_US = "en-US";
    public static final Set<String> SUPPORTED_LOCALES = Set.of(ZH_CN, EN_US);
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern MESSAGE_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.-]{0,127}");
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

    private SystemI18nRules() {
    }

    /** 校验并规范为小写稳定 applicationCode。 */
    public static String normalizeApplicationCode(String value) {
        return normalizeCode(value, "applicationCode");
    }

    /** 校验并规范为小写稳定 resourceCode。 */
    public static String normalizeResourceCode(String value) {
        return normalizeCode(value, "resourceCode");
    }

    /** 校验非空资源名称，保留业务需要的内部空格。 */
    public static String normalizeResourceName(String value) {
        return requireText(value, "resourceName", 200);
    }

    /** 校验 V1 BCP 47 Locale，禁止大小写归一化掩盖错误输入。 */
    public static String requireLocale(String value) {
        if (!SUPPORTED_LOCALES.contains(value)) {
            throw new IllegalArgumentException("locale 只支持 zh-CN 或 en-US");
        }
        return value;
    }

    /** 校验创建后不可变的消息 Key。 */
    public static String requireMessageKey(String value) {
        String normalized = requireText(value, "messageKey", 128);
        if (!MESSAGE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("messageKey 格式非法");
        }
        return normalized;
    }

    /** 校验可选说明；空白说明转为 null。 */
    public static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, "description", 1000);
    }

    /**
     * 校验普通文本并返回其中占位符集合。
     *
     * @param value 待保存消息
     * @return 去重后的不可变占位符集合
     * @throws IllegalArgumentException 空值、超长、控制字符、表达式或括号语法非法
     */
    public static Set<String> validateMessageValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("messageValue 不能为空");
        }
        if (value.length() > 4096) {
            throw new IllegalArgumentException("messageValue 长度不能超过 4096");
        }
        if (value.codePoints().anyMatch(code -> Character.isISOControl(code)
                && code != '\n' && code != '\r' && code != '\t')) {
            throw new IllegalArgumentException("messageValue 包含非法控制字符");
        }
        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (value.contains("${") || value.contains("#{")
                || lowerValue.contains("<script") || lowerValue.contains("javascript:")) {
            throw new IllegalArgumentException("messageValue 不允许表达式或 JavaScript 语法");
        }
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '}') {
                throw new IllegalArgumentException("messageValue 包含未配对右括号");
            }
            if (current != '{') {
                continue;
            }
            int end = value.indexOf('}', index + 1);
            if (end < 0) {
                throw new IllegalArgumentException("messageValue 包含未闭合占位符");
            }
            String name = value.substring(index + 1, end);
            if (!PLACEHOLDER_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Placeholder 必须匹配 [a-zA-Z][a-zA-Z0-9_]*");
            }
            placeholders.add(name);
            index = end;
        }
        return Collections.unmodifiableSet(placeholders);
    }

    /**
     * 由默认 Locale 与另一 Locale 草稿构建两份完整快照。
     *
     * @param defaultLocale 资源默认 Locale
     * @param enabledMessages 全部启用草稿
     * @return 固定含 zh-CN/en-US 的快照 Map
     * @throws IllegalArgumentException 无消息、缺默认 Locale 或同 Key 占位符不一致
     */
    public static Map<String, Snapshot> buildSnapshots(
            String defaultLocale, List<DraftValue> enabledMessages) {
        requireLocale(defaultLocale);
        if (enabledMessages == null || enabledMessages.isEmpty()) {
            throw new IllegalArgumentException("发布至少需要一个启用 Draft Message");
        }
        Map<String, Map<String, String>> byKey = new java.util.TreeMap<>();
        for (DraftValue message : enabledMessages) {
            String key = requireMessageKey(message.messageKey());
            String locale = requireLocale(message.locale());
            validateMessageValue(message.messageValue());
            String previous = byKey.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .put(locale, message.messageValue());
            if (previous != null) {
                throw new IllegalArgumentException("同一 messageKey/locale 存在重复草稿");
            }
        }

        Map<String, String> defaults = new LinkedHashMap<>();
        Map<String, String> alternatives = new LinkedHashMap<>();
        int fallbackCount = 0;
        String otherLocale = defaultLocale.equals(ZH_CN) ? EN_US : ZH_CN;
        for (Map.Entry<String, Map<String, String>> entry : byKey.entrySet()) {
            String defaultValue = entry.getValue().get(defaultLocale);
            if (defaultValue == null) {
                throw new IllegalArgumentException("每个 messageKey 必须包含 defaultLocale Message");
            }
            String alternative = entry.getValue().get(otherLocale);
            if (alternative != null
                    && !validateMessageValue(defaultValue).equals(validateMessageValue(alternative))) {
                throw new IllegalArgumentException("同一 messageKey 的 Locale Placeholder Set 必须一致");
            }
            defaults.put(entry.getKey(), defaultValue);
            if (alternative == null) {
                alternatives.put(entry.getKey(), defaultValue);
                fallbackCount++;
            } else {
                alternatives.put(entry.getKey(), alternative);
            }
        }
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        snapshots.put(defaultLocale, snapshot(defaults, 0));
        snapshots.put(otherLocale, snapshot(alternatives, fallbackCount));
        return Collections.unmodifiableMap(snapshots);
    }

    /** 以 Key 字典序构建确定性 JSON 与 SHA-256。 */
    public static Snapshot snapshot(Map<String, String> messages, int fallbackCount) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Snapshot messages 不能为空");
        }
        Map<String, String> sorted = new java.util.TreeMap<>(messages);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quoteJson(entry.getKey())).append(':').append(quoteJson(entry.getValue()));
        }
        json.append('}');
        return new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(sorted)), sorted.size(),
                fallbackCount, sha256(json.toString()), json.toString());
    }

    private static String normalizeCode(String value, String fieldName) {
        String normalized = requireText(value, fieldName, 64).toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " 必须为 2～64 位小写稳定 Code");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + maxLength);
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " 不允许控制字符");
        }
        return normalized;
    }

    private static String quoteJson(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(code -> {
            switch (code) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (code < 0x20) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", code));
                    } else {
                        result.appendCodePoint(code);
                    }
                }
            }
        });
        return result.append('"').toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    /** 发布校验使用的最小草稿值。 */
    public record DraftValue(String messageKey, String locale, String messageValue) {
    }

    /** 确定性且不可变的单 Locale 完整发布快照。 */
    public record Snapshot(
            Map<String, String> messages, int messageCount, int fallbackCount, String checksum, String json) {
    }
}
