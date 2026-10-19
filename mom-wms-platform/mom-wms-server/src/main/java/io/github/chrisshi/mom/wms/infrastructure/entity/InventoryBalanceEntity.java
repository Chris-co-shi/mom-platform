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
 * 库存唯一维度上的权威余额，仅供记账 Mapper 映射，业务写入统一经过记账服务。
 */
@Getter
@Setter
@TableName("wms_inventory_balance")
public class InventoryBalanceEntity extends BaseIdEntity {
    /**
     * MDM 物料稳定标识；与批次、SN、库位及状态共同确定唯一余额维度。
     */
    private String materialId;
    /**
     * 物料编码展示快照，不参与余额唯一键。
     */
    private String materialCode;
    /**
     * 物料名称展示快照，不参与余额唯一键。
     */
    private String materialName;
    /**
     * 记账数量采用的物料基本单位编码。
     */
    private String uomCode;
    /**
     * 批次维度；非批次物料为空。
     */
    private String batchCode;
    /**
     * 序列号维度；非 SN 管理物料为空。
     */
    private String snCode;
    /**
     * MDM 仓库稳定标识；与库位归属保持一致。
     */
    private String warehouseId;
    /**
     * 仓库编码展示快照。
     */
    private String warehouseCode;
    /**
     * MDM 库位稳定标识。
     */
    private String locationId;
    /**
     * 库位编码展示快照。
     */
    private String locationCode;
    /**
     * 质量判定维度；状态变化通过后续库存记账形成新维度。
     */
    private QualityStatus qualityStatus;
    /**
     * 冻结等仓储维度；不改变库存汇总总量口径。
     */
    private WarehouseStatus warehouseStatus;
    /**
     * 该唯一库存维度上的非负权威数量。
     */
    private BigDecimal quantity;
    /**
     * 最近一次余额记账时间。
     */
    private Instant updatedAt;
}
