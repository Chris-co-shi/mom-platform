package io.github.chrisshi.mom.qms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** V1 数值型检验特性；默认单位保留创建或更新时的 MDM 技术版本快照。 */
@Getter @Setter @TableName("qms_inspection_characteristic")
public class InspectionCharacteristicEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String defaultUomId;
    private Long defaultUomVersion;
    private Boolean enabled;
    @Version @TableField("version") private Long version = 0L;
}
