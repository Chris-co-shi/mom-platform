-- Integration 消费 Inbox。事件 ID 与消费者名称形成幂等键，业务处理失败时与业务写入一起回滚。
CREATE TABLE mom_inbox_event (
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_name),
    CONSTRAINT ck_mom_inbox_event_version CHECK (event_version > 0)
);

CREATE INDEX idx_mom_inbox_received_at
    ON mom_inbox_event (received_at);

-- P01-S05 技术消费结果，不属于正式 Integration 领域模型。
CREATE TABLE technical_message_receipt (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE mom_inbox_event IS 'Integration 消费幂等 Inbox；与消费者业务写入共享本地事务';
COMMENT ON COLUMN mom_inbox_event.processed_at IS '消费者业务动作成功完成时间；失败事务不会留下 Inbox 占位';
COMMENT ON TABLE technical_message_receipt IS 'P01-S05 RocketMQ 消费技术验收结果，非正式业务表';
