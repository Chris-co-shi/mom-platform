package io.github.chrisshi.mom.system.api;

/**
 * 按稳定双 Code 解析字典历史值的只读契约。
 *
 * <p>即使条目已禁用，历史数据仍可获得当前展示值；{@code selectable} 仅表示能否用于新选择，
 * 不能用于否定历史记录的合法性。</p>
 *
 * @param dictionaryCode 字典类型稳定 Code
 * @param key 条目稳定机器值
 * @param value 当前展示值
 * @param selectable 字典类型与条目当前是否都启用
 */
public record ResolvedSystemDictionaryItem(
        String dictionaryCode,
        String key,
        String value,
        boolean selectable) {
}
