package io.github.chrisshi.mom.outbox.application;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.metrics.MomMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * 基于 Inbox 唯一约束的消费幂等执行器。
 *
 * <p>执行器在同一个本地事务中先插入 {@code mom_inbox_event}，再执行消费者业务动作，最后标记处理完成。
 * 如果事件已经由同一消费者处理，唯一约束使 INSERT 不影响任何行，业务动作不会再次执行。如果业务动作抛出
 * 异常，Inbox INSERT 与业务写入一起回滚，RocketMQ 可以安全重试。</p>
 *
 * <p>执行结果记录为 {@code processed/duplicate/failed} 三种低基数指标，标签只包含稳定消费者名称；事件 ID、
 * 关联 ID、聚合 ID 和 Payload 不进入指标。指标异常不会改变事务提交、回滚或消息重试语义。</p>
 *
 * <p>该机制提供“至少一次传输 + 单消费者业务结果至多一次”的基础边界，但不能替代领域自身的条件更新、
 * 状态机和唯一约束。不同消费者名称拥有独立幂等空间。</p>
 */
public final class InboxDeduplicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(InboxDeduplicator.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * 创建不启用运行指标的 Inbox 幂等执行器。
     *
     * @param jdbcTemplate 当前消费者服务权威 DataSource 的 JDBC 模板
     * @param transactionTemplate 当前消费者服务本地事务模板
     */
    public InboxDeduplicator(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this(jdbcTemplate, transactionTemplate, null);
    }

    /**
     * 创建带 Micrometer 运行指标的 Inbox 幂等执行器。
     *
     * @param jdbcTemplate 当前消费者服务权威 DataSource 的 JDBC 模板
     * @param transactionTemplate 当前消费者服务本地事务模板
     * @param meterRegistry 可选 Micrometer 指标注册表；为空时关闭结果指标
     */
    public InboxDeduplicator(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
        this.transactionTemplate = Objects.requireNonNull(
                transactionTemplate,
                "transactionTemplate 不能为空");
        this.meterRegistry = meterRegistry;
    }

    /**
     * 对指定消费者仅执行一次事件业务动作。
     *
     * @param event 收到的完整事件信封
     * @param consumerName 稳定消费者名称；版本变化但幂等语义不变时应保持不变
     * @param action 与 Inbox INSERT 共用本地事务的业务写入
     * @return 首次处理并成功提交返回 {@code true}；重复事件返回 {@code false}
     * @throws RuntimeException 数据库或业务动作失败时透传，使 Binder 触发重试
     */
    public boolean executeOnce(
            EventEnvelope event,
            String consumerName,
            Runnable action) {
        Objects.requireNonNull(event, "event 不能为空");
        Objects.requireNonNull(action, "action 不能为空");
        String normalizedConsumer = requireText(consumerName, "consumerName");

        try {
            Boolean processed = transactionTemplate.execute(status -> {
                int inserted = jdbcTemplate.update("""
                                INSERT INTO mom_inbox_event (
                                    event_id,
                                    consumer_name,
                                    event_type,
                                    event_version,
                                    correlation_id,
                                    received_at,
                                    created_at
                                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                ON CONFLICT (event_id, consumer_name) DO NOTHING
                                """,
                        event.eventId(),
                        normalizedConsumer,
                        event.eventType(),
                        event.eventVersion(),
                        event.correlationId());
                if (inserted == 0) {
                    return false;
                }

                action.run();
                int completed = jdbcTemplate.update("""
                                UPDATE mom_inbox_event
                                SET processed_at = CURRENT_TIMESTAMP
                                WHERE event_id = ?
                                  AND consumer_name = ?
                                  AND processed_at IS NULL
                                """,
                        event.eventId(),
                        normalizedConsumer);
                if (completed != 1) {
                    throw new IllegalStateException("Inbox 处理完成状态未更新预期的一行记录");
                }
                return true;
            });
            boolean completed = Boolean.TRUE.equals(processed);
            recordProcessResult(normalizedConsumer, completed ? "processed" : "duplicate");
            return completed;
        }
        catch (RuntimeException exception) {
            recordProcessResult(normalizedConsumer, "failed");
            throw exception;
        }
    }

    /**
     * 记录 Inbox 结果，禁止指标异常反向影响消息事务。
     */
    private void recordProcessResult(String consumerName, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        try {
            meterRegistry.counter(
                            MomMetricNames.INBOX_PROCESS_RESULTS,
                            "consumer", consumerName,
                            "outcome", outcome)
                    .increment();
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Inbox 结果指标记录失败，消费事务结果保持不变。failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
