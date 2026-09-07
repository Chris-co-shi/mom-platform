package io.github.chrisshi.mom.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System V1 字典条目的数据库行模型。
 *
 * <p>{@code typeId} 只用于 mom_system 内部关联，跨服务消费者使用 Dictionary Code 与稳定 Key。条目禁用
 * 后仍保留并可解析历史值，只有新选择列表会过滤它。该 Entity 不承担业务校验，所有写入由 Dictionary
 * Application 在本地事务中编排；数据库故障时不缓存或伪造成功。</p>
 */
@Getter
@Setter
@TableName("system_dictionary_item")
public class DictionaryItemEntity extends BaseEntity {
    @TableField("dictionary_id")
    private String typeId;

    @TableField("item_code")
    private String key;

    @TableField("item_label")
    private String value;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("enabled")
    private Boolean enabled;

    /** 可选说明允许在更新时被显式清空。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
