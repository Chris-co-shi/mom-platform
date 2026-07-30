package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System Dictionary 的 MyBatis-Plus 行模型。
 *
 * <p>System 自有普通业务表统一继承 {@link BaseEntity}。当前字典没有删除 API，逻辑删除能力只作为统一
 * 持久化基线存在，正常业务路径不会把 {@code deleted} 改为 true。</p>
 */
@Getter
@Setter
@TableName("system_dictionary")
public class SystemDictionaryEntity extends BaseEntity {
    @TableField("dictionary_code")
    private String dictionaryCode;

    @TableField("dictionary_name")
    private String dictionaryName;

    @TableField("enabled")
    private Boolean enabled;

    /** 允许更新时显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
