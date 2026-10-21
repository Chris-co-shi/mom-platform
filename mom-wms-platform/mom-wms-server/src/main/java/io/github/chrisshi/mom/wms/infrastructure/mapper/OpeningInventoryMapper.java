package io.github.chrisshi.mom.wms.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 期初单 Mapper：常规实体查询遵循 mom-data；
 * 跨表读模型、仓库事务锁、状态限定 CAS 保留 PostgreSQL 专用 SQL。
 * 仅执行数据访问，状态机和事务编排由 Application 负责。
 */
@Mapper
public interface OpeningInventoryMapper extends MomBaseMapper<OpeningDocumentEntity> {

    /** 单据 + WMS 仓库启用状态的只读 JOIN Projection，不是业务聚合。 */
    record DocumentRow(String id, String documentNo, String warehouseId, String warehouseCode,
                       String status, String transactionId, String createdBy, long version,
                       String warehouseState) { }

    @Insert("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state,updated_by) VALUES(#{id},#{warehouseId},#{warehouseCode},'INITIALIZING',#{actor}) ON CONFLICT(warehouse_id) DO NOTHING")
    int initialize(@Param("id") String id,@Param("warehouseId") String warehouseId,
                   @Param("warehouseCode") String warehouseCode,@Param("actor") String actor);

    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId} FOR UPDATE")
    String lockWarehouse(@Param("warehouseId") String warehouseId);

    /** 保留显式操作人以兼容当前期初事务调用方（包括受控系统发起的事务）。 */
    @Insert("INSERT INTO wms_opening_document(id,document_no,warehouse_id,warehouse_code,status,created_by,updated_by) VALUES(#{id},#{documentNo},#{warehouseId},#{warehouseCode},'DRAFT',#{actor},#{actor})")
    int createDocument(@Param("id") String id,@Param("documentNo") String documentNo,
                       @Param("warehouseId") String warehouseId,@Param("warehouseCode") String warehouseCode,
                       @Param("actor") String actor);

    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id WHERE d.id=#{id}")
    DocumentRow find(@Param("id") String id);

    @Select("<script>SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id <if test='warehouseId != null'>WHERE d.warehouse_id=#{warehouseId}</if> ORDER BY d.created_at DESC,d.id DESC</script>")
    IPage<DocumentRow> list(IPage<DocumentRow> page,@Param("warehouseId") String warehouseId);

    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id WHERE d.id=#{id} FOR UPDATE OF d")
    DocumentRow lockDocument(@Param("id") String id);

    /** 自定义更新显式记录审计 Actor 和时间，并受草稿状态约束。 */
    @Update("UPDATE wms_opening_document SET updated_at=now(),updated_by=#{actor},version=version+1 WHERE id=#{id} AND status='DRAFT'")
    int bumpDocumentVersion(@Param("id") String id,@Param("actor") String actor);

    @Update("UPDATE wms_opening_document SET status='POSTED',transaction_id=#{transactionId},posted_by=#{actor},posted_at=now(),updated_at=now(),version=version+1 WHERE id=#{id} AND status='DRAFT' AND version=#{version}")
    int postDocument(@Param("id") String id,@Param("transactionId") String transactionId,
                     @Param("version") long version,@Param("actor") String actor);

    @Delete("DELETE FROM wms_opening_document WHERE id=#{id} AND status='DRAFT'")
    int deleteDraft(@Param("id") String id);

    @Delete("DELETE FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId} AND state='INITIALIZING' AND opening_document_id IS NULL")
    int deleteInitializingWarehouse(@Param("warehouseId") String warehouseId);

    @Update("UPDATE wms_warehouse_inventory_state SET state='PENDING_ACTIVATION',opening_document_id=#{documentId},updated_at=now(),updated_by=#{actor} WHERE warehouse_id=#{warehouseId} AND state='INITIALIZING'")
    int markPending(@Param("warehouseId") String warehouseId,@Param("documentId") String documentId,
                    @Param("actor") String actor);

    @Update("UPDATE wms_warehouse_inventory_state SET state='ACTIVE',activated_by=#{actor},activated_at=now(),updated_at=now(),updated_by=#{actor} WHERE warehouse_id=#{warehouseId} AND state='PENDING_ACTIVATION'")
    int activate(@Param("warehouseId") String warehouseId,@Param("actor") String actor);

    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId}")
    String warehouseState(@Param("warehouseId") String warehouseId);

    @Select("SELECT EXISTS(SELECT 1 FROM wms_inventory_balance WHERE warehouse_id=#{warehouseId})")
    boolean hasWarehouseBalance(@Param("warehouseId") String warehouseId);
}
