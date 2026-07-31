package io.github.chrisshi.mom.system.domain.preference;

/**
 * 单个受限视图排序设置。
 *
 * <p>fieldKey 只供未来客户端恢复状态，不能直接拼接或执行为后端 SQL。</p>
 */
public record SortSetting(String fieldKey, Direction direction, int priority) {
    public SortSetting {
        fieldKey = PreferenceRules.requireFieldKey(fieldKey, "invalid_sort_setting");
        if (direction == null || priority < 0) {
            throw new PreferenceValidationException("invalid_sort_setting", "排序方向或优先级非法");
        }
    }

    /** 排序方向白名单。 */
    public enum Direction { ASC, DESC }
}
