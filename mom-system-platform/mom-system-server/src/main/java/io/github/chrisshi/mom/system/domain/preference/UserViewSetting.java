package io.github.chrisshi.mom.system.domain.preference;

import java.time.Instant;
import java.util.List;

/**
 * 单用户、单应用、单视图的类型化设置聚合。
 *
 * <p>聚合只保存受限显示状态；Reset 置 disabled 并清空覆盖，不删除记录。对象不可变且不依赖 Jackson，
 * Domain/Application 永不处理客户端任意 JSON。</p>
 */
public record UserViewSetting(
        String id,
        String userId,
        String applicationCode,
        String viewKey,
        int schemaVersion,
        List<ColumnSetting> columns,
        List<SortSetting> sorts,
        List<FilterSetting> filters,
        PageSize pageSize,
        boolean enabled,
        long version,
        Instant updatedAt) {

    public UserViewSetting {
        if (userId == null || userId.isBlank() || userId.length() > 19 || schemaVersion < 1 || version < 0) {
            throw new IllegalArgumentException("视图用户引用、schemaVersion 或版本非法");
        }
        applicationCode = PreferenceRules.requireApplicationCode(applicationCode);
        viewKey = PreferenceRules.requireViewKey(viewKey);
        columns = List.copyOf(columns);
        sorts = List.copyOf(sorts);
        filters = List.copyOf(filters);
        PreferenceRules.validateView(columns, sorts, filters);
    }

    /** 构造首次保存且启用的视图设置。 */
    public static UserViewSetting create(
            String userId, String applicationCode, String viewKey, int schemaVersion,
            List<ColumnSetting> columns, List<SortSetting> sorts, List<FilterSetting> filters, PageSize pageSize) {
        return new UserViewSetting(null, userId, applicationCode, viewKey, schemaVersion,
                columns, sorts, filters, pageSize, true, 0, null);
    }

    /** 使用当前版本替换类型化视图覆盖并重新启用。 */
    public UserViewSetting replace(
            long expectedVersion, int newSchemaVersion, List<ColumnSetting> newColumns,
            List<SortSetting> newSorts, List<FilterSetting> newFilters, PageSize newPageSize) {
        requireVersion(expectedVersion);
        return new UserViewSetting(id, userId, applicationCode, viewKey, newSchemaVersion,
                newColumns, newSorts, newFilters, newPageSize, true, version, updatedAt);
    }

    /** 清空覆盖并禁用记录；读取方据此返回默认空视图。 */
    public UserViewSetting reset(long expectedVersion) {
        requireVersion(expectedVersion);
        return new UserViewSetting(id, userId, applicationCode, viewKey, schemaVersion,
                List.of(), List.of(), List.of(), null, false, version, updatedAt);
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw new StalePreferenceVersionException();
        }
    }
}
