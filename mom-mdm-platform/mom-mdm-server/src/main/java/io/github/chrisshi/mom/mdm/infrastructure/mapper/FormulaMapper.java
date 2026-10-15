package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 配方身份的本地单表 Mapper；下一修订号分配需要锁定配方主行。 */
@Mapper
public interface FormulaMapper extends MomBaseMapper<FormulaEntity> {
    /** 按稳定主键锁定配方，串行分配同一配方的下一业务修订号。 */
    @Select("""
            SELECT id, code, name, plant_id, output_material_id, base_batch_quantity, base_uom_id,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_formula WHERE id = #{id} FOR UPDATE
            """)
    FormulaEntity selectByIdForUpdate(@Param("id") String id);
}
