package io.github.chrisshi.mom.mdm.api.probe;

/**
 * MDM 服务发现与调用契约的最小技术探针响应。
 *
 * <p>该记录类型位于 {@code mom-mdm-api}，只表达跨服务传输契约，不依赖 Controller、数据库或 MDM
 * Server 实现。它用于 Phase 01 验证 Nacos、OpenFeign 和关联标识传播，不代表最终主数据业务模型。</p>
 *
 * @param service 实际处理请求的服务名称
 * @param status 技术探针状态
 * @param correlationId MDM 收到并返回的关联标识
 */
public record MdmServiceProbeResponse(
        String service,
        String status,
        String correlationId) {
}
