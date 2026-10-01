package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 权威计量单位目录的单表持久化实体，属于 MDM Infrastructure 数据库边界。
 *
 * <p>一个单位只属于一个类别，基准身份、Code、Symbol 与类别创建后不可普通修改；基准单位不独立启停。
 * 引用和生命周期由 Application 保证，唯一性及乐观并发由 PostgreSQL 与 BaseEntity 兜底。</p>
 */
@Getter
@Setter
@TableName("mdm_uom")
public class UomEntity extends BaseEntity {
    private String code;
    private String name;
    private String symbol;
    private String categoryId;
    private Boolean referenceUnit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ucumNotApplicableReason;
    private String status;
}
