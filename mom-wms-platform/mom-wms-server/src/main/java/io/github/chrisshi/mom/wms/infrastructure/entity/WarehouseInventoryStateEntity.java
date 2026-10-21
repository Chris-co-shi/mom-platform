package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * WMS 仓库库存启用状态行。
 * 表无 created_by，不应机械继承 BaseAuditEntity；状态转换由 Application 以条件更新控制。
 */
@Getter
@Setter
@TableName("wms_warehouse_inventory_state")
public class WarehouseInventoryStateEntity extends BaseIdEntity {
    private String warehouseId;
    private String warehouseCode;
    private String state;
    private String openingDocumentId;
    private String activatedBy;
    private Instant activatedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
}
