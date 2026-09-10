package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM Infrastructure 中的 Workstation 单表实体；并发和审计语义复用 BaseEntity。 */
@Getter
@Setter
@TableName("mdm_workstation")
public class WorkstationEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String productionLineId;
    private String status;
}
