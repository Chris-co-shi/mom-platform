package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 不可变库存分录持久化模型；维度与数量快照独立于余额行保留。
 */
@Getter
@Setter
@TableName("wms_inventory_transaction_entry")
public class InventoryTransactionEntryEntity extends BaseIdEntity {
    /**
     * 所属交易标识。
     */
    private String transactionId;
    /**
     * 交易内从 1 开始、按请求顺序递增的行号。
     */
    private Integer lineNo;
    /**
     * 受影响余额行的技术关联标识，不作为还原历史的依据。
     */
    private String balanceId;
    /**
     * 记账发生时的物料稳定标识。
     */
    private String materialId;
    /**
     * 记账发生时的物料编码快照。
     */
    private String materialCode;
    /**
     * 记账发生时的物料名称快照。
     */
    private String materialName;
    /**
     * 记账发生时的计量单位编码快照。
     */
    private String uomCode;
    /**
     * 记账发生时的批次；非批次库存为空。
     */
    private String batchCode;
    /**
     * 记账发生时的序列号；非 SN 库存为空。
     */
    private String snCode;
    /**
     * 记账发生时的仓库稳定标识。
     */
    private String warehouseId;
    /**
     * 记账发生时的仓库编码快照。
     */
    private String warehouseCode;
    /**
     * 记账发生时的库位稳定标识。
     */
    private String locationId;
    /**
     * 记账发生时的库位编码快照。
     */
    private String locationCode;
    /**
     * 记账发生时的质量状态。
     */
    private QualityStatus qualityStatus;
    /**
     * 记账发生时的仓储状态。
     */
    private WarehouseStatus warehouseStatus;
    /**
     * 本分录有符号数量变化；正数增加，负数减少。
     */
    private BigDecimal deltaQuantity;
    /**
     * 应用本分录前该余额维度上的数量快照。
     */
    private BigDecimal beforeQuantity;
    /**
     * 应用本分录后该余额维度上的数量快照。
     */
    private BigDecimal afterQuantity;
}
