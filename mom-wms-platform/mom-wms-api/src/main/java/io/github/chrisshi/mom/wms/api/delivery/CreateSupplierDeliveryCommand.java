package io.github.chrisshi.mom.wms.api.delivery;

import java.time.Instant;
import java.util.List;

/**
 * 创建供应商送货通知的 WMS 命令契约。
 *
 * <p>{@code sourceSystem + externalRequestId} 构成外部请求幂等身份。WMS 必须把该身份写入自己的 PostgreSQL
 * 权威存储，并使用请求指纹区分安全重放和冲突重用；调用方不得依赖 Redis 临时键作为最终业务正确性依据。</p>
 *
 * <p>命令只描述送货预告，不代表实物已经收货，也不会直接增加库存。库存事实、质量状态和库位关系必须在后续
 * PDA 收货与质量放行切片中建立。</p>
 *
 * @param sourceSystem 稳定来源系统编码，例如 ERP 或 SUPPLIER_PORTAL
 * @param externalRequestId 来源系统请求唯一标识
 * @param supplierCode 供应商业务编码
 * @param factoryCode 工厂业务编码
 * @param warehouseCode 目标仓库业务编码
 * @param deliveryNumber 供应商或 ERP 送货单号
 * @param expectedArrivalAt 预期到达时间，统一为 UTC 时间点
 * @param lines 至少一条送货明细
 */
public record CreateSupplierDeliveryCommand(
        String sourceSystem,
        String externalRequestId,
        String supplierCode,
        String factoryCode,
        String warehouseCode,
        String deliveryNumber,
        Instant expectedArrivalAt,
        List<CreateSupplierDeliveryLineCommand> lines) {

    /**
     * 固化明细列表快照，防止调用方在 Feign 序列化或应用服务执行期间修改集合。
     */
    public CreateSupplierDeliveryCommand {
        lines = lines == null ? null : List.copyOf(lines);
    }
}
