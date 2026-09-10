package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM Infrastructure 中的 ProductionLine 单表实体；仅依赖本地 Workshop 标识，不包含领域框架。 */
@Getter
@Setter
@TableName("mdm_production_line")
public class ProductionLineEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String workshopId;
    private String status;
}
