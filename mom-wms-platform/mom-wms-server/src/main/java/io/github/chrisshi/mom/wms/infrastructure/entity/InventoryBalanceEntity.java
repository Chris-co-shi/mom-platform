package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** 库存唯一维度上的权威余额，仅供记账 Mapper 映射，业务写入统一经过记账服务。 */
@Getter
@Setter
@TableName("wms_inventory_balance")
public class InventoryBalanceEntity extends BaseIdEntity {
    private String materialId;
    private String materialCode;
    private String materialName;
    private String uomCode;
    private String batchCode;
    private String snCode;
    private String warehouseId;
    private String warehouseCode;
    private String locationId;
    private String locationCode;
    private String qualityStatus;
    private String warehouseStatus;
    private BigDecimal quantity;
    private Instant updatedAt;
}
