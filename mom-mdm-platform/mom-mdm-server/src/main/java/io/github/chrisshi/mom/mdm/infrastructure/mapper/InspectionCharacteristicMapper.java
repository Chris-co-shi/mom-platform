package io.github.chrisshi.mom.mdm.infrastructure.mapper;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.InspectionCharacteristicEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 数值型检验特性的 MDM 持久化入口。 */
@Mapper
public interface InspectionCharacteristicMapper extends MomBaseMapper<InspectionCharacteristicEntity> {
    /** 按主键锁定特性，保证质量标准写入与特性停用串行。 */
    @Select("""
            SELECT id, code, name, default_uom_id, default_uom_version, enabled, version,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_inspection_characteristic WHERE id = #{id} FOR UPDATE
            """)
    InspectionCharacteristicEntity selectByIdForUpdate(@Param("id") String id);
}
