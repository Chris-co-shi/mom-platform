package io.github.chrisshi.mom.wms.api.delivery;

/**
 * WMS 创建或幂等返回供应商送货通知的结果。
 *
 * <p>响应中的技术 ID 始终使用字符串，避免 JavaScript 对 64 位整数舍入。{@code idempotentReplay=true}
 * 表示相同来源请求和相同业务负载已经成功提交，本次没有创建第二份送货通知、收货任务或 Outbox 事件。</p>
 *
 * @param deliveryId WMS 送货通知技术 ID
 * @param receivingTaskId 自动创建的收货任务技术 ID
 * @param deliveryNumber 送货单业务编号
 * @param deliveryStatus 当前送货通知状态
 * @param receivingTaskStatus 当前收货任务状态
 * @param idempotentReplay 是否为已提交结果的安全重放
 */
public record SupplierDeliveryCreationResponse(
        String deliveryId,
        String receivingTaskId,
        String deliveryNumber,
        String deliveryStatus,
        String receivingTaskStatus,
        boolean idempotentReplay) {
}
