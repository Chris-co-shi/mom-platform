package io.github.chrisshi.mom.integration.messaging;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import io.github.chrisshi.mom.tracing.CurrentTraceContext;
import io.github.chrisshi.mom.tracing.TraceContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;

import java.sql.Timestamp;
import java.util.function.Consumer;

/**
 * Integration 领域事件函数式消费者配置。
 *
 * <p>该配置通过 Spring Cloud Stream 函数模型声明 {@code momDomainEventConsumer}，由 RocketMQ Binder
 * 负责创建底层消费者和确认消息。配置默认关闭，只有同时设置
 * {@code mom.integration.message-consumer.enabled=true} 和
 * {@code spring.cloud.function.definition=momDomainEventConsumer} 时才参与消息消费，从而保证普通本地启动和
 * 既有 Nacos/Redis Smoke Test 不依赖 RocketMQ。</p>
 *
 * <p>Spring Cloud Stream 的 Imperative Function Observation 会从消息 Header 恢复发布方 Trace Context，并
 * 为当前消费尝试创建 Span。技术消费结果同时记录 Trace ID 与 Span ID，供真实 Broker CI 证明发布和消费属于
 * 同一短 Trace；重复投递可以产生新的消费尝试 Span，但 Inbox 仍只允许一次业务成功。</p>
 *
 * <p>配置不使用类级 {@code ConditionalOnBean} 判断 Inbox 基础设施，因为组件扫描发生在自动配置 Bean 注册
 * 之前，早期判断会把合法消费者错误跳过。启用消费者后，{@link InboxDeduplicator}、{@link JdbcTemplate} 或
 * {@link CurrentTraceContext} 缺失将导致应用启动失败；可靠消费者不能以“静默不绑定”的方式降级。</p>
 *
 * <p>正常事件由 {@link InboxDeduplicator} 在同一 PostgreSQL 本地事务中写入 Inbox 与技术消费结果。重复投递
 * 不会重复执行 INSERT。故障验证事件会在事务内主动抛出异常，使 Inbox 与业务结果一起回滚，并把异常交给
 * RocketMQ 执行重新消费和 DLQ 策略。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "mom.integration.message-consumer",
        name = "enabled",
        havingValue = "true")
public class IntegrationDomainEventConsumerConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            IntegrationDomainEventConsumerConfiguration.class);

    private static final String CONSUMER_NAME = "mom-integration-domain-event-v1";
    private static final String CREATED_EVENT_TYPE = "mdm.technical-probe.created";
    private static final String POISON_EVENT_TYPE = "mdm.technical-probe.poisoned";

    /**
     * 创建 Spring Cloud Stream 函数式事件消费者。
     *
     * <p>未知事件类型对当前技术消费者不产生业务动作并直接确认，因为共享 Topic 后不同消费者只处理自己关心
     * 的事件是正常行为。正式领域事件应通过独立 Binding、Topic 或 RocketMQ Tag 进一步收窄订阅范围，不能
     * 把“不关心”错误地当作消费失败。</p>
     *
     * @param inboxDeduplicator 当前服务 Inbox 幂等执行器
     * @param jdbcTemplate 当前服务唯一权威 DataSource 对应的 JDBC 模板
     * @param currentTraceContext 当前消费函数 Observation 的 Trace 上下文访问器
     * @return 接收完整事件信封的函数式 Consumer
     */
    @Bean
    Consumer<Message<EventEnvelope>> momDomainEventConsumer(
            InboxDeduplicator inboxDeduplicator,
            JdbcTemplate jdbcTemplate,
            CurrentTraceContext currentTraceContext) {
        return message -> {
            EventEnvelope event = message.getPayload();
            if (CREATED_EVENT_TYPE.equals(event.eventType())) {
                inboxDeduplicator.executeOnce(event, CONSUMER_NAME, () ->
                        insertTechnicalReceipt(
                                jdbcTemplate,
                                event,
                                currentTraceContext.snapshot()));
                return;
            }
            if (POISON_EVENT_TYPE.equals(event.eventType())) {
                inboxDeduplicator.executeOnce(event, CONSUMER_NAME, () -> {
                    throw new IllegalStateException("P01-S05 主动触发 RocketMQ 重试与死信");
                });
                return;
            }
            LOGGER.debug("忽略当前消费者不关心的事件。eventId={}, eventType={}",
                    event.eventId(), event.eventType());
        };
    }

    /**
     * 在 Inbox 事务中记录一次正常技术事件的消费结果及当前消费 Span。
     *
     * @param jdbcTemplate 当前事务绑定的 JDBC 模板
     * @param event 正在处理的事件信封
     * @param trace 当前消费函数 Observation 的 Trace 标识快照
     * @throws IllegalStateException 当前没有活动消费 Span，或 INSERT 未影响预期一行时抛出并回滚 Inbox
     */
    private static void insertTechnicalReceipt(
            JdbcTemplate jdbcTemplate,
            EventEnvelope event,
            TraceContextSnapshot trace) {
        if (!trace.isPresent()) {
            throw new IllegalStateException("消息消费者执行时没有活动 Trace Context");
        }
        int inserted = jdbcTemplate.update("""
                        INSERT INTO technical_message_receipt (
                            event_id,
                            event_type,
                            aggregate_id,
                            correlation_id,
                            payload_json,
                            received_at,
                            trace_id,
                            span_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                event.eventId(),
                event.eventType(),
                event.aggregateId(),
                event.correlationId(),
                event.payloadJson(),
                Timestamp.from(event.occurredAt()),
                trace.traceId(),
                trace.spanId());
        if (inserted != 1) {
            throw new IllegalStateException("技术消息消费结果未插入预期的一行记录");
        }
    }
}
