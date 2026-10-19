package io.github.chrisshi.mom.wms.application;

/**
 * 库存质量判定状态；名称同时作为数据库值与 HTTP 字符串契约。
 */
public enum QualityStatus {
    /**
     * 尚未完成质量判定。
     */
    PENDING,
    /**
     * 已判定合格。
     */
    QUALIFIED,
    /**
     * 受限使用或需受控处理。
     */
    RESTRICTED,
    /**
     * 判定不合格。
     */
    REJECTED
}
