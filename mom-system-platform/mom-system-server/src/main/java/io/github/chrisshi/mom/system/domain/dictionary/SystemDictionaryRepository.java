package io.github.chrisshi.mom.system.domain.dictionary;

import java.util.List;
import java.util.Optional;

/**
 * System Dictionary 头部的领域持久化端口。
 *
 * <p>Application 只依赖该端口，不感知 Mapper、Entity 或 SQL。实现共享 System 唯一 DataSource，必须
 * 检查版本更新行数并将唯一冲突转换为稳定业务语义；基础设施不可用时直接失败。</p>
 */
public interface SystemDictionaryRepository {

    /** 按 String 技术主键读取字典。 */
    Optional<SystemDictionary> findById(String id);

    /** 按规范 dictionaryCode 精确读取字典。 */
    Optional<SystemDictionary> findByCode(String dictionaryCode);

    /** 插入字典并返回数据库填充后的完整快照。 */
    SystemDictionary insert(SystemDictionary dictionary);

    /** 使用实体 Version CAS 更新名称与说明；失败返回 false。 */
    boolean update(SystemDictionary dictionary);

    /** 使用实体 Version CAS 修改状态；失败返回 false。 */
    boolean updateStatus(SystemDictionary dictionary);

    /** 按有限精确条件分页读取。 */
    DictionaryPage findPage(DictionaryQuery query);

    /** Infrastructure 无关的有限字典分页条件。 */
    record DictionaryQuery(String dictionaryCode, Boolean enabled, int page, int size) {
    }

    /** Infrastructure 无关的字典分页结果。 */
    record DictionaryPage(List<SystemDictionary> items, long total, int page, int size) {
        public DictionaryPage {
            items = List.copyOf(items);
        }
    }
}
