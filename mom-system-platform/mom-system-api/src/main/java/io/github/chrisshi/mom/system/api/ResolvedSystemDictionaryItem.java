package io.github.chrisshi.mom.system.api;

import java.time.Instant;

/**
 * System Dictionary 单项兼容读取契约。
 *
 * <p>即使字典或条目已禁用，只要稳定 Code 记录仍存在就返回，并显式给出两级启用状态及其合取结果。
 * version/updatedAt 描述条目当前版本；调用方不得把 Label、排序或数据库 ID 当作业务 Reference。</p>
 */
public record ResolvedSystemDictionaryItem(
        String dictionaryCode,
        String itemCode,
        String itemLabel,
        boolean dictionaryEnabled,
        boolean itemEnabled,
        boolean effectiveEnabled,
        long version,
        Instant updatedAt) {
}
