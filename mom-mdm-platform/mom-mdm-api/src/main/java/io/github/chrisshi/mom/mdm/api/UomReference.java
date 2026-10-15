package io.github.chrisshi.mom.mdm.api;

/**
 * 计量单位供业务域只读消费的当前快照，不能代替带规则版本的历史换算记录。
 *
 * @param id 稳定 String 主键
 * @param code 单位业务编码
 * @param name 当前名称
 * @param symbol 单位符号
 * @param categoryId 单位类别 ID
 * @param dimensionId 七维量纲 ID，类别缺失时为 null
 * @param referenceUnit 是否类别基准单位
 * @param revisionNo 业务修订号；UOM V1 无独立业务修订时为 null
 * @param version 技术乐观锁版本
 * @param availableForNewUse 完整父链启用时为 true
 * @param invalidReason 不可新引用时的稳定原因码
 */
public record UomReference(String id, String code, String name, String symbol,
                           String categoryId, String dimensionId, Boolean referenceUnit,
                           Integer revisionNo, Long version, boolean availableForNewUse,
                           String invalidReason) {
}
