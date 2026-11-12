package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.WarehouseInventoryStateEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.dao.DuplicateKeyException;

/**
 * WMS 仓库库存状态的普通 CRUD 复用 mom-data。
 * 初始化抢占由仓库唯一键保护；其余条件变更由 Application 调用 MP。
 */
@Mapper
public interface WarehouseInventoryStateMapper extends MomBaseMapper<WarehouseInventoryStateEntity> {
    /** 领取仓库初始化记录，已经存在时继续读取原记录。 */
    default int initialize(String id, String warehouseId, String warehouseCode, String actor) {
        try {
            return insertInitialState(id, warehouseId, warehouseCode, actor);
        } catch (DuplicateKeyException failure) {
            if (WmsMysqlDuplicateKey.matches(failure, "wms_warehouse_inventory_state",
                    "uk_wms_warehouse_inventory_state_warehouse_id")) {
                return 0;
            }
            throw failure;
        }
    }

    /** 创建初始化中的仓库状态行。 */
    @Insert(value = """
        INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state,updated_by)
        VALUES(#{id},#{warehouseId},#{warehouseCode},'INITIALIZING',#{actor})
        """, databaseId = "mysql")
    @Insert("""
        INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state,updated_by)
        VALUES(#{id},#{warehouseId},#{warehouseCode},'INITIALIZING',#{actor})
        ON CONFLICT(warehouse_id) DO NOTHING
        """)
    int insertInitialState(@Param("id") String id, @Param("warehouseId") String warehouseId,
                   @Param("warehouseCode") String warehouseCode, @Param("actor") String actor);

    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId} FOR UPDATE")
    String lockWarehouse(@Param("warehouseId") String warehouseId);
}
