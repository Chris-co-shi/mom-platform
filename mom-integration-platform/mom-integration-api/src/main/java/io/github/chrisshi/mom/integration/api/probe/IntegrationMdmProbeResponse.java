package io.github.chrisshi.mom.integration.api.probe;

/**
 * Integration 调用 MDM 的端到端技术探针响应。
 *
 * <p>该契约把 MDM 返回值转换为 Integration 自有字段，没有直接暴露 MDM DTO，从而保持领域 API 边界。
 * 它只用于验证 Gateway、Nacos、OpenFeign、关联标识和分布式 Trace 传播，不代表 Integration Hub 的正式
 * 业务契约，也不暴露 OpenTelemetry SDK 类型。</p>
 *
 * @param service Integration 服务名称
 * @param status Integration 技术状态
 * @param correlationId Integration 当前请求关联标识
 * @param traceId Integration 当前 HTTP Server Span 所属 Trace 标识
 * @param spanId Integration 当前 HTTP Server Span 标识
 * @param mdmService 实际响应的 MDM 服务名称
 * @param mdmStatus MDM 技术状态
 * @param mdmCorrelationId MDM 收到的关联标识
 * @param mdmTraceId MDM 当前 HTTP Server Span 所属 Trace 标识
 * @param mdmSpanId MDM 当前 HTTP Server Span 标识
 */
public record IntegrationMdmProbeResponse(
        String service,
        String status,
        String correlationId,
        String traceId,
        String spanId,
        String mdmService,
        String mdmStatus,
        String mdmCorrelationId,
        String mdmTraceId,
        String mdmSpanId) {
}
