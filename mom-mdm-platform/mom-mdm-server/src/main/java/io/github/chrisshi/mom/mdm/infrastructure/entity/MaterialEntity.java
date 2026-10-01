package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Material 单表持久化实体，属于 MDM Infrastructure 数据库边界。
 *
 * <p>该类型只保存物料业务身份、唯一分类、基础单位、可选保质期和简单生命周期，不承载包装、批次、
 * 库存、供应商或动态规格。引用完整性和并发互斥由 Application 本地事务与行锁保证，数据库不可用时
 * 操作直接失败且不降级；可选保质期允许被明确清空。</p>
 */
@Getter
@Setter
@TableName("mdm_material")
public class MaterialEntity extends BaseEntity {
    private String code;
    private String name;
    private String categoryId;
    private String baseUomId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer shelfLifeDays;
    private String status;
}
