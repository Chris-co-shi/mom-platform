package io.github.chrisshi.mom.system.application.preference;

import java.util.List;

/** System 用户偏好用例的显式 Command 集合；不包含 userId、审计字段或任意 JSON。 */
public final class SystemUserPreferenceApplicationModels {
    private SystemUserPreferenceApplicationModels() {
    }

    /** 全量替换五个可空显示覆盖；version=0 表示首次创建。 */
    public record SaveDisplayPreferenceCommand(
            String locale, String displayTimezone, String themeMode, String density, Integer pageSize, Long version) {
    }

    /** Reset 当前用户显示偏好的版本化命令。 */
    public record ResetCommand(Long version) {
    }

    /** 全量保存类型化视图覆盖；路径拥有 applicationCode/viewKey。 */
    public record SaveViewSettingCommand(
            Integer schemaVersion,
            List<ColumnCommand> columns,
            List<SortCommand> sorts,
            List<FilterCommand> filters,
            Integer pageSize,
            Long version) {
        public SaveViewSettingCommand {
            columns = columns == null ? null : List.copyOf(columns);
            sorts = sorts == null ? null : List.copyOf(sorts);
            filters = filters == null ? null : List.copyOf(filters);
        }
    }

    /** 单列设置 Command。 */
    public record ColumnCommand(String columnKey, Boolean visible, Integer order, Integer width, String pinned) {
    }

    /** 单项排序 Command。 */
    public record SortCommand(String fieldKey, String direction, Integer priority) {
    }

    /** 单项 Filter Command；values 只能为字符串数组。 */
    public record FilterCommand(String fieldKey, String operator, String valueType, List<String> values) {
        public FilterCommand {
            values = values == null ? null : List.copyOf(values);
        }
    }
}
