package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * System Dictionary Item 的 MyBatis Mapper。
 *
 * <p>所有查询限定 dictionaryId，Consumer Active List 固定按 sort_order、item_code、id 排序并只读取
 * enabled Item。Dictionary enabled 由 Application 判断；Mapper 不加载完整聚合或跨 Schema 对象。</p>
 */
@Mapper
public interface SystemDictionaryItemMapper extends MomBaseMapper<SystemDictionaryItemEntity> {

    /** 按父字典与条目 ID 精确读取，防止路径关系穿透。 */
    @Select("""
            SELECT id, dictionary_id, item_code, item_label, sort_order, enabled, version, description,
                   created_by, created_at, updated_by, updated_at
              FROM system_dictionary_item
             WHERE dictionary_id = #{dictionaryId} AND id = #{itemId}
            """)
    SystemDictionaryItemEntity selectByDictionaryAndId(
            @Param("dictionaryId") String dictionaryId, @Param("itemId") String itemId);

    /** 按父字典与稳定 Item Code 精确读取，包括禁用记录。 */
    @Select("""
            SELECT id, dictionary_id, item_code, item_label, sort_order, enabled, version, description,
                   created_by, created_at, updated_by, updated_at
              FROM system_dictionary_item
             WHERE dictionary_id = #{dictionaryId} AND item_code = #{itemCode}
            """)
    SystemDictionaryItemEntity selectByDictionaryAndCode(
            @Param("dictionaryId") String dictionaryId, @Param("itemCode") String itemCode);

    /** 读取启用 Item 的固定排序 Active List。 */
    @Select("""
            SELECT id, dictionary_id, item_code, item_label, sort_order, enabled, version, description,
                   created_by, created_at, updated_by, updated_at
              FROM system_dictionary_item
             WHERE dictionary_id = #{dictionaryId} AND enabled = true
             ORDER BY sort_order, item_code, id
            """)
    List<SystemDictionaryItemEntity> selectActive(@Param("dictionaryId") String dictionaryId);

    /** 执行单字典内有限条件和固定排序分页查询。 */
    List<SystemDictionaryItemEntity> selectPage(
            @Param("dictionaryId") String dictionaryId,
            @Param("itemCode") String itemCode,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 统计与 Item 分页条件完全一致的记录数。 */
    long countPage(
            @Param("dictionaryId") String dictionaryId,
            @Param("itemCode") String itemCode,
            @Param("enabled") Boolean enabled);
}
