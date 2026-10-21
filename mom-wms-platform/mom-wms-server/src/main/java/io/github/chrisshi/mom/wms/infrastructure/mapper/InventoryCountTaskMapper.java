package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryBalanceEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountTaskEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.ArrayTypeHandler;

import java.util.List;

/**
 * 盘点任务普通 CRUD 复用 mom-data；状态条件写入、仓库锁及快照余额查询保留 PostgreSQL 原子 SQL。
 * 不在 Mapper 内编排业务事务。
 */
@Mapper
public interface InventoryCountTaskMapper extends MomBaseMapper<InventoryCountTaskEntity> {
    /** 按创建 Key 读取类型化原任务。 */
    @Select("SELECT * FROM wms_inventory_count_task WHERE request_key=#{requestKey}")
    @Results({
        @Result(column="location_ids", property="locationIds", typeHandler=ArrayTypeHandler.class),
        @Result(column="material_ids", property="materialIds", typeHandler=ArrayTypeHandler.class)
    })
    InventoryCountTaskEntity findByRequestKey(@Param("requestKey") String requestKey);

    /** 盘点任务行级锁：修改范围、状态和明细前必须持有。 */
    @Select("SELECT * FROM wms_inventory_count_task WHERE id=#{id} FOR UPDATE")
    @Results({
        @Result(column="location_ids", property="locationIds", typeHandler=ArrayTypeHandler.class),
        @Result(column="material_ids", property="materialIds", typeHandler=ArrayTypeHandler.class)
    })
    InventoryCountTaskEntity lockTask(@Param("id") String id);

    /** 按当前草稿状态条件修改范围；审计与版本仍显式在同一 SQL 中推进。 */
    @Update("""
        UPDATE wms_inventory_count_task SET
          location_ids=#{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},
          material_ids=#{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},
          updated_by=#{actor},updated_at=now(),version=version+1
        WHERE id=#{id} AND status='DRAFT'
        """)
    int updateScope(@Param("id") String id,@Param("locations") String[] locations,
                    @Param("materials") String[] materials,@Param("actor") String actor);

    /** 整仓独占锁利用唯一约束原子领取。 */
    @Insert("INSERT INTO wms_inventory_count_scope_lock(warehouse_id,task_id) VALUES (#{warehouseId},#{taskId}) ON CONFLICT (warehouse_id) DO NOTHING")
    int acquire(@Param("warehouseId") String warehouseId,@Param("taskId") String taskId);

    @Delete("DELETE FROM wms_inventory_count_scope_lock WHERE warehouse_id=#{warehouseId} AND task_id=#{taskId}")
    int release(@Param("warehouseId") String warehouseId,@Param("taskId") String taskId);

    /** 快照必须包含零余额，同时尊重可选库位和物料范围。 */
    @Select("""
        SELECT * FROM wms_inventory_balance b
        WHERE b.warehouse_id=#{warehouseId}
          AND (cardinality(#{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]) = 0
               OR b.location_id=ANY(#{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]))
          AND (cardinality(#{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]) = 0
               OR b.material_id=ANY(#{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]))
        ORDER BY b.id
        """)
    List<InventoryBalanceEntity> books(@Param("warehouseId") String warehouseId,
                                       @Param("locations") String[] locations,@Param("materials") String[] materials);

    /** 按目标状态执行受限转换；任务级版本由 SQL 显式维护。 */
    @Update("UPDATE wms_inventory_count_task SET status='COUNTING',updated_by=#{actor},updated_at=now(),started_by=#{actor},started_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int start(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    @Update("UPDATE wms_inventory_count_task SET status='CONFIRMED',updated_by=#{actor},updated_at=now(),confirmed_by=#{actor},confirmed_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int confirm(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    @Update("UPDATE wms_inventory_count_task SET status='COMPLETED',updated_by=#{actor},updated_at=now(),completed_by=#{actor},completed_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int complete(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    @Update("UPDATE wms_inventory_count_task SET status='CANCELLED',updated_by=#{actor},updated_at=now(),cancelled_by=#{actor},cancelled_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int cancel(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

}
