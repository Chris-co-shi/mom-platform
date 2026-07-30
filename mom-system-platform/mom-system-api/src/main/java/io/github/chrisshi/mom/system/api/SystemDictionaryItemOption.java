package io.github.chrisshi.mom.system.api;

import java.time.Instant;

/**
 * System Dictionary 的有效选择项只读契约。
 *
 * <p>调用方只以 dictionaryCode + itemCode 保存稳定 Reference，itemLabel 仅是单一 fallback 展示文本。
 * 契约不暴露数据库 ID、持久化类型或任意展示元数据；网络或数据库不可用时由调用链显式失败。</p>
 */
public record SystemDictionaryItemOption(
        String dictionaryCode,
        String itemCode,
        String itemLabel,
        int sortOrder,
        long version,
        Instant updatedAt) {
}
