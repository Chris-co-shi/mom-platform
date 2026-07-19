package io.github.chrisshi.mom.wms.api.delivery;

import java.math.BigDecimal;

/**
 * 创建供应商送货通知时的一条预期到货明细。
 *
 * <p>该类型位于 WMS API 模块，只表达跨服务命令契约，不依赖 Controller、持久化实体、Mapper 或 WMS Server。
 * 数量使用 {@link BigDecimal}，避免浮点误差；物料编码、供应商批次和计量单位均是业务标识，不使用技术主键
 * 替代。</p>
 *
 * @param lineNumber 外部送货单行号，同一送货通知内必须唯一且大于零
 * @param materialCode 物料业务编码
 * @param supplierBatchNumber 供应商批次号
 * @param expectedQuantity 预期到货数量，必须大于零
 * @param unit 受控计量单位编码
 */
public record CreateSupplierDeliveryLineCommand(
        int lineNumber,
        String materialCode,
        String supplierBatchNumber,
        BigDecimal expectedQuantity,
        String unit) {
}
