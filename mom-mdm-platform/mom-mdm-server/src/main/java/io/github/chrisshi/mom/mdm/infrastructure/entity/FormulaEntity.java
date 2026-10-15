package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 配方身份、工厂和产出批量基准的 MDM 单表持久化实体；修订由独立表持有。 */
@Getter
@Setter
@TableName("mdm_formula")
public class FormulaEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String plantId;
    private String outputMaterialId;
    private BigDecimal baseBatchQuantity;
    private String baseUomId;
}
