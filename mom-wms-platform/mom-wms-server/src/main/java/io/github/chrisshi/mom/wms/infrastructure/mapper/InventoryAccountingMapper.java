package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryBalanceEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/** 库存记账的 PostgreSQL 原子写入；所有写操作由 Application 公共事务入口编排。 */
@Mapper
public interface InventoryAccountingMapper extends MomBaseMapper<InventoryBalanceEntity> {
    /** 使用数据库唯一键领取业务请求；冲突时返回 null，并等待在途同键事务结束。 */
    @Select("""
            INSERT INTO wms_inventory_transaction (id, request_key, request_hash)
            VALUES (#{id}, #{requestKey}, #{requestHash})
            ON CONFLICT (request_key) DO NOTHING RETURNING id
            """)
    String claim(@Param("id") String id, @Param("requestKey") String requestKey,
                 @Param("requestHash") String requestHash);

    /** 读取已提交请求的指纹，供重放或冲突判断。 */
    @Select("SELECT request_hash FROM wms_inventory_transaction WHERE request_key = #{requestKey}")
    String hashOf(@Param("requestKey") String requestKey);

    /** 读取已提交交易 ID。 */
    @Select("SELECT id FROM wms_inventory_transaction WHERE request_key = #{requestKey}")
    String idOf(@Param("requestKey") String requestKey);

    /** 对唯一余额维度做原子增加；冲突时由唯一约束串行化。 */
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
            RETURNING id
            """)
    String increase(@Param("id") String id, @Param("d") Dimension dimension,
                 @Param("delta") BigDecimal delta);

    /** 条件更新已存在余额；行锁与非负条件共同阻止并发超扣。 */
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
             RETURNING id
            """)
    String decrease(@Param("d") Dimension dimension, @Param("delta") BigDecimal delta);

    /** 追加一条带符号的不可变分录。 */
    @Insert("""
            INSERT INTO wms_inventory_transaction_entry
              (id, transaction_id, line_no, balance_id, delta_quantity)
            VALUES (#{id}, #{transactionId}, #{lineNo}, #{balanceId}, #{delta})
            """)
    int insertEntry(@Param("id") String id, @Param("transactionId") String transactionId,
                    @Param("lineNo") int lineNo, @Param("balanceId") String balanceId,
                    @Param("delta") BigDecimal delta);
}
