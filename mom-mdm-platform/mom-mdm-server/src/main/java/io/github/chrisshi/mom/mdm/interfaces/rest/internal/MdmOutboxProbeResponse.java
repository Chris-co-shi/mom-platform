package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.mdm.application.MdmOutboxProbeService;

/**
 * MDM Outbox 技术验证响应。
 *
 * @param probeId 同事务写入的业务技术记录 ID
 * @param eventId Outbox 与消息系统复用的事件唯一标识
 * @param eventType 事件契约类型
 */
public record MdmOutboxProbeResponse(
        String probeId,
        String eventId,
        String eventType) {

    /**
     * 将事务服务结果转换为内部 HTTP 响应。
     *
     * @param result MDM Outbox 技术验证结果
     * @return 对应 HTTP 响应对象
     */
    public static MdmOutboxProbeResponse from(
            MdmOutboxProbeService.MdmOutboxProbeResult result) {
        return new MdmOutboxProbeResponse(
                result.probeId(),
                result.eventId(),
                result.eventType());
    }
}
