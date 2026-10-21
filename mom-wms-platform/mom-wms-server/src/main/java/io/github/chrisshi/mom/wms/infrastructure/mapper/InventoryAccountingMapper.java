package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;



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
public interface InventoryAccountingMapper {
    // 数据库原子 SQL 接口：刻意不继承 BaseMapper，防止库存权威余额被普通 CRUD 任意修改。
    public record TransferReplay(String transactionId, String transactionType, String sourceType, String sourceId,
                                 String operatorId, String balanceId, String targetLocationId, BigDecimal quantity) { }

    public record BalanceChange(String id, BigDecimal beforeQuantity, BigDecimal afterQuantity) { }

    public record Balance(String id, String materialId, String materialCode, String materialName, String uomCode,
                          String batchCode, String snCode, String warehouseId, String warehouseCode,
                          String locationId, String locationCode, QualityStatus qualityStatus,
                          WarehouseStatus warehouseStatus, BigDecimal quantity) { }



    /** 同一事务按稳定键锁定库位或 SN；所有 SN 记账方共用此锁以阻止位置校验期间出现幻影余额。 */
    @Select("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(#{key}, 0))")
    int lockAccountingKey(@Param("key") String key);

    /** 仓库事务级锁取得后读取已提交的持久盘点锁及任务状态。 */
    @Select("SELECT l.task_id FROM wms_inventory_count_scope_lock l JOIN wms_inventory_count_task t ON t.id=l.task_id WHERE l.warehouse_id=#{warehouseId} AND t.status='CONFIRMED'")
    String confirmedCountOwner(@Param("warehouseId") String warehouseId);

    /** 仓库事务级锁取得后读取任意有效盘点锁。 */
    @Select("SELECT task_id FROM wms_inventory_count_scope_lock WHERE warehouse_id=#{warehouseId}")
    String countLockOwner(@Param("warehouseId") String warehouseId);

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
    TransferReplay findTransferReplay(@Param("requestKey") String requestKey);
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
    BalanceChange increase(@Param("id") String id, @Param("d") Dimension dimension,
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
    BalanceChange decrease(@Param("d") Dimension dimension,
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
