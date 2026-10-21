package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryFreezeEntity;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.Balance;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 冻结事实受限写操作；普通单表查询统一通过 mom-data 的 Freeze/Unfreeze QueryMapper。
 * 本接口不继承 BaseMapper，避免不经业务事务就直接修改原始冻结事实或历史记录。
 */
@Mapper
public interface InventoryFreezeMapper {

    /** 解冻必须在同一事务中锁定冻结限制记录，不得使用普通 selectById 替代。 */
    @Select("SELECT * FROM wms_inventory_freeze WHERE id=#{id} FOR UPDATE")
    InventoryFreezeEntity lockFreeze(@Param("id") String id);

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


    /** 条件更新是并发部分解冻的数据库级非负保护。 */
    @Update("UPDATE wms_inventory_freeze SET remaining_quantity=remaining_quantity-#{quantity} " +
            "WHERE id=#{id} AND remaining_quantity >= #{quantity}")
    int decreaseFreezeRemaining(@Param("id") String id, @Param("quantity") BigDecimal quantity);


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
