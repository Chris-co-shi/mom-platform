package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountLineEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 盘点明细的受限持久化：普通读写复用 MomBaseMapper，实盘数量仅以受限 SQL 修改；
 * 数据库触发器继续阻止快照变化和非法生命周期写入。
 */
@Mapper
public interface InventoryCountLineMapper extends MomBaseMapper<InventoryCountLineEntity> {
    /** 账外实物须在 COUNTING 状态由数据库写入同一时间戳，避免手动绕过快照保护。 */
    @Insert("""
        INSERT INTO wms_inventory_count_line
          (id,task_id,material_id,material_code,material_name,uom_code,batch_code,sn_code,warehouse_id,
           warehouse_code,location_id,location_code,quality_status,warehouse_status,book_quantity,
           counted_quantity,snapshot_by,counted_by,counted_at)
        VALUES (#{line.id},#{line.taskId},#{line.materialId},#{line.materialCode},
                #{line.materialName},#{line.uomCode},#{line.batchCode},#{line.snCode},
                #{line.warehouseId},#{line.warehouseCode},#{line.locationId},#{line.locationCode},
                #{line.qualityStatus},#{line.warehouseStatus},0,#{line.countedQuantity},
                #{line.snapshotBy},#{line.countedBy},now())
        """)
    int insertExtra(@Param("line") InventoryCountLineEntity line);

    @Update("UPDATE wms_inventory_count_line SET counted_quantity=#{quantity},counted_by=#{actor},counted_at=now() WHERE id=#{lineId} AND task_id=#{taskId}")
    int recordCount(@Param("taskId") String taskId,@Param("lineId") String lineId,
                    @Param("quantity") BigDecimal quantity,@Param("actor") String actor);
}
