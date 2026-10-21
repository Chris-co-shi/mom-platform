package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** 解冻请求的不可变历史行，由数据库 Trigger 禁止 UPDATE/DELETE。 */
@Getter
@Setter
@TableName("wms_inventory_unfreeze")
public class InventoryUnfreezeEntity extends BaseIdEntity {
    private String requestKey;
    private String requestHash;
    private String freezeId;
    private String transactionId;
    private BigDecimal quantity;
    private String note;
    private String sourceType;
    private String sourceId;
    private String operatorId;
    private Instant occurredAt;
}
