package io.github.chrisshi.mom.iam.application.admin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** IAM Admin 协议格式、长度、分页和批量上限校验。 */
public final class IamAdminCommandValidator {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9._@-]{2,119}");
    private static final Pattern MOM_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_BATCH_IDS = 200;

    private IamAdminCommandValidator() { }

    public static int pageSize(int value) {
        return value <= 0 ? 50 : Math.min(value, MAX_PAGE_SIZE);
    }

    public static int pageOffset(int value) {
        return Math.max(0, value);
    }

    public static String requireUsername(String value) {
        String normalized = requireText(value, "username", 120);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("username 格式无效");
        }
        return normalized;
    }

    public static String requireInitialPassword(String value) {
        if (value == null || value.length() < 12 || value.length() > 200) {
            throw new IllegalArgumentException("初始凭证长度必须为 12～200 个字符");
        }
        return value;
    }

    public static String requireReason(String value, String name) {
        return requireText(value, name, 100);
    }

    public static String requireId(String value, String name) {
        String normalized = requireText(value, name, 128);
        if ((name.endsWith("Id") || name.endsWith("Ids"))
                && !MOM_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " 必须是 19 位以内正数字符串 ID");
        }
        return normalized;
    }

    public static Set<String> normalizedIds(Set<String> values, String name) {
        if (values == null) return Set.of();
        if (values.size() > MAX_BATCH_IDS) {
            throw new IllegalArgumentException(name + " 最多允许 " + MAX_BATCH_IDS + " 项");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) result.add(requireId(value, name));
        return Set.copyOf(result);
    }

    public static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(name + " 长度不能超过 " + max);
        }
        return normalized;
    }

    public static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException("文本长度不能超过 " + max);
        }
        return normalized;
    }

    public static String trim(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    public static String json(String key, String value) {
        return "{\"" + escape(key) + "\":\"" + escape(value) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
