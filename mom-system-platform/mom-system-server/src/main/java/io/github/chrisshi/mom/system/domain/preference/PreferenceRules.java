package io.github.chrisshi.mom.system.domain.preference;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * System 用户偏好 V1 的集中白名单与安全规则。
 *
 * <p>规则固定应用/视图/字段 Code、集合上限、唯一性、连续排序和 16 KiB Payload。它不访问数据库、
 * SecurityContext 或外部服务，所有入口都可重复执行且失败时不包含敏感原值。</p>
 */
public final class PreferenceRules {
    public static final int MAX_COLUMNS = 100;
    public static final int MAX_SORTS = 3;
    public static final int MAX_FILTERS = 20;
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    public static final String FORBIDDEN_MESSAGE =
            "User Preference 不允许保存 Secret、Credential 或 Authorization 数据";

    private static final Pattern APPLICATION_CODE = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
    private static final Pattern VIEW_KEY = Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$");
    private static final Pattern FIELD_KEY = Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$");
    private static final Pattern JWT_LIKE = Pattern.compile("^[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}$");
    private static final Set<String> FORBIDDEN_SEGMENTS = Set.of(
            "password", "secret", "credential", "token", "authorization", "cookie", "session",
            "private", "key", "api", "client", "refresh", "access", "jwt", "claim", "permission", "role", "scope");

    private PreferenceRules() {
    }

    /** 校验稳定小写 kebab-case applicationCode。 */
    public static String requireApplicationCode(String value) {
        if (value == null || value.length() > 64 || !APPLICATION_CODE.matcher(value).matches()) {
            throw new PreferenceValidationException("invalid_application_code", "applicationCode 格式非法");
        }
        rejectSecretLike(value);
        return value;
    }

    /** 校验稳定小写点分 viewKey。 */
    public static String requireViewKey(String value) {
        if (value == null || value.length() > 128 || !VIEW_KEY.matcher(value).matches()) {
            throw new PreferenceValidationException("invalid_view_key", "viewKey 格式非法");
        }
        rejectSecretLike(value);
        return value;
    }

    /** 校验列/排序/过滤使用的稳定字段 Code。 */
    public static String requireFieldKey(String value, String code) {
        if (value == null || value.length() > 128 || !FIELD_KEY.matcher(value).matches()) {
            throw new PreferenceValidationException(code, "字段 Key 格式非法");
        }
        rejectSecretLike(value);
        return value;
    }

    /** 校验列/排序/Filter 集合的上限、唯一性和连续优先级。 */
    public static void validateView(
            List<ColumnSetting> columns, List<SortSetting> sorts, List<FilterSetting> filters) {
        if (columns == null || columns.size() > MAX_COLUMNS) {
            throw new PreferenceValidationException("invalid_column_setting", "列设置最多 100 项");
        }
        if (sorts == null || sorts.size() > MAX_SORTS) {
            throw new PreferenceValidationException("invalid_sort_setting", "排序最多 3 项");
        }
        if (filters == null || filters.size() > MAX_FILTERS) {
            throw new PreferenceValidationException("invalid_filter_setting", "Filter 最多 20 项");
        }
        requireUnique(columns.stream().map(ColumnSetting::columnKey).toList(), "invalid_column_setting");
        requireUnique(columns.stream().map(ColumnSetting::order).toList(), "invalid_column_setting");
        requireUnique(sorts.stream().map(SortSetting::fieldKey).toList(), "invalid_sort_setting");
        requireUnique(filters.stream().map(FilterSetting::fieldKey).toList(), "invalid_filter_setting");
        List<Integer> priorities = sorts.stream().map(SortSetting::priority).sorted().toList();
        for (int index = 0; index < priorities.size(); index++) {
            if (priorities.get(index) != index) {
                throw new PreferenceValidationException("invalid_sort_setting", "排序 priority 必须从 0 连续递增");
            }
        }
    }

    /** 校验受控 JSON 编码后的总 Payload 不超过 16 KiB。 */
    public static void requirePayloadSize(String... jsonParts) {
        long size = 0;
        for (String json : jsonParts) {
            size += json == null ? 0 : json.getBytes(StandardCharsets.UTF_8).length;
        }
        if (size > MAX_PAYLOAD_BYTES) {
            throw new PreferenceValidationException("payload_too_large", "视图设置 Payload 超过 16 KiB");
        }
    }

    /** 判断 Filter 标量是否明显试图保存凭据、Token 或 Cookie。 */
    static boolean looksSensitiveValue(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("bearer ") || lower.startsWith("basic ")
                || lower.contains("access_token=") || lower.contains("refresh_token=")
                || lower.contains("password=") || lower.contains("client_secret=")
                || JWT_LIKE.matcher(value).matches();
    }

    private static void rejectSecretLike(String value) {
        String[] segments = value.toLowerCase(Locale.ROOT).split("[.-]");
        for (String segment : segments) {
            if (FORBIDDEN_SEGMENTS.contains(segment)) {
                throw new PreferenceValidationException("forbidden_preference_key", FORBIDDEN_MESSAGE);
            }
        }
    }

    private static void requireUnique(List<?> values, String code) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new PreferenceValidationException(code, "视图设置存在重复 Key 或顺序");
        }
    }
}
