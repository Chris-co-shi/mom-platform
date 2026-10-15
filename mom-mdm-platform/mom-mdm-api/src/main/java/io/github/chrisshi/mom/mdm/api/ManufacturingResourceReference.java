package io.github.chrisshi.mom.mdm.api;

/**
 * 制造资源目录的跨域只读快照，仅表示当前主数据能否用于新业务。
 *
 * @param resourceType PLANT、WORKSHOP、PRODUCTION_LINE 或 WORKSTATION
 * @param id 稳定 String 主键
 * @param code 业务编码
 * @param name 当前名称
 * @param parentId 直接父级 ID；Plant 为 null
 * @param plantId 所属权威工厂 ID
 * @param timeZoneId 工厂业务 IANA 时区；存量未补齐时为 null
 * @param revisionNo 业务修订号；此类静态资源 V1 无独立业务修订时为 null
 * @param version 技术乐观锁版本
 * @param availableForNewUse 父链完整、启用且时区已配置时为 true
 * @param invalidReason 不可新引用时的稳定原因码
 */
public record ManufacturingResourceReference(
        String resourceType, String id, String code, String name, String parentId,
        String plantId, String timeZoneId, Integer revisionNo, Long version,
        boolean availableForNewUse, String invalidReason) {
}
