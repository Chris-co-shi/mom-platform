-- WMS 自有编码规则与期初单正式流水；三类写入共用 WMS 本地事务。
CREATE TABLE wms_code_rule (
    id varchar(19) NOT NULL,
    rule_code varchar(64) NOT NULL,
    usage_scenario varchar(120),
    description varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_wms_code_rule PRIMARY KEY (id),
    CONSTRAINT uk_wms_code_rule_code UNIQUE (rule_code),
    CONSTRAINT ck_wms_code_rule_version CHECK (version >= 0),
    CONSTRAINT ck_wms_code_rule_usage_scenario CHECK (usage_scenario IS NULL OR length(btrim(usage_scenario)) > 0)
);
CREATE TABLE wms_code_rule_revision (
    id varchar(19) NOT NULL,
    rule_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    status varchar(16) NOT NULL,
    definition jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_wms_code_rule_revision PRIMARY KEY (id),
    CONSTRAINT uk_wms_code_rule_revision_no UNIQUE (rule_id, revision_no),
    CONSTRAINT ck_wms_code_rule_revision_no CHECK (revision_no >= 1),
    CONSTRAINT ck_wms_code_rule_revision_status CHECK (status IN ('DRAFT','ENABLED','DISABLED')),
    CONSTRAINT ck_wms_code_rule_revision_definition CHECK (jsonb_typeof(definition) = 'object' AND definition ->> 'schemaVersion' = '1' AND jsonb_typeof(definition -> 'segments') = 'array')
);
CREATE UNIQUE INDEX uk_wms_code_rule_one_draft ON wms_code_rule_revision(rule_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX uk_wms_code_rule_one_enabled ON wms_code_rule_revision(rule_id) WHERE status = 'ENABLED';
CREATE TABLE wms_code_rule_sequence (
    rule_id varchar(19) NOT NULL,
    last_value bigint NOT NULL,
    CONSTRAINT pk_wms_code_rule_sequence PRIMARY KEY (rule_id),
    CONSTRAINT ck_wms_code_rule_sequence_positive CHECK (last_value > 0)
);
COMMENT ON TABLE wms_code_rule IS 'WMS 编码规则身份；规则行是发布与正式发号的并发串行化边界';
COMMENT ON TABLE wms_code_rule_revision IS 'WMS 编码规则不可变发布修订；草稿可编辑';
COMMENT ON TABLE wms_code_rule_sequence IS 'WMS 规则全局递增事务型流水；不按日期或修订重置';
COMMENT ON COLUMN wms_code_rule_sequence.rule_id IS '规则身份 ID；不建立业务外键';
COMMENT ON COLUMN wms_code_rule_sequence.last_value IS '已成功提交的最大流水号；回滚时不消耗';

-- 现有 OB- 技术 ID 变为可通过 System 修订的正式规则，迁移后即可创建期初单。
INSERT INTO wms_code_rule
    (id,rule_code,usage_scenario,description,version,created_at,created_by,updated_at,updated_by)
VALUES ('8300000000000000001','wms.opening.document','期初库存建账单据编号','期初建账草稿创建时正式分配',0,now(),'WMS_MIGRATION',now(),'WMS_MIGRATION');
INSERT INTO wms_code_rule_revision
    (id,rule_id,revision_no,status,definition,created_at,created_by,updated_at,updated_by)
VALUES ('8300000000000000002','8300000000000000001',1,'ENABLED',
        '{"schemaVersion":1,"segments":[{"id":"prefix","type":"LITERAL","params":{"value":"OB-"}},{"id":"serial","type":"SEQUENCE","params":{"width":8,"step":1}}]}'::jsonb,
        now(),'WMS_MIGRATION',now(),'WMS_MIGRATION');
