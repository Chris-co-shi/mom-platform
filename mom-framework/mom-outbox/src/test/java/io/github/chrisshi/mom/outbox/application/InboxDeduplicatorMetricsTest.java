package io.github.chrisshi.mom.outbox.application;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.metrics.MomMetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link InboxDeduplicator} 运行指标契约测试。
 *
 * <p>测试隔离事务执行器，只验证事务最终结果映射为 processed、duplicate、failed 三种计数。Inbox 唯一约束、
 * 业务写入同事务和异常回滚仍由真实 PostgreSQL 集成测试验证。</p>
 */
class InboxDeduplicatorMetricsTest {

    /**
     * 首次提交和重复消费分别记录 processed 与 duplicate。
     */
    @Test
    void shouldRecordProcessedAndDuplicateResults() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(transactionTemplate.execute(any())).thenReturn(true, false);
        InboxDeduplicator deduplicator = new InboxDeduplicator(
                jdbcTemplate,
                transactionTemplate,
                registry);

        assertTrue(deduplicator.executeOnce(event(), "mom-integration-domain-event-v1", () -> { }));
        assertFalse(deduplicator.executeOnce(event(), "mom-integration-domain-event-v1", () -> { }));

        assertEquals(1.0, registry.get(MomMetricNames.INBOX_PROCESS_RESULTS)
                .tags("consumer", "mom-integration-domain-event-v1", "outcome", "processed")
                .counter()
                .count());
        assertEquals(1.0, registry.get(MomMetricNames.INBOX_PROCESS_RESULTS)
                .tags("consumer", "mom-integration-domain-event-v1", "outcome", "duplicate")
                .counter()
                .count());
    }

    /**
     * 事务异常记录 failed 后必须原样抛出，不能因为指标处理吞掉消息失败。
     */
    @Test
    void shouldRecordFailedAndRethrowTransactionFailure() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(transactionTemplate.execute(any())).thenThrow(new IllegalStateException("transaction failed"));
        InboxDeduplicator deduplicator = new InboxDeduplicator(
                jdbcTemplate,
                transactionTemplate,
                registry);

        assertThrows(IllegalStateException.class, () -> deduplicator.executeOnce(
                event(),
                "mom-integration-domain-event-v1",
                () -> { }));
        assertEquals(1.0, registry.get(MomMetricNames.INBOX_PROCESS_RESULTS)
                .tags("consumer", "mom-integration-domain-event-v1", "outcome", "failed")
                .counter()
                .count());
    }

    private static EventEnvelope event() {
        return new EventEnvelope(
                "0000000000000000001",
                "mdm.technical-probe.created",
                1,
                "technical-probe",
                "0000000000000000002",
                Instant.parse("2026-07-19T00:00:00Z"),
                "mom-mdm-server",
                "p01-s08-correlation",
                "{\"status\":\"created\"}");
    }
}
