package io.github.chrisshi.mom.wms.application;

/** 库存仓储状态；名称同时作为数据库值与 HTTP 字符串契约。 */
public enum WarehouseStatus {
    /** 正常仓储状态。 */
    NORMAL,
    /** 冻结仓储状态。 */
    FROZEN
}
