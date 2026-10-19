package io.github.chrisshi.mom.wms.infrastructure.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.EntryView;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.Filter;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.TransactionView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 直接查询不可变交易与分录；列表一条 SQL 行对应一条分录，分页 count 无一对多重复。 */
@Mapper
public interface InventoryHistoryQueryMapper {
    /**
     * 按交易时间、交易 ID、分录 ID 倒序稳定分页；时间为左闭右开。
     * @param page 数据库分页对象
     * @param filter 已规范化的组合条件
     * @return 分录分页结果
     */
    @Select("""
            <script>
            SELECT e.id, e.transaction_id AS transactionId, e.line_no AS lineNo,
                   t.transaction_type AS transactionType, t.source_type AS sourceType,
                   t.source_id AS sourceId, t.operator_id AS operatorId, t.occurred_at AS occurredAt,
                   e.material_id AS materialId, e.material_code AS materialCode,
                   e.material_name AS materialName, e.uom_code AS uomCode,
                   e.batch_code AS batchCode, e.sn_code AS snCode,
                   e.warehouse_id AS warehouseId, e.warehouse_code AS warehouseCode,
                   e.location_id AS locationId, e.location_code AS locationCode,
                   e.quality_status AS qualityStatus, e.warehouse_status AS warehouseStatus,
                   e.delta_quantity AS deltaQuantity, e.before_quantity AS beforeQuantity,
                   e.after_quantity AS afterQuantity
              FROM wms_inventory_transaction t
              JOIN wms_inventory_transaction_entry e ON e.transaction_id = t.id
             WHERE t.occurred_at &gt;= #{filter.startTime} AND t.occurred_at &lt; #{filter.endTime}
               <if test="filter.materialId != null">AND e.material_id = #{filter.materialId}</if>
               <if test="filter.batchCode != null">AND e.batch_code = #{filter.batchCode}</if>
               <if test="filter.snCode != null">AND e.sn_code = #{filter.snCode}</if>
               <if test="filter.warehouseId != null">AND e.warehouse_id = #{filter.warehouseId}</if>
               <if test="filter.locationId != null">AND e.location_id = #{filter.locationId}</if>
               <if test="filter.transactionType != null">AND t.transaction_type = #{filter.transactionType}</if>
               <if test="filter.sourceType != null">AND t.source_type = #{filter.sourceType}</if>
               <if test="filter.sourceId != null">AND t.source_id = #{filter.sourceId}</if>
               <if test="filter.actor != null">AND t.operator_id = #{filter.actor}</if>
             ORDER BY t.occurred_at DESC, t.id DESC, e.id DESC
            </script>
            """)
    IPage<EntryView> search(IPage<EntryView> page, @Param("filter") Filter filter);

    /**
     * 查询交易头及冻结/解冻当时留下的原因与说明；两张来源表各有交易 ID 唯一约束。
     * @param transactionId 交易标识
     * @return 交易头，不存在时为空
     */
    @Select("""
            SELECT t.id, t.transaction_type AS transactionType, t.source_type AS sourceType,
                   t.source_id AS sourceId, t.operator_id AS operatorId, t.occurred_at AS occurredAt,
                   f.reason_code AS reasonCode, COALESCE(f.note, u.note) AS note
              FROM wms_inventory_transaction t
              LEFT JOIN wms_inventory_freeze f ON f.transaction_id = t.id
              LEFT JOIN wms_inventory_unfreeze u ON u.transaction_id = t.id
             WHERE t.id = #{transactionId}
            """)
    TransactionView transaction(@Param("transactionId") String transactionId);

    /**
     * 不带列表过滤条件读取一笔交易的全部分录；行号由交易内唯一约束保证。
     * @param transactionId 交易标识
     * @return 按 lineNo 升序的全部分录
     */
    @Select("""
            SELECT e.id, e.transaction_id AS transactionId, e.line_no AS lineNo,
                   e.material_id AS materialId, e.material_code AS materialCode,
                   e.material_name AS materialName, e.uom_code AS uomCode,
                   e.batch_code AS batchCode, e.sn_code AS snCode,
                   e.warehouse_id AS warehouseId, e.warehouse_code AS warehouseCode,
                   e.location_id AS locationId, e.location_code AS locationCode,
                   e.quality_status AS qualityStatus, e.warehouse_status AS warehouseStatus,
                   e.delta_quantity AS deltaQuantity, e.before_quantity AS beforeQuantity,
                   e.after_quantity AS afterQuantity
              FROM wms_inventory_transaction_entry e
             WHERE e.transaction_id = #{transactionId}
             ORDER BY e.line_no, e.id
            """)
    List<EntryView> entries(@Param("transactionId") String transactionId);
}
