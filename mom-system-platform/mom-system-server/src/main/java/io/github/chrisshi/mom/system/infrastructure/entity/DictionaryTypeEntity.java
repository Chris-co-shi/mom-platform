package io.github.chrisshi.mom.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System V1 字典类型的数据库行模型。
 *
 * <p>该类型只属于 Dictionary Infrastructure，不得进入 Controller 契约或跨服务 API。它继承审计、乐观锁
 * 与逻辑删除能力；当前无删除 API，正常路径只更新名称、说明、启停和版本。并发正确性由数据库唯一约束
 * 与 MyBatis-Plus Version CAS 共同保证，数据库不可用时调用失败关闭。</p>
 */
@Getter
@Setter
@TableName("system_dictionary")
public class DictionaryTypeEntity extends BaseEntity {
    @TableField("dictionary_code")
    private String code;

    @TableField("dictionary_name")
    private String name;

    @TableField("enabled")
    private Boolean enabled;

    /** 可选说明允许在更新时被显式清空。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
