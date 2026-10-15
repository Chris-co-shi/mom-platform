package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.ProductionVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/** ProductionVersion 单表 Mapper；发布事务显式锁定目标和同范围已发布版本。 */
@Mapper
public interface ProductionVersionMapper extends MomBaseMapper<ProductionVersionEntity> {
    /** 锁定目标版本以串行状态转换和草稿修改。 */
    @Select("""
            SELECT id, code, name, business_version, plant_id, output_material_id,
                   formula_revision_id, routing_revision_id, valid_from, valid_to,
                   status, lock_version, released_at, obsolete_at,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_production_version WHERE id=#{id} FOR UPDATE
            """)
    ProductionVersionEntity selectByIdForUpdate(@Param("id") String id);

    /** 锁定同范围所有重叠 RELEASED 版本；Material 主行锁同时阻止并发发布幻读。 */
    @Select("""
            SELECT id, code, name, business_version, plant_id, output_material_id,
                   formula_revision_id, routing_revision_id, valid_from, valid_to,
                   status, lock_version, released_at, obsolete_at,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_production_version
             WHERE plant_id=#{plantId} AND output_material_id=#{outputMaterialId}
               AND status='RELEASED' AND id<>#{excludedId}
               AND daterange(valid_from, valid_to, '[)') && daterange(#{validFrom}, #{validTo}, '[)')
             ORDER BY id FOR UPDATE
            """)
    List<ProductionVersionEntity> selectReleasedOverlapsForUpdate(
            @Param("plantId") String plantId, @Param("outputMaterialId") String outputMaterialId,
            @Param("validFrom") LocalDate validFrom, @Param("validTo") LocalDate validTo,
            @Param("excludedId") String excludedId);
}
