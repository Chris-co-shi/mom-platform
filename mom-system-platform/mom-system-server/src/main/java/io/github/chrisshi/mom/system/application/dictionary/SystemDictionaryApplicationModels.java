package io.github.chrisshi.mom.system.application.dictionary;

import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItem;

import java.time.Instant;
import java.util.List;

/**
 * System Dictionary 用例的 Command、Query 与管理只读 View。
 *
 * <p>这些类型不携带 HTTP、MyBatis Entity 或客户端审计字段。数据库 ID 只在 System 管理视图中出现；
 * Consumer 契约使用 dictionaryCode + itemCode，不允许依赖 ID、Label 或排序作为持久化语义。</p>
 */
public final class SystemDictionaryApplicationModels {
    private SystemDictionaryApplicationModels() {
    }

    /** 创建字典命令；dictionaryCode 创建后不可修改。 */
    public record CreateDictionaryCommand(
            String dictionaryCode, String dictionaryName, String description, Boolean enabled) {
    }

    /** 更新字典 fallback 名称和说明的版本化命令。 */
    public record UpdateDictionaryCommand(String dictionaryName, String description, Long version) {
    }

    /** 字典或条目共用的版本化启停命令。 */
    public record StatusCommand(Boolean enabled, Long version) {
    }

    /** 字典管理分页查询；Code 只做精确匹配。 */
    public record DictionaryPageQuery(String dictionaryCode, Boolean enabled, int page, int size) {
    }

    /** 创建条目命令；dictionaryId 来自路径，itemCode 创建后不可修改。 */
    public record CreateItemCommand(
            String itemCode, String itemLabel, Integer sortOrder, String description, Boolean enabled) {
    }

    /** 更新条目 fallback Label、排序和说明的版本化命令。 */
    public record UpdateItemCommand(String itemLabel, Integer sortOrder, String description, Long version) {
    }

    /** 条目管理分页查询；Item Code 只做精确匹配。 */
    public record ItemPageQuery(String itemCode, Boolean enabled, int page, int size) {
    }

    /** 字典管理视图，包含 System 内部管理 ID 与服务端审计。 */
    public record DictionaryView(
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
        /** 从领域对象映射为无持久化实现类型的管理视图。 */
        public static DictionaryView from(SystemDictionary dictionary) {
            return new DictionaryView(dictionary.id(), dictionary.dictionaryCode(), dictionary.dictionaryName(),
                    dictionary.enabled(), dictionary.version(), dictionary.description(), dictionary.createdBy(),
                    dictionary.createdAt(), dictionary.updatedBy(), dictionary.updatedAt());
        }
    }

    /** 字典管理分页结果。 */
    public record DictionaryPageView(List<DictionaryView> items, long total, int page, int size) {
        public DictionaryPageView {
            items = List.copyOf(items);
        }
    }

    /** 条目管理视图；数据库 ID 不进入 Consumer 只读契约。 */
    public record ItemView(
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
        /** 从领域对象映射为无持久化实现类型的管理视图。 */
        public static ItemView from(SystemDictionaryItem item) {
            return new ItemView(item.id(), item.dictionaryId(), item.itemCode(), item.itemLabel(), item.sortOrder(),
                    item.enabled(), item.version(), item.description(), item.createdBy(), item.createdAt(),
                    item.updatedBy(), item.updatedAt());
        }
    }

    /** 条目管理分页结果。 */
    public record ItemPageView(List<ItemView> items, long total, int page, int size) {
        public ItemPageView {
            items = List.copyOf(items);
        }
    }
}
