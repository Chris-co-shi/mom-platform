package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 不可变库存交易持久化模型；旧交易新增事实列为空表示无法从 Phase 1 数据还原。 */
@Getter
@Setter
@TableName("wms_inventory_transaction")
public class InventoryTransactionEntity extends BaseIdEntity {
    /** 幂等请求标识，在交易表中全局唯一。 */
    private String requestKey;
    /** 由完整记账请求计算的 SHA-256 指纹。 */
    private String requestHash;
    /** 交易业务类型，由实际记账用例定义。 */
    private String transactionType;
    /** 业务来源类别，例如来源单据的类型。 */
    private String sourceType;
    /** 来源业务对象的稳定标识。 */
    private String sourceId;
    /** 实际承担本次操作责任的用户或稳定系统主体标识。 */
    private String operatorId;
    /** 数据库记账时间，使用 UTC 时间戳。 */
    private Instant occurredAt;
}
