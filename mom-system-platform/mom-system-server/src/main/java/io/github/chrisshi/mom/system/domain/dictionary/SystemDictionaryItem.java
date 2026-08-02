package io.github.chrisshi.mom.system.domain.dictionary;

import java.time.Instant;

/**
 * System 非权威通用字典条目领域对象。
 *
 * <p>dictionaryId 只用于 System 内部关联；跨服务 Reference 必须保存 dictionaryCode + itemCode。条目不含
 * Metadata、Tree、Alias、Locale 或任意额外属性。实例不可变，版本 CAS 由持久化 Adapter 执行。</p>
 */
public record SystemDictionaryItem(
        String id,
        String dictionaryId,
        String itemCode,
        String itemLabel,
        int sortOrder,
        boolean enabled,
        long version,
        String description,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {

    /** 建立尚未持久化的新条目；dictionaryId 和 itemCode 创建后不可修改。 */
    public static SystemDictionaryItem newItem(
            String dictionaryId, String itemCode, String itemLabel, int sortOrder,
            boolean enabled, String description) {
        return new SystemDictionaryItem(null, dictionaryId, itemCode, itemLabel, sortOrder, enabled, 0L,
                description, null, null, null, null);
    }

    /** 按客户端版本更新 Label、排序与说明，稳定 Reference 保持不可变。 */
    public SystemDictionaryItem update(
            long expectedVersion, String newLabel, int newSortOrder, String newDescription) {
        return new SystemDictionaryItem(id, dictionaryId, itemCode, newLabel, newSortOrder, enabled,
                expectedVersion, newDescription, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 按客户端版本启用或禁用条目。 */
    public SystemDictionaryItem changeStatus(long expectedVersion, boolean newEnabled) {
        return new SystemDictionaryItem(id, dictionaryId, itemCode, itemLabel, sortOrder, newEnabled,
                expectedVersion, description, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 计算字典和条目两级状态的有效启用结果，无副作用。 */
    public boolean effectiveEnabled(boolean dictionaryEnabled) {
        return dictionaryEnabled && enabled;
    }
}
