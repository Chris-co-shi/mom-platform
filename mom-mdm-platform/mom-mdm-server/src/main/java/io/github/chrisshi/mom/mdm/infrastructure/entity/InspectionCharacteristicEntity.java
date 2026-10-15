package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM 持有的 V1 数值型检验特性；默认单位保留技术版本快照。 */
@Getter @Setter @TableName("mdm_inspection_characteristic")
public class InspectionCharacteristicEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String defaultUomId;
    private Long defaultUomVersion;
    private Boolean enabled;
    @Version @TableField("version") private Long version = 0L;
}
