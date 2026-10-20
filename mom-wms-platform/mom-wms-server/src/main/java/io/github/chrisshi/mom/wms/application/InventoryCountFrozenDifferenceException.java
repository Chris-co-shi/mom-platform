package io.github.chrisshi.mom.wms.application;

/** FROZEN 余额的盘点差异无法自动归属到现有冻结限制，V1 禁止确认和调整。 */
public final class InventoryCountFrozenDifferenceException extends IllegalStateException {
    /** 构造需要人工核对冻结限制的盘点差异错误。 */
    public InventoryCountFrozenDifferenceException() { super("冻结库存存在盘点差异，请人工核对冻结限制后重新盘点"); }
}
