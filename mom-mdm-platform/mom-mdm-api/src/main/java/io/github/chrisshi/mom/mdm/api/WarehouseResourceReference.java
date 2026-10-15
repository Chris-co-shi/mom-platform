package io.github.chrisshi.mom.mdm.api;

/**
 * 仓库或库区的静态主数据引用，不包含 WMS 库存、占用或物流作业事实。
 *
 * @param resourceType WAREHOUSE 或 WAREHOUSE_AREA
 * @param id 稳定 String 主键
 * @param code 业务编码
 * @param name 当前名称
 * @param parentId Warehouse 的 Plant ID 或 WarehouseArea 的 Warehouse ID
 * @param plantId 所属工厂 ID，父链缺失时可为 null
 * @param plantTimeZoneId 工厂业务时区，存量待补或父链缺失时可为 null
 * @param revisionNo 此类静态资源 V1 无独立业务修订时为 null
 * @param version 技术乐观锁版本
 * @param availableForNewUse 当前静态父链是否完整启用
 * @param invalidReason 不可新引用时的稳定原因码
 */
public record WarehouseResourceReference(
        String resourceType, String id, String code, String name, String parentId,
        String plantId, String plantTimeZoneId, Integer revisionNo, Long version,
        boolean availableForNewUse, String invalidReason) {
}
