-- 仅供 GitHub Actions 真实基础设施 Smoke 使用；正式 Flyway 不再创建这些技术表。
SET search_path TO :"schema";

CREATE TABLE IF NOT EXISTS technical_data_probe (
    id VARCHAR(19) PRIMARY KEY,
    probe_key VARCHAR(120) NOT NULL,
    probe_value VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_smoke_technical_data_probe_key UNIQUE (probe_key)
);

CREATE TABLE IF NOT EXISTS technical_seata_at_coordinator (
    transaction_key VARCHAR(100) PRIMARY KEY,
    coordinator_value VARCHAR(200) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
