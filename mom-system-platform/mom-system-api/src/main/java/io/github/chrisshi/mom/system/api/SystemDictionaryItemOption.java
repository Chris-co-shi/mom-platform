package io.github.chrisshi.mom.system.api;

/**
 * 供当前消费者按稳定字典 Code 获取的新选择项契约。
 *
 * <p>契约只暴露稳定机器值和展示值，不暴露数据库 ID、Entity 或持久化版本。调用方应保存
 * {@code key}，不得把 {@code value} 当作业务事实；已禁用条目不会出现在该列表中。</p>
 *
 * @param dictionaryCode 字典类型稳定 Code
 * @param key 条目稳定机器值
 * @param value 当前展示值
 * @param sortOrder 排序值
 */
public record SystemDictionaryItemOption(
        String dictionaryCode,
        String key,
        String value,
        int sortOrder) {
}
