package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System Dictionary 的 MyBatis-Plus 行模型。
 *
 * <p>Entity 仅位于 Infrastructure，继承 String ID 与统一审计并独立声明乐观锁。字典没有删除 API，
 * 因而不继承逻辑删除基类；Code 格式和唯一性由 V2 数据库约束最终兜底。</p>
 */
@Getter
@Setter
@TableName("system_dictionary")
public class SystemDictionaryEntity extends BaseAuditEntity {
    @TableField("dictionary_code")
    private String dictionaryCode;

    @TableField("dictionary_name")
    private String dictionaryName;

    @TableField("enabled")
    private Boolean enabled;

    @Version
    @TableField("version")
    private Long version = 0L;

    /** 允许更新时显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
