package io.github.chrisshi.mom.outbox.model;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;

import java.time.Instant;

/**
 * 发布器从数据库领取到的 Outbox 快照。
 *
 * <p>记录包含重建事件信封和执行 CAS 状态更新所需的全部字段。它不持有数据库连接、事务或行锁；领取事务
 * 提交后发布器才使用该快照调用 Broker，因此 RocketMQ 网络等待不会长期占用 Hikari 连接和 PostgreSQL 行锁。</p>
 *
 * @param eventId 事件及 Outbox 主键
 * @param eventType 事件类型
 * @param eventVersion 事件版本
 * @param aggregateType 聚合类型
 * @param aggregateId 聚合标识
 * @param occurredAt 领域事实发生时间
 * @param producer 事件生产服务
 * @param correlationId 关联标识
 * @param payloadJson 事件 JSON 负载
 * @param status 当前持久化状态
 * @param retryCount 已发生的失败次数
 * @param leaseOwner 当前租约所有者
 * @param leaseUntil 租约到期时间
 */
public record OutboxRecord(
        String eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String producer,
        String correlationId,
        String payloadJson,
        OutboxStatus status,
        int retryCount,
        String leaseOwner,
        Instant leaseUntil) {

    /**
     * 将持久化快照恢复为跨服务事件信封。
     *
     * @return 与首次写入 Outbox 时身份和内容完全一致的事件信封
     */
    public EventEnvelope toEnvelope() {
        return new EventEnvelope(
                eventId,
                eventType,
                eventVersion,
                aggregateType,
                aggregateId,
                occurredAt,
                producer,
                correlationId,
                payloadJson);
    }
}
