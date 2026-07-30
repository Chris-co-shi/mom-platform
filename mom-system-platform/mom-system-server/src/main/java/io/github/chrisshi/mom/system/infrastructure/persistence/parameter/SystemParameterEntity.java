package io.github.chrisshi.mom.system.infrastructure.persistence.parameter;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import lombok.Getter;
import lombok.Setter;

/**
 * System Parameter 的 MyBatis-Plus 行模型。
 *
 * <p>System 自有普通业务表统一继承 {@link BaseEntity}，获得 String ASSIGN_ID、创建/更新审计、
 * 乐观锁与逻辑删除字段。当前参数能力没有删除 API，{@code deleted} 在正常业务路径始终保持 false。</p>
 */
@Getter
@Setter
@TableName("system_parameter")
public class SystemParameterEntity extends BaseEntity {
    @TableField("scope_type")
    private ParameterScopeType scopeType;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("parameter_key")
    private String parameterKey;

    @TableField("value_type")
    private ParameterValueType valueType;

    @TableField("parameter_value")
    private String parameterValue;

    @TableField("enabled")
    private Boolean enabled;

    /** 允许更新时显式清空可选描述。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
