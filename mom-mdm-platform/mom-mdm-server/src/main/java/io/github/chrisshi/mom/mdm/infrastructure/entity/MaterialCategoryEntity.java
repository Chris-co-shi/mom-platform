package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * MaterialCategory 单表持久化实体，属于 MDM Infrastructure 数据库边界。
 *
 * <p>该类型映射动态物料分类树及 Material 创建时可读取的默认建议值，不承载 Material、UOM 或规则引擎
 * 语义。父级完整性和循环约束由同一本地事务内的 Application 校验，数据库唯一约束与 Version 分别处理
 * 并发 Code 冲突和更新冲突；数据库不可用时操作直接失败并回滚。可清空字段显式使用 ALWAYS 更新策略，
 * 以支持移动回根分类及撤销可选保质期建议。</p>
 */
@Getter
@Setter
@TableName("mdm_material_category")
public class MaterialCategoryEntity extends BaseEntity {
    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String parentId;
    private Integer sort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer defaultShelfLifeDays;
    private String status;
}
