package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * System Dictionary 头部的 MyBatis Mapper。
 *
 * <p>简单写入使用 MomBaseMapper 实体路径以触发统一审计与乐观锁；分页 SQL 参数化并固定排序，不接受
 * 客户端排序字段。Mapper 只访问当前 search_path 的 mom_system，不建立跨 Schema 查询。</p>
 */
@Mapper
public interface SystemDictionaryMapper extends MomBaseMapper<SystemDictionaryEntity> {

    /** 按规范 Code 精确读取唯一字典。 */
    @Select("""
            SELECT id, dictionary_code, dictionary_name, enabled, version, description,
                   created_by, created_at, updated_by, updated_at
              FROM system_dictionary
             WHERE dictionary_code = #{dictionaryCode}
            """)
    SystemDictionaryEntity selectByCode(@Param("dictionaryCode") String dictionaryCode);

    /** 执行有限条件和固定排序分页查询。 */
    List<SystemDictionaryEntity> selectPage(
            @Param("dictionaryCode") String dictionaryCode,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 统计与分页条件完全一致的字典记录数。 */
    long countPage(@Param("dictionaryCode") String dictionaryCode, @Param("enabled") Boolean enabled);
}
