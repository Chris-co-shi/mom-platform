-- MDM 是编码规则的唯一持久化 Owner；规则身份行作为同 ruleCode 的并发串行化边界。
CREATE TABLE mdm_code_rule (
    id varchar(19) NOT NULL,
    rule_code varchar(64) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_code_rule PRIMARY KEY (id),
    CONSTRAINT uk_mdm_code_rule_code UNIQUE (rule_code),
    CONSTRAINT ck_mdm_code_rule_version CHECK (version >= 0)
);

CREATE TABLE mdm_code_rule_revision (
    id varchar(19) NOT NULL,
    rule_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    status varchar(16) NOT NULL,
    definition jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_code_rule_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_code_rule_revision_no UNIQUE (rule_id, revision_no),
    CONSTRAINT ck_mdm_code_rule_revision_no CHECK (revision_no >= 1),
    CONSTRAINT ck_mdm_code_rule_revision_status CHECK (status IN ('DRAFT','ENABLED','DISABLED')),
    CONSTRAINT ck_mdm_code_rule_revision_definition CHECK (jsonb_typeof(definition) = 'object' AND definition ->> 'schemaVersion' = '1' AND jsonb_typeof(definition -> 'segments') = 'array')
);

-- 两个部分唯一索引同时服务同规则状态查询；V1 不增加推测性组合索引。
CREATE UNIQUE INDEX uk_mdm_code_rule_one_draft
    ON mdm_code_rule_revision (rule_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX uk_mdm_code_rule_one_enabled
    ON mdm_code_rule_revision (rule_id) WHERE status = 'ENABLED';

COMMENT ON TABLE mdm_code_rule IS 'MDM 编码规则身份；version 是整个规则族的写入 CAS 令牌';
COMMENT ON TABLE mdm_code_rule_revision IS '编码规则不可变发布修订；仅 DRAFT 的 definition 可以更新';
COMMENT ON COLUMN mdm_code_rule.id IS '当前 Owner 内规则身份的 varchar(19) 技术主键';
COMMENT ON COLUMN mdm_code_rule.rule_code IS '当前 Owner 内唯一、不可改名的规则编码';
COMMENT ON COLUMN mdm_code_rule.version IS '同 ruleCode 所有写入共用的规则级并发版本';
COMMENT ON COLUMN mdm_code_rule_revision.id IS '规则修订的 varchar(19) 技术主键';
COMMENT ON COLUMN mdm_code_rule_revision.rule_id IS '所属规则身份 ID，由 Application 保障存在性';
COMMENT ON COLUMN mdm_code_rule_revision.status IS 'DRAFT、ENABLED 或 DISABLED；发布后 definition 不再修改';
COMMENT ON COLUMN mdm_code_rule_revision.definition IS '与 CodeRuleDefinition V1 一致的 JSONB，保持 segment 数组顺序及参数';
