package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

/**
 * MDM Outbox 技术验证请求。
 *
 * @param probeKey 技术业务键
 * @param probeValue 技术业务值
 * @param poisonEvent 是否生成用于验证 RocketMQ 重试和死信的故障事件
 */
public record MdmOutboxProbeRequest(
        String probeKey,
        String probeValue,
        boolean poisonEvent) {
}
