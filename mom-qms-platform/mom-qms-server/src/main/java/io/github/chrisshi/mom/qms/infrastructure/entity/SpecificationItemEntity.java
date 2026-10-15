package io.github.chrisshi.mom.qms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseCreatedEntity;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** 已发布质量标准中的数值范围及其特性、方法、单位快照引用。 */
@Getter @Setter @TableName("qms_specification_item")
public class SpecificationItemEntity extends BaseCreatedEntity {
    private String revisionId;
    private Integer sequenceNo;
    private String characteristicId;
    private Long characteristicVersion;
    private String methodId;
    private Long methodVersion;
    private String uomId;
    private Long uomVersion;
    private BigDecimal lowerLimit;
    private BigDecimal targetValue;
    private BigDecimal upperLimit;
    private Boolean mandatory;
}
