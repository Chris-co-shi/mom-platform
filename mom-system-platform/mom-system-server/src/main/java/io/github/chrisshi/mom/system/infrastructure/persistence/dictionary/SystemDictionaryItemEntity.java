package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System Dictionary Item 的 MyBatis-Plus 行模型。
 *
 * <p>dictionaryId 是 System 同 Schema 内部关联，不进入 Consumer 契约。Entity 不含逻辑删除、Tree、
 * Metadata、Alias 或 Locale；同字典 Code 唯一性、FK 和排序范围由 V2 约束兜底。</p>
 */
@Getter
@Setter
@TableName("system_dictionary_item")
public class SystemDictionaryItemEntity extends BaseAuditEntity {
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

    @Version
    @TableField("version")
    private Long version = 0L;

    /** 允许更新时显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
