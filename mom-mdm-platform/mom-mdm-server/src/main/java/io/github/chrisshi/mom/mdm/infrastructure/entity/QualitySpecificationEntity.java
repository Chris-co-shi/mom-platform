package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** 物料、工厂和用途唯一确定的 MDM 质量标准身份。 */
@Getter @Setter @TableName("mdm_quality_specification")
public class QualitySpecificationEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String plantId;
    private String materialId;
    private String inspectionUsage;
    private Long plantVersion;
    private Integer materialRevisionNo;
    private Long materialVersion;
}
