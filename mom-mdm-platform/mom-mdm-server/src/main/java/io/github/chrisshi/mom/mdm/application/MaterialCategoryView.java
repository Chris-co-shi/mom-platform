package io.github.chrisshi.mom.mdm.application;

import java.time.Instant;

/**
 * MaterialCategory 详情与分页使用的不可变 Application 视图。
 *
 * <p>该视图隔离 Infrastructure Entity 与 HTTP 边界，不包含 Result、MyBatis 或逻辑删除字段。默认保质期
 * 仅在 Material 创建选择 CATEGORY_DEFAULT 时复制，不会随分类后续变化回写已有 Material。</p>
 *
 * @param id String 技术主键
 * @param code 平台唯一且不可普通修改的业务编码
 * @param name 面向业务用户的名称
 * @param parentId 可选父分类 ID，根分类为空
 * @param sort 同级显示排序值
 * @param defaultShelfLifeDays 可选非负保质期天数默认建议
 * @param status ENABLED 或 DISABLED
 * @param createdAt 创建 UTC 时间
 * @param createdBy 创建 Actor ID
 * @param updatedAt 最近修改 UTC 时间
 * @param updatedBy 最近修改 Actor ID
 * @param version 乐观锁版本
 */
public record MaterialCategoryView(
        String id, String code, String name, String parentId, Integer sort,
        Integer defaultShelfLifeDays, String status, Instant createdAt, String createdBy,
        Instant updatedAt, String updatedBy, Long version) {
}
