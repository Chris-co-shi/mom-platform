package io.github.chrisshi.mom.wms.application;

/**
 * 期初建账引用的 MDM 主数据不满足新业务使用条件。
 *
 * @param invalidReason MDM 返回的稳定不可用原因码
 */
public class OpeningInventoryReferenceException extends RuntimeException {
    private final String invalidReason;

    /**
     * @param invalidReason MDM 返回的稳定不可用原因码；未知原因允许为 null
     */
    public OpeningInventoryReferenceException(String invalidReason) {
        super("期初建账引用的仓库主数据当前不可用");
        this.invalidReason = invalidReason;
    }

    /**
     * @return MDM 返回的稳定不可用原因码
     */
    public String invalidReason() {
        return invalidReason;
    }
}
