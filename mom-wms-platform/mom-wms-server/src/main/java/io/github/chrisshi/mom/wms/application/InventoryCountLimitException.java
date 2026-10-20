package io.github.chrisshi.mom.wms.application;

/** 盘点非零差异超过现有单笔库存记账 100 条安全上限。 */
public final class InventoryCountLimitException extends IllegalArgumentException {
    /** 构造盘点整单记账上限错误。 */
    public InventoryCountLimitException() { super("非零盘点差异超过单笔记账上限 100 条"); }
}
