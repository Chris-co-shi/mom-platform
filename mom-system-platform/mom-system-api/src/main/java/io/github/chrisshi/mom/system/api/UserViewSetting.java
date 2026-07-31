package io.github.chrisshi.mom.system.api;

import java.time.Instant;
import java.util.List;

/**
 * 用户受限视图设置的稳定只读契约。
 *
 * <p>结构只允许列、最多三项排序、受限 Filter 和白名单分页值；不暴露数据库 ID、userId 或任意 JSON
 * String。业务查询服务必须再次映射允许字段和 Operator，不能直接执行本契约内容。</p>
 */
public record UserViewSetting(
        String applicationCode,
        String viewKey,
        int schemaVersion,
        List<ColumnSetting> columns,
        List<SortSetting> sorts,
        List<FilterSetting> filters,
        Integer pageSize,
        boolean enabled,
        long version,
        boolean persisted,
        Instant updatedAt) {

    /** 受限列状态。 */
    public record ColumnSetting(String columnKey, boolean visible, int order, Integer width, Pinned pinned) {
    }

    /** 受限排序状态。 */
    public record SortSetting(String fieldKey, Direction direction, int priority) {
    }

    /** 受限 Saved Filter；values 只能是标量字符串数组。 */
    public record FilterSetting(String fieldKey, Operator operator, ValueType valueType, List<String> values) {
    }

    public enum Pinned { NONE, LEFT, RIGHT }

    public enum Direction { ASC, DESC }

    public enum Operator { EQ, NE, CONTAINS, STARTS_WITH, IN, BETWEEN, IS_NULL, IS_NOT_NULL }

    public enum ValueType { STRING, INTEGER, DECIMAL, BOOLEAN, DATE, INSTANT }
}
