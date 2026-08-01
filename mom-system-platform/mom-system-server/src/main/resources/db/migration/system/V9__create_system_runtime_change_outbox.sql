-- S18 System Runtime 变更事件可靠发布与消费幂等基础表。
-- 两表与 System 业务表共用唯一 DataSource 和事务管理器，不建立物理外键。

CREATE TABLE mom_outbox_event (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    producer VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mom_outbox_event PRIMARY KEY (event_id),
    CONSTRAINT ck_mom_outbox_event_version_positive CHECK (event_version > 0),
    CONSTRAINT ck_mom_outbox_event_retry_count_non_negative CHECK (retry_count >= 0),
    CONSTRAINT ck_mom_outbox_event_status_allowed CHECK (
        status IN ('PENDING', 'CLAIMED', 'RETRY', 'SENT', 'DEAD'))
);

COMMENT ON TABLE mom_outbox_event IS 'System 运行时变更事件可靠发布 Outbox';
COMMENT ON COLUMN mom_outbox_event.event_id IS '事件全局唯一标识，重试时保持不变';
COMMENT ON COLUMN mom_outbox_event.aggregate_id IS '产生事件的聚合技术标识';
COMMENT ON COLUMN mom_outbox_event.correlation_id IS '端到端关联标识';
COMMENT ON COLUMN mom_outbox_event.status IS 'PENDING/CLAIMED/RETRY/SENT/DEAD 发布状态';
COMMENT ON COLUMN mom_outbox_event.payload_json IS '版本化且不含 Secret 的事件 JSON 负载';

CREATE INDEX ix_mom_outbox_event_claim
    ON mom_outbox_event (status, next_attempt_at, lease_until, occurred_at, event_id);
CREATE INDEX ix_mom_outbox_event_sent_at
    ON mom_outbox_event (sent_at)
    WHERE sent_at IS NOT NULL;

CREATE TABLE mom_inbox_event (
    event_id VARCHAR(64) NOT NULL,
    consumer_name VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_mom_inbox_event PRIMARY KEY (event_id, consumer_name),
    CONSTRAINT ck_mom_inbox_event_version_positive CHECK (event_version > 0)
);

COMMENT ON TABLE mom_inbox_event IS 'System 运行时变更事件消费幂等 Inbox';
COMMENT ON COLUMN mom_inbox_event.event_id IS '生产方事件全局唯一标识';
COMMENT ON COLUMN mom_inbox_event.consumer_name IS '稳定消费者名称和幂等空间';
COMMENT ON COLUMN mom_inbox_event.correlation_id IS '端到端关联标识';
COMMENT ON COLUMN mom_inbox_event.processed_at IS '消费者业务动作成功完成时间';

CREATE INDEX ix_mom_inbox_event_processed_at
    ON mom_inbox_event (processed_at, created_at);
