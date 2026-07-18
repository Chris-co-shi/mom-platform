package io.github.chrisshi.mom.outbox.persistence;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.model.OutboxRecord;
import io.github.chrisshi.mom.outbox.model.OutboxStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL Outbox JDBC 仓储。
 *
 * <p>仓储固定操作当前服务 Schema 中的 {@code mom_outbox_event} 表，不允许指定任意表名或跨 Schema 访问。
 * 多实例发布器使用 {@code FOR UPDATE SKIP LOCKED} 领取不同记录，并立即写入租约后提交事务。后续 SENT、RETRY
 * 或 DEAD 更新同时校验状态和租约所有者，旧实例在租约丢失后不能覆盖新实例结果。</p>
 *
 * <p>该仓储不调用消息中间件。所有数据库方法都使用短事务；网络发送由上层在事务外完成。数据库不可用时
 * 异常直接向上传播，发布器停止本轮处理，不会伪造成功。</p>
 */
public final class JdbcOutboxRepository {

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT event_id
                FROM mom_outbox_event
                WHERE status IN ('PENDING', 'RETRY')
                  AND next_attempt_at <= CURRENT_TIMESTAMP
                  AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                ORDER BY occurred_at, event_id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE mom_outbox_event AS event
            SET status = 'CLAIMED',
                lease_owner = ?,
                lease_until = CURRENT_TIMESTAMP + CAST(? AS bigint) * INTERVAL '1 millisecond',
                updated_at = CURRENT_TIMESTAMP
            FROM candidates
            WHERE event.event_id = candidates.event_id
            RETURNING event.event_id,
                      event.event_type,
                      event.event_version,
                      event.aggregate_type,
                      event.aggregate_id,
                      event.occurred_at,
                      event.producer,
                      event.correlation_id,
                      event.payload_json,
                      event.status,
                      event.retry_count,
                      event.lease_owner,
                      event.lease_until
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建 Outbox JDBC 仓储。
     *
     * @param jdbcTemplate 当前服务唯一权威 DataSource 对应的 JDBC 模板
     * @param transactionTemplate 当前服务本地事务模板
     */
    public JdbcOutboxRepository(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
        this.transactionTemplate = Objects.requireNonNull(
                transactionTemplate,
                "transactionTemplate 不能为空");
    }

    /**
     * 把事件信封插入当前业务事务中的 Outbox。
     *
     * <p>调用方必须已经开启本地事务，使领域写入和本次 INSERT 同时提交或回滚。事件 ID 是主键，同一事件
     * 重复追加会触发数据库唯一约束，而不是创建两个不同身份的消息。</p>
     *
     * @param event 完整事件信封
     * @return 成功插入的记录数，正常为 1
     */
    public int append(EventEnvelope event) {
        Objects.requireNonNull(event, "event 不能为空");
        return jdbcTemplate.update("""
                        INSERT INTO mom_outbox_event (
                            event_id,
                            event_type,
                            event_version,
                            aggregate_type,
                            aggregate_id,
                            occurred_at,
                            producer,
                            correlation_id,
                            payload_json,
                            status,
                            retry_count,
                            next_attempt_at,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP,
                                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateType(),
                event.aggregateId(),
                Timestamp.from(event.occurredAt()),
                event.producer(),
                event.correlationId(),
                event.payloadJson());
    }

    /**
     * 原子领取一批可发布事件并写入实例租约。
     *
     * @param leaseOwner 发布实例唯一标识
     * @param batchSize 本轮最大领取数量
     * @param leaseDuration 租约有效期
     * @return 已领取的不可变记录快照
     */
    public List<OutboxRecord> claimAvailable(
            String leaseOwner,
            int batchSize,
            Duration leaseDuration) {
        String normalizedOwner = requireText(leaseOwner, "leaseOwner");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize 必须大于零");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration 必须为正数");
        }

        List<OutboxRecord> claimed = transactionTemplate.execute(status -> jdbcTemplate.query(
                CLAIM_SQL,
                JdbcOutboxRepository::mapRecord,
                batchSize,
                normalizedOwner,
                leaseDuration.toMillis()));
        return claimed == null ? List.of() : List.copyOf(claimed);
    }

    /**
     * 在 Broker 同步发送成功后把记录标记为 SENT。
     *
     * @param eventId 事件标识
     * @param leaseOwner 当前发布实例租约标识
     * @return CAS 更新成功返回 {@code true}；租约丢失或状态变化返回 {@code false}
     */
    public boolean markSent(String eventId, String leaseOwner) {
        Integer changed = transactionTemplate.execute(status -> jdbcTemplate.update("""
                        UPDATE mom_outbox_event
                        SET status = 'SENT',
                            sent_at = CURRENT_TIMESTAMP,
                            lease_owner = NULL,
                            lease_until = NULL,
                            last_error = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE event_id = ?
                          AND status = 'CLAIMED'
                          AND lease_owner = ?
                        """,
                requireText(eventId, "eventId"),
                requireText(leaseOwner, "leaseOwner")));
        return changed != null && changed == 1;
    }

    /**
     * 记录一次发布失败，并按调用方决策进入 RETRY 或 DEAD。
     *
     * @param eventId 事件标识
     * @param leaseOwner 当前租约所有者
     * @param retryCount 新的累计失败次数
     * @param nextStatus 只能是 RETRY 或 DEAD
     * @param nextAttemptAt 下次允许领取时间；DEAD 时仍保存当前时间用于审计
     * @param errorSummary 不含敏感载荷的错误摘要
     * @return CAS 更新成功返回 {@code true}
     */
    public boolean markFailure(
            String eventId,
            String leaseOwner,
            int retryCount,
            OutboxStatus nextStatus,
            Instant nextAttemptAt,
            String errorSummary) {
        if (retryCount < 1) {
            throw new IllegalArgumentException("retryCount 必须大于零");
        }
        if (nextStatus != OutboxStatus.RETRY && nextStatus != OutboxStatus.DEAD) {
            throw new IllegalArgumentException("失败状态只能是 RETRY 或 DEAD");
        }
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt 不能为空");

        Integer changed = transactionTemplate.execute(status -> jdbcTemplate.update("""
                        UPDATE mom_outbox_event
                        SET status = ?,
                            retry_count = ?,
                            next_attempt_at = ?,
                            lease_owner = NULL,
                            lease_until = NULL,
                            last_error = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE event_id = ?
                          AND status = 'CLAIMED'
                          AND lease_owner = ?
                        """,
                nextStatus.name(),
                retryCount,
                Timestamp.from(nextAttemptAt),
                abbreviate(errorSummary, 1000),
                requireText(eventId, "eventId"),
                requireText(leaseOwner, "leaseOwner")));
        return changed != null && changed == 1;
    }

    /**
     * 查询指定事件当前 Outbox 状态，供技术验收和运维诊断使用。
     *
     * @param eventId 事件标识
     * @return 存在时返回记录快照
     */
    public Optional<OutboxRecord> findByEventId(String eventId) {
        List<OutboxRecord> records = jdbcTemplate.query("""
                        SELECT event_id,
                               event_type,
                               event_version,
                               aggregate_type,
                               aggregate_id,
                               occurred_at,
                               producer,
                               correlation_id,
                               payload_json,
                               status,
                               retry_count,
                               lease_owner,
                               lease_until
                        FROM mom_outbox_event
                        WHERE event_id = ?
                        """,
                JdbcOutboxRepository::mapRecord,
                requireText(eventId, "eventId"));
        return records.stream().findFirst();
    }

    private static OutboxRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp leaseUntil = resultSet.getTimestamp("lease_until");
        return new OutboxRecord(
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getInt("event_version"),
                resultSet.getString("aggregate_type"),
                resultSet.getString("aggregate_id"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getString("producer"),
                resultSet.getString("correlation_id"),
                resultSet.getString("payload_json"),
                OutboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("retry_count"),
                resultSet.getString("lease_owner"),
                leaseUntil == null ? null : leaseUntil.toInstant());
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "unknown publish failure";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
