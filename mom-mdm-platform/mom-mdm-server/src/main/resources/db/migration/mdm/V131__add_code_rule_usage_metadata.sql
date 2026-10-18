-- 规则级说明供所有修订共享；旧数据保留 NULL，不根据规则编码猜测使用场景。
ALTER TABLE mdm_code_rule ADD COLUMN usage_scenario varchar(120), ADD COLUMN description varchar(1000);
ALTER TABLE mdm_code_rule ADD CONSTRAINT ck_mdm_code_rule_usage_scenario CHECK (usage_scenario IS NULL OR length(btrim(usage_scenario)) > 0);
COMMENT ON COLUMN mdm_code_rule.usage_scenario IS '规则级使用场景；新建必填，历史未补充数据允许为空，各修订共用';
COMMENT ON COLUMN mdm_code_rule.description IS '规则级纯文本描述；草稿保存时与规则版本、审计在同一本地事务中更新';
