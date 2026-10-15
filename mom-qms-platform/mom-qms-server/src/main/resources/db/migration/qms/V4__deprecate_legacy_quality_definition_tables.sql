-- 历史 V1–V3 已发布，不能改写或删表。这些表只保留已有数据，新权威为 mom_mdm。
COMMENT ON TABLE qms_inspection_characteristic IS
    'LEGACY READ-ONLY DATA: 检验特性权威已转移至 MDM，QMS 运行时不再读写本表';
COMMENT ON TABLE qms_inspection_method IS
    'LEGACY READ-ONLY DATA: 检验方法权威已转移至 MDM，QMS 运行时不再读写本表';
COMMENT ON TABLE qms_quality_specification IS
    'LEGACY READ-ONLY DATA: 质量标准权威已转移至 MDM，本表仅保留历史数据';
COMMENT ON TABLE qms_specification_revision IS
    'LEGACY READ-ONLY DATA: 质量标准修订权威已转移至 MDM';
COMMENT ON TABLE qms_specification_item IS
    'LEGACY READ-ONLY DATA: 质量标准条目权威已转移至 MDM';

-- V3 的质量定义文案随原 QMS 编辑入口一起退役。保留翻译历史，但不再通过 QMS 运行时接口发布。
UPDATE qms_i18n_message_definition
SET enabled = false,
    version = version + 1,
    updated_by = 'QMS_MIGRATION',
    updated_at = CURRENT_TIMESTAMP
WHERE enabled = true
  AND (message_key LIKE 'qms.common.%' OR message_key LIKE 'qms.spec.%');
