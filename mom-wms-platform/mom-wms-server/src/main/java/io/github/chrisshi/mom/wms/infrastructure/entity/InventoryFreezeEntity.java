package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 仓储冻结限制的类型化持久化行。原始冻结事实受 PostgreSQL Trigger 保护，
 * 仅 remainingQuantity 允许通过受限原子 SQL 递减。
 */
@Getter
@Setter
@TableName("wms_inventory_freeze")
public class InventoryFreezeEntity extends BaseIdEntity {
    private String transactionId;
    private String originalBalanceId;
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
    private QualityStatus qualityStatus;
    private String reasonCode;
    private String note;
    private BigDecimal originalQuantity;
    private BigDecimal remainingQuantity;
    private String sourceType;
    private String sourceId;
    private String operatorId;
    private Instant frozenAt;
}
