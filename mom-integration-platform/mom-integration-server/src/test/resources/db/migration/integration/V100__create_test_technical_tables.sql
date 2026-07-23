-- 仅供自动化测试使用的 Integration 技术表，不会进入打包产物或正式 Schema。
CREATE TABLE technical_message_receipt (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(32),
    span_id VARCHAR(16)
);

CREATE TABLE technical_seata_at_participant (
    transaction_key VARCHAR(100) PRIMARY KEY,
    participant_value VARCHAR(200) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

