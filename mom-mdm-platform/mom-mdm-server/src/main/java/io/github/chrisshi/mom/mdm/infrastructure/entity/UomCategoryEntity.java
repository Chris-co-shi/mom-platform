package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 计量单位类别单表持久化实体，属于 MDM Infrastructure 数据库边界。
 *
 * <p>类别定义业务可换算边界并只引用一个量纲；无物理外键，引用完整性和父级启用状态由 Application
 * 在本地事务内保证。类型无共享状态，数据库失败时直接回滚。</p>
 */
@Getter
@Setter
@TableName("mdm_uom_category")
public class UomCategoryEntity extends BaseEntity {
    private String code;
    private String nameZh;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;
    private String dimensionId;
    private String status;
}
