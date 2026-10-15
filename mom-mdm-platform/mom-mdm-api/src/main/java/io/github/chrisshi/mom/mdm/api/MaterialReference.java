package io.github.chrisshi.mom.mdm.api;

/**
 * 物料主数据供其他业务域只读消费的稳定快照。
 *
 * <p>{@code availableForNewUse} 仅判断当前主数据能否被新业务引用，不否定历史事实。
 * {@code revisionNo} 是业务修订，{@code version} 是技术乐观锁版本；二者不能混用。</p>
 *
 * @param id 稳定 String 主键
 * @param code 业务编码
 * @param name 当前展示名称
 * @param categoryId 分类 ID
 * @param baseUomId 不可普通修改的基础单位 ID
 * @param materialType 物料用途
 * @param batchTrackingPolicy 独立批次追踪策略
 * @param shelfLifeDays 可选保质期天数
 * @param revisionNo 业务修订号
 * @param version 技术锁版本
 * @param availableForNewUse 是否允许新业务引用
 * @param invalidReason 不可新引用时的稳定原因码，可用时为空
 */
public record MaterialReference(
        String id, String code, String name, String categoryId, String baseUomId,
        String materialType, String batchTrackingPolicy, Integer shelfLifeDays,
        Integer revisionNo, Long version, boolean availableForNewUse, String invalidReason) {
}
