package io.github.chrisshi.mom.system.application.dictionary;

import java.time.Instant;

/**
 * Dictionary Application 的写入命令与管理 View 集合。
 *
 * <p>这些对象不感知 HTTP、Mapper 或数据库 Wrapper；数据库 ID 只出现在 System 管理 View，跨服务读取
 * 使用 mom-system-api 中的稳定 Code 契约。</p>
 */
public final class DictionaryModels {
    private DictionaryModels() {
    }

    /** 创建字典类型命令。 */
    public record CreateType(String code, String name, Boolean enabled, String description) {
    }

    /** 更新字典类型命令；稳定 Code 不可修改。 */
    public record UpdateType(String name, String description, Long version) {
    }

    /** 创建字典条目命令。 */
    public record CreateItem(String key, String value, Integer sortOrder, Boolean enabled, String description) {
    }

    /** 更新字典条目命令；稳定 Key 与父类型不可修改。 */
    public record UpdateItem(String value, Integer sortOrder, String description, Long version) {
    }

    /** 版本化启停命令。 */
    public record ChangeStatus(Boolean enabled, Long version) {
    }

    /** System 管理端字典类型视图。 */
    public record TypeView(String id, String code, String name, boolean enabled, String description,
                           long version, Instant updatedAt) {
    }

    /** System 管理端字典条目视图。 */
    public record ItemView(String id, String typeId, String key, String value, int sortOrder, boolean enabled,
                           String description, long version, Instant updatedAt) {
    }
}
