package io.github.chrisshi.mom.system.domain.dictionary;

import java.util.List;
import java.util.Optional;

/**
 * System Dictionary Item 的独立领域持久化端口。
 *
 * <p>端口按 dictionaryId 有界操作单项或分页，不要求加载完整集合。Active List 固定排序且只读取启用
 * Item；Dictionary 自身 enabled 由 Application 单独判定。数据库错误不通过缓存或默认值降级。</p>
 */
public interface SystemDictionaryItemRepository {

    /** 按字典内部 ID 与条目内部 ID 读取，防止跨字典路径误用。 */
    Optional<SystemDictionaryItem> findById(String dictionaryId, String itemId);

    /** 按字典内部 ID 与规范 Item Code 精确读取，包括禁用记录。 */
    Optional<SystemDictionaryItem> findByCode(String dictionaryId, String itemCode);

    /** 插入条目并返回数据库填充后的完整快照。 */
    SystemDictionaryItem insert(SystemDictionaryItem item);

    /** 使用实体 Version CAS 更新 Label、排序与说明；失败返回 false。 */
    boolean update(SystemDictionaryItem item);

    /** 使用实体 Version CAS 修改条目状态；失败返回 false。 */
    boolean updateStatus(SystemDictionaryItem item);

    /** 查询启用条目，固定按 sortOrder、itemCode、id 排序。 */
    List<SystemDictionaryItem> findActive(String dictionaryId);

    /** 按有限精确条件分页读取管理列表。 */
    ItemPage findPage(ItemQuery query);

    /** Infrastructure 无关的有限 Item 分页条件。 */
    record ItemQuery(String dictionaryId, String itemCode, Boolean enabled, int page, int size) {
    }

    /** Infrastructure 无关的 Item 分页结果。 */
    record ItemPage(List<SystemDictionaryItem> items, long total, int page, int size) {
        public ItemPage {
            items = List.copyOf(items);
        }
    }
}
