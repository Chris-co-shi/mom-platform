package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** MySQL 记账适配所需的受限语句，由同一个 WMS 本地事务组合执行。 */
@Mapper
public interface MysqlInventoryAccountingMapper {
    /** 持有行锁的权威库存余额和仓库归属。 */
    record LockedBalance(String id, String warehouseId, BigDecimal quantity) { }

    /** 写入不可变交易；请求键冲突由调用方按目标约束识别。 */
    @Insert("""
        INSERT INTO wms_inventory_transaction
          (id, request_key, request_hash, transaction_type, source_type, source_id, operator_id)
        VALUES (#{id}, #{requestKey}, #{requestHash}, #{transactionType}, #{sourceType}, #{sourceId}, #{operatorId})
        """)
    int insertTransaction(@Param("id") String id, @Param("requestKey") String requestKey,
                          @Param("requestHash") String requestHash, @Param("transactionType") String transactionType,
                          @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                          @Param("operatorId") String operatorId);

    /** 使用唯一索引的完整维度加锁；仓库归属由调用方校验。 */
    @Select("""
        SELECT id, warehouse_id, quantity FROM wms_inventory_balance
         WHERE material_id = #{d.materialId}
           AND batch_code_is_null = (#{d.batchCode} IS NULL)
           AND batch_code_key = COALESCE(#{d.batchCode}, '')
           AND sn_code_is_null = (#{d.snCode} IS NULL)
           AND sn_code_key = COALESCE(#{d.snCode}, '')
           AND location_id = #{d.locationId}
           AND quality_status = #{d.qualityStatus}
           AND warehouse_status = #{d.warehouseStatus}
         FOR UPDATE
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @ConstructorArgs({@Arg(column = "id", javaType = String.class),
        @Arg(column = "warehouse_id", javaType = String.class),
        @Arg(column = "quantity", javaType = BigDecimal.class)})
    LockedBalance lockBalance(@Param("d") Dimension dimension);

    /** 创建新维度余额；主键及正余额 SN 冲突直接暴露。 */
    @Insert("""
        INSERT INTO wms_inventory_balance
          (id, material_id, material_code, material_name, uom_code, batch_code, sn_code,
           warehouse_id, warehouse_code, location_id, location_code, quality_status, warehouse_status, quantity)
        VALUES
          (#{id}, #{d.materialId}, #{d.materialCode}, #{d.materialName}, #{d.uomCode},
           #{d.batchCode}, #{d.snCode}, #{d.warehouseId}, #{d.warehouseCode},
           #{d.locationId}, #{d.locationCode}, #{d.qualityStatus}, #{d.warehouseStatus}, #{delta})
        """)
    int insertBalance(@Param("id") String id, @Param("d") Dimension dimension, @Param("delta") BigDecimal delta);

    /** 更新已锁定余额，并保留数据库级非负条件。 */
    @Update("""
        UPDATE wms_inventory_balance SET quantity = quantity + #{delta}, updated_at = CURRENT_TIMESTAMP(6)
         WHERE id = #{id} AND quantity >= -#{delta}
        """)
    int changeQuantity(@Param("id") String id, @Param("delta") BigDecimal delta);

    /** 读取当前事务刚写入的实际余额，供不可变分录留存。 */
    @Select("SELECT quantity FROM wms_inventory_balance WHERE id = #{id}")
    BigDecimal quantity(@Param("id") String id);
}
