package io.github.chrisshi.mom.system.domain.preference;

/**
 * 单个受限视图列设置。
 *
 * <p>columnKey 是客户端稳定 Code，不是数据库字段名；Domain 只恢复显示状态，不授权查询字段。</p>
 */
public record ColumnSetting(String columnKey, boolean visible, int order, Integer width, Pinned pinned) {
    public ColumnSetting {
        columnKey = PreferenceRules.requireFieldKey(columnKey, "invalid_column_setting");
        if (order < 0 || width != null && (width < 50 || width > 1000) || pinned == null) {
            throw new PreferenceValidationException("invalid_column_setting", "列顺序、宽度或固定位置非法");
        }
    }

    /** 列固定位置白名单。 */
    public enum Pinned { NONE, LEFT, RIGHT }
}
