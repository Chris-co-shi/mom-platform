package io.github.chrisshi.mom.wms.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 盘点任务、范围锁与快照的 PostgreSQL SQL；由调用方控制本地事务和锁顺序。 */
@Mapper
public interface InventoryCountSqlMapper {
    /** 插入任务草稿。 */
    @Insert("""
        INSERT INTO wms_inventory_count_task
          (id,task_no,request_key,warehouse_id,warehouse_code,location_ids,material_ids,created_by,updated_by)
        VALUES (#{id},#{number},#{requestKey},#{warehouseId},#{warehouseCode},
                #{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},
                #{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},#{actor},#{actor})
        """)
    int create(@Param("id") String id,@Param("number") String number,@Param("requestKey") String requestKey,
               @Param("warehouseId") String warehouseId,@Param("warehouseCode") String warehouseCode,
               @Param("locations") String[] locations,@Param("materials") String[] materials,@Param("actor") String actor);

    /** 按任务 ID 查询。 */
    @Select("SELECT * FROM wms_inventory_count_task WHERE id=#{id}")
    Map<String,Object> find(@Param("id") String id);

    /** 序列化同一创建请求键。 */
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{key},0))")
    Object lockCreateRequest(@Param("key") String key);

    /** 按请求键查询已提交任务。 */
    @Select("SELECT * FROM wms_inventory_count_task WHERE request_key=#{requestKey}")
    Map<String,Object> findByRequestKey(@Param("requestKey") String requestKey);

    /** 锁定任务行。 */
    @Select("SELECT * FROM wms_inventory_count_task WHERE id=#{id} FOR UPDATE")
    Map<String,Object> lockTask(@Param("id") String id);

    /** 读取任务明细。 */
    @Select("SELECT * FROM wms_inventory_count_line WHERE task_id=#{taskId} ORDER BY id")
    List<Map<String,Object>> lines(@Param("taskId") String taskId);

    /** 更新草稿范围与审计字段。 */
    @Update("""
        UPDATE wms_inventory_count_task SET
          location_ids=#{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},
          material_ids=#{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},
          updated_by=#{actor},updated_at=now(),version=version+1
        WHERE id=#{id} AND status='DRAFT'
        """)
    int updateScope(@Param("id") String id,@Param("locations") String[] locations,
                    @Param("materials") String[] materials,@Param("actor") String actor);

    /** 查询仓库库存生命周期。 */
    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId}")
    String warehouseState(@Param("warehouseId") String warehouseId);

    /** 与库存记账使用同一事务级仓库锁键。 */
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{key},0))")
    Object lockWarehouse(@Param("key") String key);

    /** 尝试占有整仓持久锁。 */
    @Insert("INSERT INTO wms_inventory_count_scope_lock(warehouse_id,task_id) VALUES (#{warehouseId},#{taskId}) ON CONFLICT (warehouse_id) DO NOTHING")
    int acquire(@Param("warehouseId") String warehouseId,@Param("taskId") String taskId);

    /** 查询整仓锁所有者。 */
    @Select("SELECT task_id FROM wms_inventory_count_scope_lock WHERE warehouse_id=#{warehouseId}")
    String lockOwner(@Param("warehouseId") String warehouseId);

    /** 只释放本任务持有的整仓锁。 */
    @Delete("DELETE FROM wms_inventory_count_scope_lock WHERE warehouse_id=#{warehouseId} AND task_id=#{taskId}")
    int release(@Param("warehouseId") String warehouseId,@Param("taskId") String taskId);

    /** 在仓库事务锁下按范围查询账面余额。 */
    @Select("""
        SELECT * FROM wms_inventory_balance b
        WHERE b.warehouse_id=#{warehouseId}
          AND (cardinality(#{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]) = 0
               OR b.location_id=ANY(#{locations,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]))
          AND (cardinality(#{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]) = 0
               OR b.material_id=ANY(#{materials,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}::varchar[]))
        ORDER BY b.id
        """)
    List<Map<String,Object>> books(@Param("warehouseId") String warehouseId,@Param("locations") String[] locations,
                                    @Param("materials") String[] materials);

    /** 插入不可变账面快照。 */
    @Insert("""
        INSERT INTO wms_inventory_count_line
          (id,task_id,material_id,material_code,material_name,uom_code,batch_code,sn_code,warehouse_id,
           warehouse_code,location_id,location_code,quality_status,warehouse_status,book_quantity,snapshot_by)
        VALUES (#{id},#{taskId},#{materialId},#{materialCode},#{materialName},#{uomCode},#{batchCode},#{snCode},
                #{warehouseId},#{warehouseCode},#{locationId},#{locationCode},#{qualityStatus},#{warehouseStatus},
                #{quantity},#{actor})
        """)
    int snapshot(@Param("id") String id,@Param("taskId") String taskId,
                 @Param("materialId") String materialId,@Param("materialCode") String materialCode,
                 @Param("materialName") String materialName,@Param("uomCode") String uomCode,
                 @Param("batchCode") String batchCode,@Param("snCode") String snCode,
                 @Param("warehouseId") String warehouseId,@Param("warehouseCode") String warehouseCode,
                 @Param("locationId") String locationId,@Param("locationCode") String locationCode,
                 @Param("qualityStatus") String qualityStatus,@Param("warehouseStatus") String warehouseStatus,
                 @Param("quantity") BigDecimal quantity,@Param("actor") String actor);

    /** 进入盘点中。 */
    @Update("UPDATE wms_inventory_count_task SET status='COUNTING',updated_by=#{actor},updated_at=now(),started_by=#{actor},started_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int start(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    /** 确认盘点。 */
    @Update("UPDATE wms_inventory_count_task SET status='CONFIRMED',updated_by=#{actor},updated_at=now(),confirmed_by=#{actor},confirmed_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int confirm(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    /** 完成盘点。 */
    @Update("UPDATE wms_inventory_count_task SET status='COMPLETED',updated_by=#{actor},updated_at=now(),completed_by=#{actor},completed_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int complete(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    /** 取消盘点。 */
    @Update("UPDATE wms_inventory_count_task SET status='CANCELLED',updated_by=#{actor},updated_at=now(),cancelled_by=#{actor},cancelled_at=now(),transaction_id=COALESCE(#{transactionId},transaction_id),version=version+1 WHERE id=#{id} AND status=#{from}")
    int cancel(@Param("id") String id,@Param("from") String from,@Param("actor") String actor,@Param("transactionId") String transactionId);

    /** 保存实盘数量。 */
    @Update("UPDATE wms_inventory_count_line SET counted_quantity=#{quantity},counted_by=#{actor},counted_at=now() WHERE id=#{lineId} AND task_id=#{taskId}")
    int count(@Param("taskId") String taskId,@Param("lineId") String lineId,
              @Param("quantity") BigDecimal quantity,@Param("actor") String actor);

    /** 插入零账面的账外实物行。 */
    @Insert("""
        INSERT INTO wms_inventory_count_line
          (id,task_id,material_id,material_code,material_name,uom_code,batch_code,sn_code,warehouse_id,
           warehouse_code,location_id,location_code,quality_status,warehouse_status,book_quantity,
           counted_quantity,snapshot_by,counted_by,counted_at)
        VALUES (#{id},#{taskId},#{materialId},#{materialCode},#{materialName},#{uomCode},#{batchCode},#{snCode},
                #{warehouseId},#{warehouseCode},#{locationId},#{locationCode},#{qualityStatus},#{warehouseStatus},
                0,#{quantity},#{actor},#{actor},now())
        """)
    int addExtra(@Param("id") String id,@Param("taskId") String taskId,
                 @Param("materialId") String materialId,@Param("materialCode") String materialCode,
                 @Param("materialName") String materialName,@Param("uomCode") String uomCode,
                 @Param("batchCode") String batchCode,@Param("snCode") String snCode,
                 @Param("warehouseId") String warehouseId,@Param("warehouseCode") String warehouseCode,
                 @Param("locationId") String locationId,@Param("locationCode") String locationCode,
                 @Param("qualityStatus") String qualityStatus,@Param("warehouseStatus") String warehouseStatus,
                 @Param("quantity") BigDecimal quantity,@Param("actor") String actor);

    /** 查询正余额 SN 是否已存在。 */
    @Select("SELECT EXISTS(SELECT 1 FROM wms_inventory_balance WHERE sn_code=#{sn} AND quantity>0)")
    Boolean snExists(@Param("sn") String sn);
}
