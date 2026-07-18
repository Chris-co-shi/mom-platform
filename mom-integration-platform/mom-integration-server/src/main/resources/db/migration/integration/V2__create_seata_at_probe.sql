-- P01-S06 Integration Seata AT 分支技术验证结构。
-- undo_log 结构基于 Apache Seata 2.5.0 PostgreSQL 官方脚本，并放入当前服务 Schema。

CREATE TABLE undo_log (
    id            BIGSERIAL    NOT NULL,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INTEGER      NOT NULL,
    log_created   TIMESTAMP(0) NOT NULL,
    log_modified  TIMESTAMP(0) NOT NULL,
    CONSTRAINT pk_integration_undo_log PRIMARY KEY (id),
    CONSTRAINT ux_integration_undo_log_xid_branch UNIQUE (xid, branch_id)
);

CREATE INDEX ix_integration_undo_log_created ON undo_log (log_created);

COMMENT ON TABLE undo_log IS 'Apache Seata AT 分支回滚日志；由 Seata DataSourceProxy 维护';
COMMENT ON COLUMN undo_log.branch_id IS 'Seata 分支事务 ID';
COMMENT ON COLUMN undo_log.xid IS 'Seata 全局事务 ID';
COMMENT ON COLUMN undo_log.rollback_info IS 'AT 前后镜像序列化数据';
COMMENT ON COLUMN undo_log.log_status IS '0=正常，1=防悬挂状态';

CREATE TABLE technical_seata_at_participant (
    transaction_key   VARCHAR(100) NOT NULL,
    participant_value VARCHAR(200) NOT NULL,
    xid               VARCHAR(128) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_technical_seata_at_participant PRIMARY KEY (transaction_key)
);

COMMENT ON TABLE technical_seata_at_participant IS 'P01-S06 Integration Seata AT 分支技术验证表，不属于正式集成领域模型';
COMMENT ON COLUMN technical_seata_at_participant.transaction_key IS '一次短全局事务的稳定技术键';
COMMENT ON COLUMN technical_seata_at_participant.xid IS '参与者观察到的 Seata XID，仅用于验收诊断';
