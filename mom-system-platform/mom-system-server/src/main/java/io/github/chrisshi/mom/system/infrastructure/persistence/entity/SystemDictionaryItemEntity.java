package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System Dictionary Item 的 MyBatis-Plus 行模型。
 *
 * <p>dictionaryId 是 System 同 Schema 内部关联，不进入 Consumer 契约。Entity 统一继承
 * {@link BaseEntity}；当前 Item 没有删除 API，逻辑删除字段在正常业务路径保持 false。</p>
 */
@Getter
@Setter
@TableName("system_dictionary_item")
public class SystemDictionaryItemEntity extends BaseEntity {
    @TableField("dictionary_id")
    private String dictionaryId;

    @TableField("item_code")
    private String itemCode;

    @TableField("item_label")
    private String itemLabel;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("enabled")
    private Boolean enabled;

    /** 允许更新时显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
