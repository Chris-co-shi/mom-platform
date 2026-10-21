package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.WarehouseInventoryStateEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * WMS 仓库库存状态的普通 CRUD 复用 mom-data。
 * 初始化抢占依赖 PostgreSQL ON CONFLICT；状态行锁需要 FOR UPDATE，其余条件变更由 Application 调用 MP。
 */
@Mapper
public interface WarehouseInventoryStateMapper extends MomBaseMapper<WarehouseInventoryStateEntity> {
    @Insert("""
        INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state,updated_by)
        VALUES(#{id},#{warehouseId},#{warehouseCode},'INITIALIZING',#{actor})
        ON CONFLICT(warehouse_id) DO NOTHING
        """)
    int initialize(@Param("id") String id, @Param("warehouseId") String warehouseId,
                   @Param("warehouseCode") String warehouseCode, @Param("actor") String actor);

    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId} FOR UPDATE")
    String lockWarehouse(@Param("warehouseId") String warehouseId);
}
