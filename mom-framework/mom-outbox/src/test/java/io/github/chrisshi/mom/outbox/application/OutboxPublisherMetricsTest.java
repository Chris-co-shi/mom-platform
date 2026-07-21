package io.github.chrisshi.mom.outbox.application;

import io.github.chrisshi.mom.messaging.event.EventTransport;
import io.github.chrisshi.mom.metrics.MomMetricNames;
import io.github.chrisshi.mom.outbox.config.OutboxPublisherProperties;
import io.github.chrisshi.mom.outbox.model.OutboxRecord;
import io.github.chrisshi.mom.outbox.model.OutboxStatus;
import io.github.chrisshi.mom.outbox.persistence.JdbcOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OutboxPublisher} 运行指标契约测试。
 *
 * <p>测试使用模拟仓储和传输端口，只验证持久化状态机结果到低基数指标的映射；数据库租约、CAS 和真实 Broker
 * 行为继续由 PostgreSQL 集成测试与 Messaging CI 验证。事件 ID 不应成为 Meter 标签。</p>
 */
class OutboxPublisherMetricsTest {

    /**
     * Broker 接受且 SENT CAS 成功时只记录一次 sent。
     */
    @Test
    void shouldRecordSentAfterSuccessfulStateUpdate() {
        JdbcOutboxRepository repository = mock(JdbcOutboxRepository.class);
        EventTransport transport = mock(EventTransport.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxRecord record = record(0);
        when(repository.claimAvailable(anyString(), anyInt(), any())).thenReturn(List.of(record));
        when(transport.send(anyString(), any())).thenReturn(true);
        when(repository.markSent(record.eventId(), "test-owner")).thenReturn(true);

        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                transport,
                new OutboxPublisherProperties(),
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC),
                "test-owner",
                ObservationRegistry.NOOP,
                registry);

        assertEquals(1, publisher.publishAvailableBatch());
        assertEquals(1.0, registry.get(MomMetricNames.OUTBOX_PUBLISH_RESULTS)
                .tag("outcome", "sent")
                .counter()
                .count());
    }

    /**
     * Broker 发送失败且持久化为 RETRY 时记录 retry，并保持返回成功数为零。
     */
    @Test
    void shouldRecordRetryAfterTransportFailure() {
        JdbcOutboxRepository repository = mock(JdbcOutboxRepository.class);
        EventTransport transport = mock(EventTransport.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxRecord record = record(0);
        when(repository.claimAvailable(anyString(), anyInt(), any())).thenReturn(List.of(record));
        when(transport.send(anyString(), any())).thenThrow(new IllegalStateException("broker unavailable"));
        when(repository.markFailure(
                anyString(), anyString(), anyInt(), any(), any(), anyString()))
                .thenReturn(true);

        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                transport,
                new OutboxPublisherProperties(),
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC),
                "test-owner",
                ObservationRegistry.NOOP,
                registry);

        assertEquals(0, publisher.publishAvailableBatch());
        assertEquals(1.0, registry.get(MomMetricNames.OUTBOX_PUBLISH_RESULTS)
                .tag("outcome", "retry")
                .counter()
                .count());
    }

    /**
     * SENT 状态 CAS 冲突不得误记为成功。
     */
    @Test
    void shouldRecordCasConflictWhenSentStateUpdateLosesLease() {
        JdbcOutboxRepository repository = mock(JdbcOutboxRepository.class);
        EventTransport transport = mock(EventTransport.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxRecord record = record(0);
        when(repository.claimAvailable(anyString(), anyInt(), any())).thenReturn(List.of(record));
        when(transport.send(anyString(), any())).thenReturn(true);
        when(repository.markSent(record.eventId(), "test-owner")).thenReturn(false);

        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                transport,
                new OutboxPublisherProperties(),
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC),
                "test-owner",
                ObservationRegistry.NOOP,
                registry);

        assertEquals(0, publisher.publishAvailableBatch());
        assertEquals(1.0, registry.get(MomMetricNames.OUTBOX_PUBLISH_RESULTS)
                .tag("outcome", "cas_conflict")
                .counter()
                .count());
    }

    private static OutboxRecord record(int retryCount) {
        return new OutboxRecord(
                "0000000000000000001",
                "mdm.technical-probe.created",
                1,
                "technical-probe",
                "0000000000000000002",
                Instant.parse("2026-07-19T00:00:00Z"),
                "mom-mdm-server",
                "p01-s08-correlation",
                "{\"status\":\"created\"}",
                OutboxStatus.CLAIMED,
                retryCount,
                "test-owner",
                Instant.parse("2026-07-19T00:00:30Z"));
    }
}
