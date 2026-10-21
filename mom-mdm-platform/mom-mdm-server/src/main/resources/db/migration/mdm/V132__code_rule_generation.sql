-- 当前 Owner 内按规则身份及 UTC 周期维护正式流水；不按修订重置。
CREATE TABLE mdm_code_rule_sequence (
    rule_id varchar(19) NOT NULL,
    scope_key varchar(8) NOT NULL DEFAULT '',
    last_value bigint NOT NULL,
    CONSTRAINT pk_mdm_code_rule_sequence PRIMARY KEY (rule_id, scope_key),
    CONSTRAINT ck_mdm_code_rule_sequence_positive CHECK (last_value > 0)
);

-- 请求键用于未知结果重试；完整编码唯一键防止周期和修订变更后的历史碰撞。
CREATE TABLE mdm_code_rule_issue (
    rule_id varchar(19) NOT NULL,
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    code varchar(128) NOT NULL,
    revision_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_mdm_code_rule_issue PRIMARY KEY (request_key),
    CONSTRAINT uk_mdm_code_rule_issue_code UNIQUE (rule_id, code),
    CONSTRAINT ck_mdm_code_rule_issue_revision CHECK (revision_no > 0)
);
COMMENT ON TABLE mdm_code_rule_sequence IS 'MDM 编码规则正式流水，作用域为规则身份和 UTC 重置周期';
COMMENT ON TABLE mdm_code_rule_issue IS 'MDM 正式发号幂等记录和规则内完整编码唯一约束';
COMMENT ON COLUMN mdm_code_rule_sequence.rule_id IS '所属 MDM 规则身份 ID，不随修订变化';
COMMENT ON COLUMN mdm_code_rule_sequence.scope_key IS 'UTC 年/月/日周期键；空字符串表示不重置';
COMMENT ON COLUMN mdm_code_rule_issue.rule_id IS '所属 MDM 规则身份 ID；完整编码在该规则内唯一';
COMMENT ON COLUMN mdm_code_rule_issue.revision_id IS '本次正式发号实际使用的不可变发布修订 ID';
