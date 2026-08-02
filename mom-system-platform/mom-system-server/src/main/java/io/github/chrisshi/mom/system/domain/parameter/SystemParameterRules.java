package io.github.chrisshi.mom.system.domain.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * System Parameter 的键与作用域规范化规则。
 *
 * <p>该纯领域类型不依赖 Spring、Web 或持久化。所有入口在查询或写入前复用同一规则；明显敏感词按完整
 * Key Segment 拒绝，避免把 System Parameter 误用为 Secret/Credential Store。规则无共享可变状态，线程安全。</p>
 */
public final class SystemParameterRules {
    public static final String GLOBAL_SCOPE_CODE = "";
    private static final Pattern PARAMETER_KEY = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern APPLICATION_CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Set<String> SENSITIVE_SEGMENTS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential",
            "private-key", "private_key", "privatekey", "client-secret", "client_secret", "clientsecret",
            "access-key", "access_key", "accesskey", "api-key", "api_key", "apikey");

    private SystemParameterRules() {
    }

    /**
     * 规范化并验证参数键。
     *
     * @param value 原始键
     * @return 小写规范键
     * @throws IllegalArgumentException 键为空、超长、格式非法或包含敏感词段
     */
    public static String normalizeKey(String value) {
        String normalized = requireText(value, "parameterKey", 128).toLowerCase(Locale.ROOT);
        if (!PARAMETER_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("parameterKey 格式非法");
        }
        if (containsSensitiveSegment(normalized)) {
            throw new IllegalArgumentException("System Parameter 不允许保存 Secret 或 Credential");
        }
        return normalized;
    }

    /**
     * 按作用域规范化 scopeCode。
     *
     * @param scopeType 作用域类型
     * @param scopeCode 原始作用域编码
     * @return GLOBAL 的空字符串或 APPLICATION 的小写 kebab-case 编码
     * @throws IllegalArgumentException 作用域组合非法
     */
    public static String normalizeScopeCode(ParameterScopeType scopeType, String scopeCode) {
        if (scopeType == null) {
            throw new IllegalArgumentException("scopeType 不能为空");
        }
        if (scopeType == ParameterScopeType.GLOBAL) {
            if (scopeCode != null && !scopeCode.isBlank()) {
                throw new IllegalArgumentException("GLOBAL scopeCode 必须为空");
            }
            return GLOBAL_SCOPE_CODE;
        }
        String normalized = requireText(scopeCode, "scopeCode", 64).toLowerCase(Locale.ROOT);
        if (!APPLICATION_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("APPLICATION scopeCode 必须是 2～64 位小写 kebab-case applicationCode");
        }
        return normalized;
    }

    /** 规范化可选描述；空白转为 null，普通空格不做额外重写。 */
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

    private static boolean containsSensitiveSegment(String key) {
        if (SENSITIVE_SEGMENTS.contains(key)) {
            return true;
        }
        for (String segment : key.split("[.]")) {
            if (SENSITIVE_SEGMENTS.contains(segment)) {
                return true;
            }
            for (String sensitive : SENSITIVE_SEGMENTS) {
                if (segment.equals(sensitive)
                        || segment.startsWith(sensitive + "-") || segment.endsWith("-" + sensitive)
                        || segment.startsWith(sensitive + "_") || segment.endsWith("_" + sensitive)) {
                    return true;
                }
            }
        }
        return false;
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
