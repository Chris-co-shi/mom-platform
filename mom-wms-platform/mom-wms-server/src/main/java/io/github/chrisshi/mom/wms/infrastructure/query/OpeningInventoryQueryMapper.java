package io.github.chrisshi.mom.wms.infrastructure.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 期初单及仓库生命周期只读联合视图；保留 JOIN 分页以避免 N+1 查询。 */
@Mapper
public interface OpeningInventoryQueryMapper {
    /** 单据 + WMS 仓库启用状态的只读 JOIN Projection，不是业务聚合。 */
    record DocumentRow(String id, String documentNo, String warehouseId, String warehouseCode,
                       String status, String transactionId, String createdBy, long version,
                       String warehouseState) { }


    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id WHERE d.id=#{id}")
    DocumentRow find(@Param("id") String id);

    @Select("<script>SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id <if test='warehouseId != null'>WHERE d.warehouse_id=#{warehouseId}</if> ORDER BY d.created_at DESC,d.id DESC</script>")
    IPage<DocumentRow> list(IPage<DocumentRow> page,@Param("warehouseId") String warehouseId);


}
