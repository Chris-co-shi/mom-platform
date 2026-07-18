package io.github.chrisshi.mom.integration.api.probe;

/**
 * Redis 幂等技术验证接口的响应契约。
 *
 * @param service 当前处理请求的服务名称
 * @param status 幂等占位状态
 * @param mayProceed 当前请求是否取得继续执行资格
 * @param correlationId 当前请求关联标识
 * @param ttlSeconds 幂等键 TTL 秒数
 * @param failureReason fail-open 绕过保护时的失败摘要；正常场景为空
 */
public record IdempotencyProbeResponse(
        String service,
        String status,
        boolean mayProceed,
        String correlationId,
        long ttlSeconds,
        String failureReason) {
}
