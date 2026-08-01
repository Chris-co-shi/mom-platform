-- S18 System Runtime 变更事件可靠发布与消费幂等基础表。
-- 两表与 System 业务表共用唯一 DataSource 和事务管理器，不建立物理外键。

CREATE TABLE mom_outbox_event (
    event_id VARCHAR(64) PRIMARY KEY,
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
    CONSTRAINT ck_system_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT ck_system_outbox_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_system_outbox_status CHECK (
        status IN ('PENDING', 'CLAIMED', 'RETRY', 'SENT', 'DEAD')),
    CONSTRAINT ck_system_outbox_lease_pair CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL))
);

CREATE INDEX idx_system_outbox_claim
    ON mom_outbox_event (status, next_attempt_at, lease_until, occurred_at, event_id);
CREATE INDEX idx_system_outbox_sent_at
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
    CONSTRAINT pk_system_inbox_event PRIMARY KEY (event_id, consumer_name),
    CONSTRAINT ck_system_inbox_event_version CHECK (event_version > 0)
);

CREATE INDEX idx_system_inbox_processed_at
    ON mom_inbox_event (processed_at, created_at);
