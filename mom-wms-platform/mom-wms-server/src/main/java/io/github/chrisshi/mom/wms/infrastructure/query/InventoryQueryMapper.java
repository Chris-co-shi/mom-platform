package io.github.chrisshi.mom.wms.infrastructure.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.DetailFilter;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.DetailRow;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.OverviewFilter;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.OverviewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 查询使用数据库 SUM 和 MyBatis-Plus 分页，不读取历史流水或跨 MDM Schema JOIN。 */
@Mapper
public interface InventoryQueryMapper {
    /** Material 汇总：可用仅 QUALIFIED+NORMAL；冻结属于不可用子集。 */
    @Select("""
            <script>
            SELECT material_id AS materialId, MIN(material_code) AS materialCode,
                   MIN(material_name) AS materialName, MIN(uom_code) AS uomCode,
                   SUM(quantity) AS totalQuantity,
                   SUM(CASE WHEN quality_status = 'QUALIFIED' AND warehouse_status = 'NORMAL'
                            THEN quantity ELSE 0 END) AS availableQuantity,
                   SUM(CASE WHEN quality_status = 'QUALIFIED' AND warehouse_status = 'NORMAL'
                            THEN 0 ELSE quantity END) AS unavailableQuantity,
                   SUM(CASE WHEN warehouse_status = 'FROZEN' THEN quantity ELSE 0 END) AS frozenQuantity
              FROM wms_inventory_balance
             WHERE quantity &gt; 0
               <if test="filter.materialId != null">AND material_id = #{filter.materialId}</if>
               <if test="filter.warehouseId != null">AND warehouse_id = #{filter.warehouseId}</if>
               <if test="filter.keyword != null">AND (material_code ILIKE '%' || #{filter.keyword} || '%'
                    OR material_name ILIKE '%' || #{filter.keyword} || '%')</if>
             GROUP BY material_id ORDER BY material_id
            </script>
            """)
    IPage<OverviewRow> overview(IPage<OverviewRow> page, @Param("filter") OverviewFilter filter);

    /** 组合过滤仅访问非零余额；主键提供稳定分页次序。 */
    @Select("""
            <script>
            SELECT id, material_id AS materialId, material_code AS materialCode,
                   material_name AS materialName, uom_code AS uomCode,
                   batch_code AS batchCode, sn_code AS snCode,
                   warehouse_id AS warehouseId, warehouse_code AS warehouseCode,
                   location_id AS locationId, location_code AS locationCode,
                   quality_status AS qualityStatus, warehouse_status AS warehouseStatus,
                   quantity
              FROM wms_inventory_balance
             WHERE quantity &gt; 0
               <if test="filter.materialId != null">AND material_id = #{filter.materialId}</if>
               <if test="filter.warehouseId != null">AND warehouse_id = #{filter.warehouseId}</if>
               <if test="filter.locationId != null">AND location_id = #{filter.locationId}</if>
               <if test="filter.batchCode != null">AND batch_code = #{filter.batchCode}</if>
               <if test="filter.snCode != null">AND sn_code = #{filter.snCode}</if>
               <if test="filter.qualityStatus != null">AND quality_status = #{filter.qualityStatus}</if>
               <if test="filter.warehouseStatus != null">AND warehouse_status = #{filter.warehouseStatus}</if>
             ORDER BY id
            </script>
            """)
    IPage<DetailRow> details(IPage<DetailRow> page, @Param("filter") DetailFilter filter);
}
