package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.Balance;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * WMS 冻结/解冻事实与剩余限制的原子数据库操作。
 * 冻结历史不可变，余额扣减必须经条件 SQL；不暴露任意通用删除/覆盖入口。
 * 业务冻结策略、幂等与跨行编排均由 InventoryFreezeApplication 负责。
 */
@Mapper
public interface InventoryFreezeMapper {
    public record FreezeRecord(String id, String transactionId, String originalBalanceId, String materialId,
                               String materialCode, String materialName, String uomCode, String batchCode,
                               String snCode, String warehouseId, String warehouseCode, String locationId,
                               String locationCode, QualityStatus qualityStatus,
                               String reasonCode, String note, BigDecimal originalQuantity, BigDecimal remainingQuantity,
                               String sourceType, String sourceId, String operatorId, Instant frozenAt) { }

    public record UnfreezeRecord(String requestHash, String transactionId) { }

    /** 查询冻结交易对应限制，支撑交易请求幂等重放返回原冻结记录。 */
    @Select("""
        SELECT id, transaction_id AS transactionId, original_balance_id AS originalBalanceId,
               material_id AS materialId, material_code AS materialCode, material_name AS materialName,
               uom_code AS uomCode, batch_code AS batchCode, sn_code AS snCode,
               warehouse_id AS warehouseId, warehouse_code AS warehouseCode, location_id AS locationId,
               location_code AS locationCode, quality_status AS qualityStatus, reason_code AS reasonCode,
               note, original_quantity AS originalQuantity, remaining_quantity AS remainingQuantity,
               source_type AS sourceType, source_id AS sourceId, operator_id AS operatorId, frozen_at AS frozenAt
          FROM wms_inventory_freeze WHERE transaction_id=#{transactionId}
        """)
    @ConstructorArgs({@Arg(column="id", javaType=String.class), @Arg(column="transactionId", javaType=String.class),
        @Arg(column="originalBalanceId", javaType=String.class), @Arg(column="materialId", javaType=String.class),
        @Arg(column="materialCode", javaType=String.class), @Arg(column="materialName", javaType=String.class),
        @Arg(column="uomCode", javaType=String.class), @Arg(column="batchCode", javaType=String.class),
        @Arg(column="snCode", javaType=String.class), @Arg(column="warehouseId", javaType=String.class),
        @Arg(column="warehouseCode", javaType=String.class), @Arg(column="locationId", javaType=String.class),
        @Arg(column="locationCode", javaType=String.class), @Arg(column="qualityStatus", javaType=QualityStatus.class),
        @Arg(column="reasonCode", javaType=String.class), @Arg(column="note", javaType=String.class),
        @Arg(column="originalQuantity", javaType=BigDecimal.class), @Arg(column="remainingQuantity", javaType=BigDecimal.class),
        @Arg(column="sourceType", javaType=String.class), @Arg(column="sourceId", javaType=String.class),
        @Arg(column="operatorId", javaType=String.class), @Arg(column="frozenAt", javaType=Instant.class)})
    FreezeRecord findFreezeByTransaction(@Param("transactionId") String transactionId);

    /** 锁定限制行串行化并发解冻；必须在调用库存记账事务内执行。 */
    @Select("""
        SELECT id, transaction_id AS transactionId, original_balance_id AS originalBalanceId,
               material_id AS materialId, material_code AS materialCode, material_name AS materialName,
               uom_code AS uomCode, batch_code AS batchCode, sn_code AS snCode,
               warehouse_id AS warehouseId, warehouse_code AS warehouseCode, location_id AS locationId,
               location_code AS locationCode, quality_status AS qualityStatus, reason_code AS reasonCode,
               note, original_quantity AS originalQuantity, remaining_quantity AS remainingQuantity,
               source_type AS sourceType, source_id AS sourceId, operator_id AS operatorId, frozen_at AS frozenAt
          FROM wms_inventory_freeze WHERE id=#{id} FOR UPDATE
        """)
    @ConstructorArgs({@Arg(column="id", javaType=String.class), @Arg(column="transactionId", javaType=String.class),
        @Arg(column="originalBalanceId", javaType=String.class), @Arg(column="materialId", javaType=String.class),
        @Arg(column="materialCode", javaType=String.class), @Arg(column="materialName", javaType=String.class),
        @Arg(column="uomCode", javaType=String.class), @Arg(column="batchCode", javaType=String.class),
        @Arg(column="snCode", javaType=String.class), @Arg(column="warehouseId", javaType=String.class),
        @Arg(column="warehouseCode", javaType=String.class), @Arg(column="locationId", javaType=String.class),
        @Arg(column="locationCode", javaType=String.class), @Arg(column="qualityStatus", javaType=QualityStatus.class),
        @Arg(column="reasonCode", javaType=String.class), @Arg(column="note", javaType=String.class),
        @Arg(column="originalQuantity", javaType=BigDecimal.class), @Arg(column="remainingQuantity", javaType=BigDecimal.class),
        @Arg(column="sourceType", javaType=String.class), @Arg(column="sourceId", javaType=String.class),
        @Arg(column="operatorId", javaType=String.class), @Arg(column="frozenAt", javaType=Instant.class)})
    FreezeRecord lockFreeze(@Param("id") String id);

    /** 与冻结交易同一事务保存原因、操作者和原始维度快照；transaction_id 唯一约束支持重放。 */
    @Insert("""
        INSERT INTO wms_inventory_freeze (id,transaction_id,original_balance_id,material_id,material_code,
          material_name,uom_code,batch_code,sn_code,warehouse_id,warehouse_code,location_id,location_code,
          quality_status,reason_code,note,original_quantity,remaining_quantity,source_type,source_id,operator_id)
        VALUES (#{id},#{transactionId},#{balance.id},#{balance.materialId},#{balance.materialCode},
          #{balance.materialName},#{balance.uomCode},#{balance.batchCode},#{balance.snCode},#{balance.warehouseId},
          #{balance.warehouseCode},#{balance.locationId},#{balance.locationCode},#{balance.qualityStatus},
          #{reasonCode},#{note},#{quantity},#{quantity},#{sourceType},#{sourceId},#{operatorId})
        ON CONFLICT (transaction_id) DO NOTHING
        """)
    int insertFreeze(@Param("id") String id, @Param("transactionId") String transactionId,
                     @Param("balance") Balance balance, @Param("reasonCode") String reasonCode,
                     @Param("note") String note, @Param("quantity") BigDecimal quantity,
                     @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                     @Param("operatorId") String operatorId);

    /** 查询指定余额维度的全部有效限制，使用与部分索引一致的字段和剩余量过滤。 */
    @Select("""
        SELECT f.id, f.transaction_id AS transactionId, f.original_balance_id AS originalBalanceId,
               f.material_id AS materialId, f.material_code AS materialCode, f.material_name AS materialName,
               f.uom_code AS uomCode, f.batch_code AS batchCode, f.sn_code AS snCode,
               f.warehouse_id AS warehouseId, f.warehouse_code AS warehouseCode, f.location_id AS locationId,
               f.location_code AS locationCode, f.quality_status AS qualityStatus, f.reason_code AS reasonCode,
               f.note, f.original_quantity AS originalQuantity, f.remaining_quantity AS remainingQuantity,
               f.source_type AS sourceType, f.source_id AS sourceId, f.operator_id AS operatorId, f.frozen_at AS frozenAt
          FROM wms_inventory_freeze f
         WHERE f.material_id=#{b.materialId} AND f.warehouse_id=#{b.warehouseId}
           AND f.location_id=#{b.locationId} AND f.batch_code IS NOT DISTINCT FROM #{b.batchCode}
           AND f.sn_code IS NOT DISTINCT FROM #{b.snCode} AND f.quality_status=#{b.qualityStatus}
           AND f.remaining_quantity > 0
         ORDER BY f.frozen_at, f.id
        """)
    @ConstructorArgs({@Arg(column="id", javaType=String.class), @Arg(column="transactionId", javaType=String.class),
        @Arg(column="originalBalanceId", javaType=String.class), @Arg(column="materialId", javaType=String.class),
        @Arg(column="materialCode", javaType=String.class), @Arg(column="materialName", javaType=String.class),
        @Arg(column="uomCode", javaType=String.class), @Arg(column="batchCode", javaType=String.class),
        @Arg(column="snCode", javaType=String.class), @Arg(column="warehouseId", javaType=String.class),
        @Arg(column="warehouseCode", javaType=String.class), @Arg(column="locationId", javaType=String.class),
        @Arg(column="locationCode", javaType=String.class), @Arg(column="qualityStatus", javaType=QualityStatus.class),
        @Arg(column="reasonCode", javaType=String.class), @Arg(column="note", javaType=String.class),
        @Arg(column="originalQuantity", javaType=BigDecimal.class), @Arg(column="remainingQuantity", javaType=BigDecimal.class),
        @Arg(column="sourceType", javaType=String.class), @Arg(column="sourceId", javaType=String.class),
        @Arg(column="operatorId", javaType=String.class), @Arg(column="frozenAt", javaType=Instant.class)})
    List<FreezeRecord> activeFreezesByDimension(@Param("b") Balance balance);

    /** 条件更新是并发部分解冻的数据库级非负保护。 */
    @Update("UPDATE wms_inventory_freeze SET remaining_quantity=remaining_quantity-#{quantity} " +
            "WHERE id=#{id} AND remaining_quantity >= #{quantity}")
    int decreaseFreezeRemaining(@Param("id") String id, @Param("quantity") BigDecimal quantity);

    /** 查询已提交解冻请求摘要，区分幂等重放与同键异内容。 */
    @Select("SELECT request_hash AS requestHash, transaction_id AS transactionId " +
            "FROM wms_inventory_unfreeze WHERE request_key=#{requestKey}")
    @ConstructorArgs({@Arg(column="requestHash", javaType=String.class), @Arg(column="transactionId", javaType=String.class)})
    UnfreezeRecord findUnfreeze(@Param("requestKey") String requestKey);

    /** 写入不可变解冻历史，request_key 唯一约束阻止重复解除。 */
    @Insert("""
        INSERT INTO wms_inventory_unfreeze
          (id,request_key,request_hash,freeze_id,transaction_id,quantity,note,source_type,source_id,operator_id)
        VALUES (#{id},#{requestKey},#{requestHash},#{freezeId},#{transactionId},#{quantity},#{note},
                #{sourceType},#{sourceId},#{operatorId})
        """)
    int insertUnfreeze(@Param("id") String id, @Param("requestKey") String requestKey,
                       @Param("requestHash") String requestHash, @Param("freezeId") String freezeId,
                       @Param("transactionId") String transactionId, @Param("quantity") BigDecimal quantity,
                       @Param("note") String note, @Param("sourceType") String sourceType,
                       @Param("sourceId") String sourceId, @Param("operatorId") String operatorId);


}
