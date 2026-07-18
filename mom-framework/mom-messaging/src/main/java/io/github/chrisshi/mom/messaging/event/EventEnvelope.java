package io.github.chrisshi.mom.messaging.event;

import java.time.Instant;
import java.util.Objects;

/**
 * MOM 跨服务领域事件信封。
 *
 * <p>该类型属于 {@code mom-messaging} 公共契约，只描述跨服务消息所需的稳定元数据，不依赖 RocketMQ、
 * Spring Cloud Stream 或任何领域 Server。事件生产方负责在写入 Outbox 时一次性生成完整信封，发布器重试时
 * 必须复用同一个 {@code eventId} 和业务内容，禁止每次发送重新生成身份。</p>
 *
 * <p>{@code payloadJson} 保存事件版本对应的 JSON 文本。使用文本而不是 Java 原生序列化可以避免类路径耦合，
 * 并允许消费者按 {@code eventType + eventVersion} 选择自己的反序列化模型。信封不可变，可安全在线程间共享。</p>
 *
 * @param eventId 事件全局唯一标识；重复投递时保持不变
 * @param eventType 稳定事件类型，例如 {@code mdm.technical-probe.created}
 * @param eventVersion 事件契约版本，从 1 开始递增
 * @param aggregateType 产生事件的聚合类型
 * @param aggregateId 产生事件的聚合技术标识
 * @param occurredAt 领域事实发生时间，统一为 UTC 时间点
 * @param producer 生产事件的稳定服务名
 * @param correlationId 关联同步请求、工作流或批处理的标识
 * @param payloadJson 事件业务负载 JSON；不得包含密钥、Token 或未脱敏敏感数据
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String producer,
        String correlationId,
        String payloadJson) {

    /**
     * 校验事件信封的最小可发布约束。
     *
     * @throws IllegalArgumentException 任一必填文本为空或版本号小于 1 时抛出
     * @throws NullPointerException 发生时间为空时抛出
     */
    public EventEnvelope {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion 必须大于零");
        }
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
        producer = requireText(producer, "producer");
        correlationId = requireText(correlationId, "correlationId");
        payloadJson = requireText(payloadJson, "payloadJson");
    }

    /**
     * 校验并规范化信封文本字段。
     *
     * @param value 原始字段值
     * @param fieldName 用于异常消息的字段名
     * @return 去除首尾空白后的值
     * @throws IllegalArgumentException 值为空或仅包含空白时抛出
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
