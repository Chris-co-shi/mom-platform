package io.github.chrisshi.mom.mdm.application;

import java.time.Instant;

/**
 * Material 详情和分页使用的不可变 Application 视图。
 *
 * <p>该视图隔离数据库实体与 HTTP Result，不包含逻辑删除、包装、批次、库存或供应商信息。类型无共享
 * 可变状态且线程安全；数据库不可用时由 Application 直接失败，不生成不完整视图。</p>
 *
 * @param id String 技术主键
 * @param code 平台全局唯一且不可普通修改的业务编码
 * @param name 面向业务用户的名称
 * @param categoryId 唯一叶子物料分类 ID
 * @param baseUomId 基础计量单位 ID
 * @param shelfLifeDays 可选非负保质期天数
 * @param status ENABLED 或 DISABLED
 * @param createdAt 创建 UTC 时间
 * @param createdBy 创建 Actor ID
 * @param updatedAt 最近修改 UTC 时间
 * @param updatedBy 最近修改 Actor ID
 * @param version 乐观锁版本
 */
public record MaterialView(
        String id,
        String code,
        String name,
        String categoryId,
        String baseUomId,
        Integer shelfLifeDays,
        String status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Long version) {
}
