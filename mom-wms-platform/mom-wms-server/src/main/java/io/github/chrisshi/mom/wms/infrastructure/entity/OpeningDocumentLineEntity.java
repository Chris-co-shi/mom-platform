package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** 期初单明细：已过账内容不可更改，业务上的草稿操作统一由 Application 加锁后执行。 */
@Getter
@Setter
@TableName("wms_opening_document_line")
public class OpeningDocumentLineEntity extends BaseIdEntity {
    private String documentId;
    private Integer lineNo;
    private String materialId;
    private String materialCode;
    private String materialName;
    private String batchCode;
    private String snCode;
    private String warehouseId;
    private String warehouseCode;
    private String locationId;
    private String locationCode;
    private QualityStatus qualityStatus;
    private WarehouseStatus warehouseStatus;
    private BigDecimal quantity;
    private String uomCode;
    /** 数据库默认值，不由实体写入覆盖。 */
    private Instant createdAt;
}
