package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryBalanceEntity;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingOperations.Balance;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingOperations.FreezeRecord;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingOperations.UnfreezeRecord;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
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
 * 库存专用 PostgreSQL 原子写入；由 Application 的公共事务入口统一调用。
 */
@Mapper
interface InventoryAccountingMapper extends MomBaseMapper<InventoryBalanceEntity> {
    /** 同一事务按稳定键锁定库位或 SN；所有 SN 记账方共用此锁以阻止位置校验期间出现幻影余额。 */
    @Select("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(#{key}, 0))")
    int lockAccountingKey(@Param("key") String key);

    /** 仅允许全局一条正余额的 SN 作为不可拆载体整体移动。 */
    @Select("SELECT count(*) FROM wms_inventory_balance WHERE sn_code=#{snCode} AND quantity>0")
    int positiveBalanceCountForSn(@Param("snCode") String snCode);

    /** 读取不可变交易和两条分录，以原始输入而非当前余额决定重放。 */
    @Select("""
        SELECT t.id AS transactionId, t.transaction_type AS transactionType,
               t.source_type AS sourceType, t.source_id AS sourceId, t.operator_id AS operatorId,
               e1.balance_id AS balanceId, e2.location_id AS targetLocationId,
               e2.delta_quantity AS quantity
          FROM wms_inventory_transaction t
          LEFT JOIN wms_inventory_transaction_entry e1 ON e1.transaction_id=t.id AND e1.line_no=1
          LEFT JOIN wms_inventory_transaction_entry e2 ON e2.transaction_id=t.id AND e2.line_no=2
         WHERE t.request_key=#{requestKey}
        """)
    @ConstructorArgs({@Arg(column="transactionId", javaType=String.class),
        @Arg(column="transactionType", javaType=String.class), @Arg(column="sourceType", javaType=String.class),
        @Arg(column="sourceId", javaType=String.class), @Arg(column="operatorId", javaType=String.class),
        @Arg(column="balanceId", javaType=String.class), @Arg(column="targetLocationId", javaType=String.class),
        @Arg(column="quantity", javaType=BigDecimal.class)})
    InventoryAccountingOperations.TransferReplay findTransferReplay(@Param("requestKey") String requestKey);
    /** 读取冻结命令指向的余额快照；余额扣减必须由统一记账 SQL 完成。 */
    @Select("""
        SELECT id, material_id AS materialId, material_code AS materialCode, material_name AS materialName,
               uom_code AS uomCode, batch_code AS batchCode, sn_code AS snCode,
               warehouse_id AS warehouseId, warehouse_code AS warehouseCode, location_id AS locationId,
               location_code AS locationCode, quality_status AS qualityStatus,
               warehouse_status AS warehouseStatus, quantity
          FROM wms_inventory_balance WHERE id=#{id}
        """)
    @ConstructorArgs({@Arg(column="id", javaType=String.class), @Arg(column="materialId", javaType=String.class),
        @Arg(column="materialCode", javaType=String.class), @Arg(column="materialName", javaType=String.class),
        @Arg(column="uomCode", javaType=String.class), @Arg(column="batchCode", javaType=String.class),
        @Arg(column="snCode", javaType=String.class), @Arg(column="warehouseId", javaType=String.class),
        @Arg(column="warehouseCode", javaType=String.class), @Arg(column="locationId", javaType=String.class),
        @Arg(column="locationCode", javaType=String.class), @Arg(column="qualityStatus", javaType=QualityStatus.class),
        @Arg(column="warehouseStatus", javaType=WarehouseStatus.class),
        @Arg(column="quantity", javaType=BigDecimal.class)})
    Balance findInventoryBalance(@Param("id") String id);

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

    /**
     * 读取 WMS 仓库库存生命周期状态供唯一记账入口 fail closed 校验。
     * @param warehouseId WMS 仓库稳定 ID @return 当前状态，不存在时为空
     */
    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId}")
    String warehouseInventoryState(@Param("warehouseId") String warehouseId);

    /**
     * 数据库唯一键领取幂等请求，并在同一本地事务记录来源与责任主体。
     * @param id 新交易技术标识
     * @param requestKey 幂等请求标识
     * @param requestHash 完整请求指纹
     * @param transactionType 交易业务类型
     * @param sourceType 业务来源类型
     * @param sourceId 来源对象标识
     * @param operatorId 实际操作责任主体
     * @return 新领取的交易标识；请求键冲突时为空
     */
    @Select("""
        INSERT INTO wms_inventory_transaction
          (id, request_key, request_hash, transaction_type, source_type, source_id, operator_id)
        VALUES (#{id}, #{requestKey}, #{requestHash}, #{transactionType}, #{sourceType}, #{sourceId}, #{operatorId})
        ON CONFLICT (request_key) DO NOTHING RETURNING id
        """)
    String claim(@Param("id") String id, @Param("requestKey") String requestKey,
                 @Param("requestHash") String requestHash, @Param("transactionType") String transactionType,
                 @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                 @Param("operatorId") String operatorId);

    /**
     * 读取已提交请求指纹，区分同内容重放与同键异内容冲突。
     * @param requestKey 幂等请求标识
     * @return 已提交请求指纹
     */
    @Select("SELECT request_hash FROM wms_inventory_transaction WHERE request_key = #{requestKey}")
    String hashOf(@Param("requestKey") String requestKey);

    /**
     * 读取已提交交易标识，供幂等重放返回。
     * @param requestKey 幂等请求标识
     * @return 已提交交易标识
     */
    @Select("SELECT id FROM wms_inventory_transaction WHERE request_key = #{requestKey}")
    String idOf(@Param("requestKey") String requestKey);

    /**
     * 以唯一维度原子增加，返回该分录执行前后的数据库实际余额。
     * @param id 新余额技术标识
     * @param dimension 库存唯一维度及快照
     * @param delta 正数增加量
     * @return 实际受影响余额及前后数量；仓库归属冲突时为空
     */
    @Select("""
        INSERT INTO wms_inventory_balance
          (id, material_id, material_code, material_name, uom_code, batch_code, sn_code,
           warehouse_id, warehouse_code, location_id, location_code,
           quality_status, warehouse_status, quantity)
        VALUES
          (#{id}, #{d.materialId}, #{d.materialCode}, #{d.materialName}, #{d.uomCode},
           #{d.batchCode}, #{d.snCode}, #{d.warehouseId}, #{d.warehouseCode},
           #{d.locationId}, #{d.locationCode}, #{d.qualityStatus}, #{d.warehouseStatus}, #{delta})
        ON CONFLICT ON CONSTRAINT uk_wms_inventory_balance_dimension DO UPDATE
          SET quantity = wms_inventory_balance.quantity + EXCLUDED.quantity,
              updated_at = now()
          WHERE wms_inventory_balance.warehouse_id = EXCLUDED.warehouse_id
        RETURNING id, quantity - #{delta} AS before_quantity, quantity AS after_quantity
        """)
    @ConstructorArgs({@Arg(column = "id", javaType = String.class),
        @Arg(column = "before_quantity", javaType = BigDecimal.class),
        @Arg(column = "after_quantity", javaType = BigDecimal.class)})
    InventoryAccountingOperations.BalanceChange increase(@Param("id") String id, @Param("d") Dimension dimension,
                                                          @Param("delta") BigDecimal delta);

    /**
     * 条件扣减由行锁和非负条件共同保护，并返回执行前后的实际余额。
     * @param dimension 库存唯一维度
     * @param delta 负数扣减量
     * @return 实际受影响余额及前后数量；库存不足或余额不存在时为空
     */
    @Select("""
        UPDATE wms_inventory_balance
           SET quantity = quantity + #{delta}, updated_at = now()
         WHERE material_id = #{d.materialId}
           AND batch_code IS NOT DISTINCT FROM #{d.batchCode}
           AND sn_code IS NOT DISTINCT FROM #{d.snCode}
           AND location_id = #{d.locationId}
           AND warehouse_id = #{d.warehouseId}
           AND quality_status = #{d.qualityStatus}
           AND warehouse_status = #{d.warehouseStatus}
           AND quantity >= -#{delta}
         RETURNING id, quantity - #{delta} AS before_quantity, quantity AS after_quantity
        """)
    @ConstructorArgs({@Arg(column = "id", javaType = String.class),
        @Arg(column = "before_quantity", javaType = BigDecimal.class),
        @Arg(column = "after_quantity", javaType = BigDecimal.class)})
    InventoryAccountingOperations.BalanceChange decrease(@Param("d") Dimension dimension,
                                                          @Param("delta") BigDecimal delta);

    /**
     * 追加不可变分录，并保存发生时完整维度、状态和数量快照。
     * @param id 分录技术标识
     * @param transactionId 所属交易标识
     * @param lineNo 请求内顺序行号
     * @param balanceId 受影响余额技术标识
     * @param dimension 记账发生时的库存维度
     * @param delta 有符号数量变化
     * @param beforeQuantity 分录作用前数量
     * @param afterQuantity 分录作用后数量
     * @return 插入行数
     */
    @Insert("""
        INSERT INTO wms_inventory_transaction_entry
          (id, transaction_id, line_no, balance_id, material_id, material_code, material_name, uom_code,
           batch_code, sn_code, warehouse_id, warehouse_code, location_id, location_code,
           quality_status, warehouse_status, delta_quantity, before_quantity, after_quantity)
        VALUES
          (#{id}, #{transactionId}, #{lineNo}, #{balanceId}, #{d.materialId}, #{d.materialCode},
           #{d.materialName}, #{d.uomCode}, #{d.batchCode}, #{d.snCode}, #{d.warehouseId},
           #{d.warehouseCode}, #{d.locationId}, #{d.locationCode}, #{d.qualityStatus},
           #{d.warehouseStatus}, #{delta}, #{beforeQuantity}, #{afterQuantity})
        """)
    int insertEntry(@Param("id") String id, @Param("transactionId") String transactionId,
                    @Param("lineNo") int lineNo, @Param("balanceId") String balanceId,
                    @Param("d") Dimension dimension, @Param("delta") BigDecimal delta,
                    @Param("beforeQuantity") BigDecimal beforeQuantity,
                    @Param("afterQuantity") BigDecimal afterQuantity);

}
