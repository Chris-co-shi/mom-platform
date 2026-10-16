package io.github.chrisshi.mom.mdm.infrastructure.mapper;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.SpecificationRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface SpecificationRevisionMapper extends MomBaseMapper<SpecificationRevisionEntity> {
    /** 锁定具体修订供状态转换及技术版本校验。 */
    @Select("""
            SELECT id, specification_id, revision_no, status, lock_version, released_at, obsolete_at,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_specification_revision WHERE id = #{id} FOR UPDATE
            """)
    SpecificationRevisionEntity selectByIdForUpdate(@Param("id") String id);
}
