-- MDM 自有 Transactional Outbox。业务写入与本表 INSERT 必须使用同一 DataSource 和本地事务。
CREATE TABLE mom_outbox_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    producer VARCHAR(120) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(1000),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mom_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT ck_mom_outbox_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_mom_outbox_status CHECK (
        status IN ('PENDING', 'CLAIMED', 'RETRY', 'SENT', 'DEAD')
    )
);

CREATE INDEX idx_mom_outbox_publishable
    ON mom_outbox_event (next_attempt_at, occurred_at, event_id)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX idx_mom_outbox_lease
    ON mom_outbox_event (lease_until)
    WHERE status = 'CLAIMED';

COMMENT ON TABLE mom_outbox_event IS 'MDM 事务消息 Outbox；与领域写入同事务，发布器通过租约异步发送';
COMMENT ON COLUMN mom_outbox_event.event_id IS '事件全局唯一标识；重复投递必须复用同一值';
COMMENT ON COLUMN mom_outbox_event.payload_json IS '版本化事件 JSON，不允许保存密钥、Token 或未脱敏敏感数据';
COMMENT ON COLUMN mom_outbox_event.status IS 'PENDING/CLAIMED/RETRY/SENT/DEAD 持久化状态机';
