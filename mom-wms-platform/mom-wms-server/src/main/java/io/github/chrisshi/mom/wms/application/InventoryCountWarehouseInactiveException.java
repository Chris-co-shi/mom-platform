package io.github.chrisshi.mom.wms.application;

/** 所选仓库尚未完成 WMS 期初建账与库存启用，不能创建或开始盘点。 */
public final class InventoryCountWarehouseInactiveException extends IllegalStateException {
    /** 构造仓库库存生命周期未满足盘点前提的业务错误。 */
    public InventoryCountWarehouseInactiveException() {
        super("请先完成期初建账并启用仓库库存");
    }
}
