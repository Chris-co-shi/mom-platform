package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseCreatedEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 某一配方修订的有序物料明细；不携带 Lot、库存或质量结果。 */
@Getter
@Setter
@TableName("mdm_formula_item")
public class FormulaItemEntity extends BaseCreatedEntity {
    private String revisionId;
    private Integer sequenceNo;
    private String materialId;
    private BigDecimal quantity;
    private String uomId;
}
