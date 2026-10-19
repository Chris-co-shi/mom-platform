-- V8 已发布，后续迁移补齐关键列语义，保持原 Migration checksum 不变。
COMMENT ON COLUMN wms_code_rule.id IS 'WMS 编码规则身份的技术主键';
COMMENT ON COLUMN wms_code_rule.version IS '规则编辑的乐观并发版本，从零开始递增';
COMMENT ON COLUMN wms_code_rule_revision.id IS '规则修订的技术主键';
COMMENT ON COLUMN wms_code_rule_revision.rule_id IS '所属 WMS 编码规则身份；由应用层保证引用存在';
COMMENT ON COLUMN wms_code_rule_revision.status IS '修订生命周期：草稿、已启用或已停用';
