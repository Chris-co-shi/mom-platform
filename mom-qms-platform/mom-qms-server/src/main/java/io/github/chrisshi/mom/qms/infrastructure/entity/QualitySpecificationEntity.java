package io.github.chrisshi.mom.qms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** 物料、工厂和用途唯一确定的质量标准身份，引用版本是 MDM 校验快照。 */
@Getter @Setter @TableName("qms_quality_specification")
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
