package io.github.chrisshi.mom.mdm.api;

/**
 * 通用厂内位置的跨域只读快照，不包含库存、占用、容器或物流作业状态。
 *
 * @param id 稳定位置 ID
 * @param code 位置业务编码
 * @param name 当前名称
 * @param plantId 所属工厂 ID
 * @param plantTimeZoneId 工厂业务时区，存量待补时为 null
 * @param warehouseAreaId 可选仓储区域 ID；null 表示通用厂内位置
 * @param locationTypeId 动态位置分类 ID
 * @param revisionNo V1 没有业务修订时为 null
 * @param version 技术乐观锁版本
 * @param availableForNewUse 当前静态主数据链是否可供新引用
 * @param invalidReason 不可新引用时的稳定原因码
 */
public record LocationReference(
        String id, String code, String name, String plantId, String plantTimeZoneId,
        String warehouseAreaId, String locationTypeId, Integer revisionNo, Long version,
        boolean availableForNewUse, String invalidReason) {
}
