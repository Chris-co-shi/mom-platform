-- 保留现有期初单流水并扩展为按规则身份及 UTC 周期的作用域。
ALTER TABLE wms_code_rule_sequence ADD COLUMN scope_key varchar(8) NOT NULL DEFAULT '';
ALTER TABLE wms_code_rule_sequence DROP CONSTRAINT pk_wms_code_rule_sequence;
ALTER TABLE wms_code_rule_sequence ADD CONSTRAINT pk_wms_code_rule_sequence PRIMARY KEY (rule_id, scope_key);
COMMENT ON TABLE wms_code_rule_sequence IS 'WMS 编码规则正式流水，作用域为规则身份和 UTC 重置周期；旧流水保留在空周期';

CREATE TABLE wms_code_rule_issue (
    rule_id varchar(19) NOT NULL,
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    code varchar(128) NOT NULL,
    revision_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_wms_code_rule_issue PRIMARY KEY (request_key),
    CONSTRAINT uk_wms_code_rule_issue_code UNIQUE (rule_id, code),
    CONSTRAINT ck_wms_code_rule_issue_revision CHECK (revision_no > 0)
);
COMMENT ON TABLE wms_code_rule_issue IS 'WMS 正式发号幂等记录和规则内完整编码唯一约束';
COMMENT ON COLUMN wms_code_rule_sequence.scope_key IS 'UTC 年/月/日周期键；空字符串表示不重置';
COMMENT ON COLUMN wms_code_rule_issue.rule_id IS '所属 WMS 规则身份 ID；完整编码在该规则内唯一';
COMMENT ON COLUMN wms_code_rule_issue.revision_id IS '本次正式发号实际使用的不可变发布修订 ID';

-- 旧业务发号记录进入完整编码唯一集合；请求键使用历史单据 ID，不与新请求复用。
INSERT INTO wms_code_rule_issue(rule_id,request_key,request_hash,code,revision_id,revision_no,created_at)
SELECT '8300000000000000001','legacy:opening:' || document.id,repeat('0',64),document.document_no,
       '8300000000000000002',1,document.created_at
FROM wms_opening_document document;
INSERT INTO wms_code_rule_issue(rule_id,request_key,request_hash,code,revision_id,revision_no,created_at)
SELECT '8300000000000000013','legacy:count:' || task.id,repeat('0',64),task.task_no,
       '8300000000000000014',1,task.created_at
FROM wms_inventory_count_task task;
