package io.github.chrisshi.mom.system.api;

import java.time.Instant;

/**
 * 用户显示偏好的只读有效值契约。
 *
 * <p>该 DTO 不暴露数据库 ID 或 userId，也不承载 Role、Permission、Scope、Token 或 Factory 业务事实。
 * Source 明确区分用户覆盖与平台默认，供 Web/Mobile 后续安全合并白名单字段。</p>
 */
public record ResolvedUserPreference(
        String locale,
        String displayTimezone,
        UserThemeMode themeMode,
        UserDensity density,
        int pageSize,
        long version,
        boolean persisted,
        Instant updatedAt,
        Sources sources) {

    /** 每个显示字段的有效值来源。 */
    public enum Source {
        USER,
        PLATFORM_DEFAULT
    }

    /** 显示偏好五个白名单字段的来源集合。 */
    public record Sources(
            Source locale,
            Source displayTimezone,
            Source themeMode,
            Source density,
            Source pageSize) {
    }
}
