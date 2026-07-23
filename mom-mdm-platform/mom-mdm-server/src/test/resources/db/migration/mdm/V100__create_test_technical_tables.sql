-- 仅供自动化测试使用的 MDM 技术表，不会进入打包产物或正式 Schema。
CREATE TABLE technical_data_probe (
    id VARCHAR(19) PRIMARY KEY,
    probe_key VARCHAR(120) NOT NULL,
    probe_value VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_test_technical_data_probe_key UNIQUE (probe_key)
);

CREATE TABLE technical_seata_at_coordinator (
    transaction_key VARCHAR(100) PRIMARY KEY,
    coordinator_value VARCHAR(200) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

