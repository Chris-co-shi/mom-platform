package io.github.chrisshi.mom.outbox.application;

import io.github.chrisshi.mom.messaging.event.EventTransport;
import io.github.chrisshi.mom.metrics.MomMetricNames;
import io.github.chrisshi.mom.outbox.config.OutboxPublisherProperties;
import io.github.chrisshi.mom.outbox.model.OutboxRecord;
import io.github.chrisshi.mom.outbox.model.OutboxStatus;
import io.github.chrisshi.mom.outbox.persistence.JdbcOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 租约式 Outbox 发布器。
 *
 * <p>每轮先在短事务中领取记录并提交租约，随后在没有数据库事务和行锁的状态下调用 {@link EventTransport}。
 * 发送结果再通过独立短事务执行 CAS 更新。多个应用实例可以同时运行；{@code SKIP LOCKED} 分散领取，租约防止
 * 实例崩溃后记录永久卡住，租约所有者条件防止旧实例覆盖新实例结果。</p>
 *
 * <p>每条领取记录创建一个短 {@code mom.outbox.publish} Observation。消息传输层可以把当前 Trace Context
 * 写入 Broker Header，使消费者形成子 Span。该 Trace 只覆盖一次发布尝试，不延长原始 HTTP 请求；事件之间
 * 继续使用 {@code eventId} 与 {@code correlationId} 关联。业务 ID 仅作为高基数 Span 属性，不作为指标标签。</p>
 *
 * <p>发布结果额外记录 {@code sent/retry/dead/cas_conflict} 四种低基数指标，不使用事件 ID、关联 ID 或负载作为
 * 标签。指标注册或写入失败只记录警告，不得改变 Outbox 的持久化状态、重试和至少一次传输语义。</p>
 *
 * <p>发布失败使用有上限的指数退避，达到最大次数后进入 DEAD。发布器只记录异常类型和摘要，禁止把事件负载
 * 写入错误日志或 {@code last_error}。数据库、Broker 或 Trace Exporter 不可用时都不会把未确认发送标记为
 * SENT；可观测性自身使用 NOOP Registry 时不得阻断发布。</p>
 */
public final class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcOutboxRepository repository;
    private final EventTransport transport;
    private final OutboxPublisherProperties properties;
    private final Clock clock;
    private final String leaseOwner;
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    /**
     * 创建不启用 Trace 和运行指标的 Outbox 发布器。
     *
     * <p>该重载保留给纯单元测试和不启用 Observability 的嵌入场景；正式应用自动配置会注入真实注册表。</p>
     *
     * @param repository Outbox JDBC 仓储
     * @param transport 消息传输端口
     * @param properties 发布和重试参数
     * @param clock UTC 时钟
     * @param leaseOwner 当前应用实例唯一租约标识
     */
    public OutboxPublisher(
            JdbcOutboxRepository repository,
            EventTransport transport,
            OutboxPublisherProperties properties,
            Clock clock,
            String leaseOwner) {
        this(repository, transport, properties, clock, leaseOwner, ObservationRegistry.NOOP, null);
    }

    /**
     * 创建带 Micrometer Observation、但不启用运行指标的 Outbox 发布器。
     *
     * @param repository Outbox JDBC 仓储
     * @param transport 消息传输端口
     * @param properties 发布和重试参数
     * @param clock UTC 时钟
     * @param leaseOwner 当前应用实例唯一租约标识
     * @param observationRegistry Micrometer Observation 注册表；不得为空
     */
    public OutboxPublisher(
            JdbcOutboxRepository repository,
            EventTransport transport,
            OutboxPublisherProperties properties,
            Clock clock,
            String leaseOwner,
            ObservationRegistry observationRegistry) {
        this(repository, transport, properties, clock, leaseOwner, observationRegistry, null);
    }

    /**
     * 创建带 Trace 和 Prometheus 运行指标的 Outbox 发布器。
     *
     * @param repository Outbox JDBC 仓储
     * @param transport 消息传输端口
     * @param properties 发布和重试参数
     * @param clock UTC 时钟
     * @param leaseOwner 当前应用实例唯一租约标识
     * @param observationRegistry Micrometer Observation 注册表；不得为空
     * @param meterRegistry 可选 Micrometer 指标注册表；为空时关闭结果指标
     */
    public OutboxPublisher(
            JdbcOutboxRepository repository,
            EventTransport transport,
            OutboxPublisherProperties properties,
            Clock clock,
            String leaseOwner,
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.transport = Objects.requireNonNull(transport, "transport 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.observationRegistry = Objects.requireNonNull(
                observationRegistry,
                "observationRegistry 不能为空");
        this.meterRegistry = meterRegistry;
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner 不能为空");
        }
        this.leaseOwner = leaseOwner.trim();
        properties.validate();
    }

    /**
     * 按固定间隔触发一轮领取与发送。
     *
     * <p>Spring 调度线程中的异常会被捕获并记录，避免一次数据库故障永久停止后续调度。单条事件失败不会阻断
     * 同批其他事件；每条记录独立更新 RETRY 或 DEAD。</p>
     */
    @Scheduled(fixedDelayString = "${mom.outbox.publisher.fixed-delay-millis:1000}")
    public void publishScheduledBatch() {
        try {
            publishAvailableBatch();
        }
        catch (RuntimeException exception) {
            LOGGER.error("Outbox 发布批次失败；本轮停止，后续调度将重试。failureType={}",
                    exception.getClass().getSimpleName(), exception);
        }
    }

    /**
     * 立即执行一轮发布，主要供集成测试、运维命令和调度入口复用。
     *
     * @return 本轮成功标记 SENT 的事件数量
     */
    public int publishAvailableBatch() {
        List<OutboxRecord> claimed = repository.claimAvailable(
                leaseOwner,
                properties.getBatchSize(),
                properties.getLeaseDuration());
        int sentCount = 0;
        for (OutboxRecord record : claimed) {
            if (publishOne(record)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    private boolean publishOne(OutboxRecord record) {
        Observation observation = Observation.createNotStarted(
                        "mom.outbox.publish",
                        observationRegistry)
                .lowCardinalityKeyValue("messaging.system", "rocketmq")
                .lowCardinalityKeyValue("event.type", record.eventType())
                .lowCardinalityKeyValue("outbox.attempt", Integer.toString(record.retryCount() + 1))
                .highCardinalityKeyValue("event.id", record.eventId())
                .highCardinalityKeyValue("correlation.id", record.correlationId())
                .start();
        try (Observation.Scope ignored = observation.openScope()) {
            boolean accepted = transport.send(
                    properties.getBindingName(),
                    record.toEnvelope());
            if (!accepted) {
                IllegalStateException exception = new IllegalStateException("消息 Binding 未接受事件");
                observation.error(exception);
                recordPublishResult(markFailure(record, exception));
                return false;
            }
            boolean updated = repository.markSent(record.eventId(), leaseOwner);
            if (!updated) {
                LOGGER.warn("Outbox 已发送但 SENT 状态 CAS 失败，将依靠消费者幂等承受潜在重复。eventId={}",
                        record.eventId());
                recordPublishResult("cas_conflict");
                return false;
            }
            recordPublishResult("sent");
            return true;
        }
        catch (RuntimeException exception) {
            observation.error(exception);
            recordPublishResult(markFailure(record, exception));
            return false;
        }
        finally {
            observation.stop();
        }
    }

    private String markFailure(OutboxRecord record, RuntimeException exception) {
        int nextRetryCount = record.retryCount() + 1;
        OutboxStatus nextStatus = nextRetryCount >= properties.getMaxAttempts()
                ? OutboxStatus.DEAD
                : OutboxStatus.RETRY;
        Instant nextAttemptAt = clock.instant().plus(calculateBackoff(nextRetryCount));
        String errorSummary = exception.getClass().getSimpleName()
                + ": "
                + (exception.getMessage() == null ? "no message" : exception.getMessage());

        boolean updated = repository.markFailure(
                record.eventId(),
                leaseOwner,
                nextRetryCount,
                nextStatus,
                nextAttemptAt,
                errorSummary);
        if (!updated) {
            LOGGER.warn("Outbox 发布失败但状态 CAS 未更新，可能已经由新租约接管。eventId={}, failureType={}",
                    record.eventId(), exception.getClass().getSimpleName());
            return "cas_conflict";
        }
        if (nextStatus == OutboxStatus.DEAD) {
            LOGGER.error("Outbox 事件达到最大尝试次数并进入 DEAD。eventId={}, retryCount={}, failureType={}",
                    record.eventId(), nextRetryCount, exception.getClass().getSimpleName());
            return "dead";
        }
        LOGGER.warn("Outbox 事件发布失败并进入退避重试。eventId={}, retryCount={}, failureType={}",
                record.eventId(), nextRetryCount, exception.getClass().getSimpleName());
        return "retry";
    }

    /**
     * 记录单条发布最终结果，禁止指标异常反向影响消息状态机。
     */
    private void recordPublishResult(String outcome) {
        if (meterRegistry == null) {
            return;
        }
        try {
            meterRegistry.counter(
                            MomMetricNames.OUTBOX_PUBLISH_RESULTS,
                            "outcome", outcome)
                    .increment();
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Outbox 结果指标记录失败，消息状态保持不变。failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 计算有上限的指数退避，第一次失败使用 initialBackoff，之后按二倍增长。
     *
     * @param retryCount 下一次持久化的重试次数
     * @return 不超过最大退避时间的等待时长
     */
    private Duration calculateBackoff(int retryCount) {
        int exponent = Math.min(Math.max(retryCount - 1, 0), 30);
        long multiplier = 1L << exponent;
        Duration candidate;
        try {
            candidate = properties.getInitialBackoff().multipliedBy(multiplier);
        }
        catch (ArithmeticException overflow) {
            return properties.getMaxBackoff();
        }
        return candidate.compareTo(properties.getMaxBackoff()) > 0
                ? properties.getMaxBackoff()
                : candidate;
    }
}
