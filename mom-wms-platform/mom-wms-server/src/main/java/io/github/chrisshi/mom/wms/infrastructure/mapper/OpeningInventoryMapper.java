package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentEntity;
import io.github.chrisshi.mom.wms.infrastructure.query.OpeningInventoryQueryMapper.DocumentRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 期初单实体 Mapper：常规 CRUD 由 mom-data 实现，仅保留显式 Actor 创建、行锁、版本推进和过账 CAS。 */
@Mapper
public interface OpeningInventoryMapper extends MomBaseMapper<OpeningDocumentEntity> {

    /** 保留显式操作人以兼容当前期初事务调用方（包括受控系统发起的事务）。 */
    @Insert("INSERT INTO wms_opening_document(id,document_no,warehouse_id,warehouse_code,status,created_by,updated_by) VALUES(#{id},#{documentNo},#{warehouseId},#{warehouseCode},'DRAFT',#{actor},#{actor})")
    int createDocument(@Param("id") String id,@Param("documentNo") String documentNo,
                       @Param("warehouseId") String warehouseId,@Param("warehouseCode") String warehouseCode,
                       @Param("actor") String actor);

    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id WHERE d.id=#{id} FOR UPDATE OF d")
    DocumentRow lockDocument(@Param("id") String id);

    /** 自定义更新显式记录审计 Actor 和时间，并受草稿状态约束。 */
    @Update("UPDATE wms_opening_document SET updated_at=now(),updated_by=#{actor},version=version+1 WHERE id=#{id} AND status='DRAFT'")
    int bumpDocumentVersion(@Param("id") String id,@Param("actor") String actor);

    @Update("UPDATE wms_opening_document SET status='POSTED',transaction_id=#{transactionId},posted_by=#{actor},posted_at=now(),updated_at=now(),version=version+1 WHERE id=#{id} AND status='DRAFT' AND version=#{version}")
    int postDocument(@Param("id") String id,@Param("transactionId") String transactionId,
                     @Param("version") long version,@Param("actor") String actor);

}
