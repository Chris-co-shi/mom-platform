package io.github.chrisshi.mom.system.domain.preference;

import java.time.Instant;

/**
 * 单个 IAM 用户引用对应的显示偏好聚合。
 *
 * <p>所有覆盖字段可为 null，表示回到 Platform Default；聚合不保存授权、Factory Scope 或业务事实。
 * 版本由 PostgreSQL/MyBatis-Plus CAS 推进，Domain 对象本身不可变、线程安全。</p>
 */
public record UserPreference(
        String id,
        String userId,
        SupportedLocale locale,
        DisplayTimezone displayTimezone,
        ThemeMode themeMode,
        Density density,
        PageSize pageSize,
        long version,
        Instant updatedAt) {

    public UserPreference {
        if (userId == null || userId.isBlank() || userId.length() > 19 || version < 0) {
            throw new IllegalArgumentException("用户引用或版本非法");
        }
    }

    /** 构造首次持久化的用户覆盖，版本固定为零。 */
    public static UserPreference create(
            String userId, SupportedLocale locale, DisplayTimezone timezone, ThemeMode theme,
            Density density, PageSize pageSize) {
        return new UserPreference(null, userId, locale, timezone, theme, density, pageSize, 0, null);
    }

    /** 使用调用方当前版本替换全部五个白名单覆盖字段。 */
    public UserPreference replace(
            long expectedVersion, SupportedLocale locale, DisplayTimezone timezone, ThemeMode theme,
            Density density, PageSize pageSize) {
        requireVersion(expectedVersion);
        return new UserPreference(id, userId, locale, timezone, theme, density, pageSize, version, updatedAt);
    }

    /** 清空全部用户覆盖，保留行和乐观版本。 */
    public UserPreference reset(long expectedVersion) {
        return replace(expectedVersion, null, null, null, null, null);
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw new StalePreferenceVersionException();
        }
    }
}
