package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 配方业务修订 Mapper；发布/废止与草稿修改共享主键行锁。 */
@Mapper
public interface FormulaRevisionMapper extends MomBaseMapper<FormulaRevisionEntity> {
    /** 读取并锁定一个修订，状态决策与写入保持同一本地事务。 */
    @Select("""
            SELECT id, formula_id, revision_no, status, lock_version, released_at, obsolete_at,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_formula_revision WHERE id = #{id} FOR UPDATE
            """)
    FormulaRevisionEntity selectByIdForUpdate(@Param("id") String id);
}
