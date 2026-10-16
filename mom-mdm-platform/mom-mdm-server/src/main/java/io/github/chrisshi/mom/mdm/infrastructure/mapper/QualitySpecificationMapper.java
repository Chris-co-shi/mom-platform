package io.github.chrisshi.mom.mdm.infrastructure.mapper;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.QualitySpecificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface QualitySpecificationMapper extends MomBaseMapper<QualitySpecificationEntity> {
    /** 锁定标准身份，串行创建下一修订。 */
    @Select("""
            SELECT id, code, name, plant_id, material_id, inspection_usage, plant_version,
                   material_revision_no, material_version, created_at, created_by, updated_at, updated_by
              FROM mdm_quality_specification WHERE id = #{id} FOR UPDATE
            """)
    QualitySpecificationEntity selectByIdForUpdate(@Param("id") String id);
}
