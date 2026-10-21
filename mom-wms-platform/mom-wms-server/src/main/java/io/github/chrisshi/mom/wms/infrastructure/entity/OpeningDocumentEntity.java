package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * WMS 期初单持久化行。审计字段复用 mom-data，DRAFT/POSTED 状态、
 * version CAS 和仓库库存生命周期仍由 Application 事务及专用 SQL 管控。
 * 单据不启用逻辑删除，过账后的业务事实必须留存。
 */
@Getter
@Setter
@TableName("wms_opening_document")
public class OpeningDocumentEntity extends BaseAuditEntity {
    private String documentNo;
    private String warehouseId;
    private String warehouseCode;
    private String status;
    private String transactionId;
    /** 单据明细版本由状态限定 SQL 推进，不在通用 updateById 上实施 @Version。 */
    private Long version;
    private String postedBy;
    private Instant postedAt;
}
