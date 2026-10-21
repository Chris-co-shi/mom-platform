package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 盘点明细持久化实体。账面快照不可修改；COUNTING 阶段的实盘事实由受限 SQL 更新，
 * 且数据库触发器继续保护原始快照和确认后的记录。
 */
@Getter
@Setter
@TableName("wms_inventory_count_line")
public class InventoryCountLineEntity extends BaseIdEntity {
    private String taskId;
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
    private WarehouseStatus warehouseStatus;
    private BigDecimal bookQuantity;
    private BigDecimal countedQuantity;
    private String snapshotBy;
    private Instant snapshotAt;
    private String countedBy;
    private Instant countedAt;
}
