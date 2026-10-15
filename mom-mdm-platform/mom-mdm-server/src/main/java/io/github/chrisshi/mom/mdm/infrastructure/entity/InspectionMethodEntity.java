package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** 检验方法静态定义，不包含实验室执行或仪器通信。 */
@Getter @Setter @TableName("mdm_inspection_method")
public class InspectionMethodEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String description;
    private Boolean enabled;
    @Version @TableField("version") private Long version = 0L;
}
