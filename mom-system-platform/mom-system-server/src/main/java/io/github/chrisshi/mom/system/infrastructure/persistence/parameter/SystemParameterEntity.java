package io.github.chrisshi.mom.system.infrastructure.persistence.parameter;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import lombok.Getter;
import lombok.Setter;

/**
 * System Parameter 的 MyBatis-Plus 行模型。
 *
 * <p>该 Entity 仅位于 Infrastructure，继承 String ID 与审计能力并独立声明乐观锁；参数禁止物理删除且
 * 没有删除接口，因此不继承带逻辑删除的 BaseEntity。数据库约束是 Scope、类型与唯一性的最终兜底。</p>
 */
@Getter
@Setter
@TableName("system_parameter")
public class SystemParameterEntity extends BaseAuditEntity {
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

    @Version
    @TableField("version")
    private Long version = 0L;

    /** 允许更新时显式清空可选描述。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
