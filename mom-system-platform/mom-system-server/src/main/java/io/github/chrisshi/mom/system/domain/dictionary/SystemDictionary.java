package io.github.chrisshi.mom.system.domain.dictionary;

import java.time.Instant;

/**
 * System 非权威通用字典领域对象。
 *
 * <p>对象只描述稳定 Code、fallback 名称、启停、版本与审计，不加载完整 Item 集合。写入由 Application
 * 本地事务编排，唯一约束与版本 CAS 兜底并发；实例不可变。数据库不可用时不缓存或伪造字典。</p>
 */
public record SystemDictionary(
        String id,
        String dictionaryCode,
        String dictionaryName,
        boolean enabled,
        long version,
        String description,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {

    /** 建立尚未持久化的新字典；ID 与审计由统一数据基础设施填充。 */
    public static SystemDictionary newDictionary(
            String dictionaryCode, String dictionaryName, boolean enabled, String description) {
        return new SystemDictionary(null, dictionaryCode, dictionaryName, enabled, 0L, description,
                null, null, null, null);
    }

    /** 按客户端版本更新可变名称与说明，稳定 dictionaryCode 保持不可变。 */
    public SystemDictionary update(long expectedVersion, String newName, String newDescription) {
        return new SystemDictionary(id, dictionaryCode, newName, enabled, expectedVersion, newDescription,
                createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 按客户端版本启用或禁用字典；该操作不修改任何 Item 状态。 */
    public SystemDictionary changeStatus(long expectedVersion, boolean newEnabled) {
        return new SystemDictionary(id, dictionaryCode, dictionaryName, newEnabled, expectedVersion, description,
                createdBy, createdAt, updatedBy, updatedAt);
    }
}
