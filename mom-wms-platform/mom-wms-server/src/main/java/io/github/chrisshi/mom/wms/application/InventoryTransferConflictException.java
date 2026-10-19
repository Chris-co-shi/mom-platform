package io.github.chrisshi.mom.wms.application;

/** 库位移动的库存、状态、SN 或幂等内容发生业务冲突；当前事务必须整体回滚。 */
public class InventoryTransferConflictException extends RuntimeException {
    /** @param message 供服务内部定位冲突的说明，不直接返回给浏览器 */
    public InventoryTransferConflictException(String message) { super(message); }
}
