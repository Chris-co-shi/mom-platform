-- System 只持有导航标签；不复制或修改 MDM 位置业务事实。
UPDATE system_i18n_translation
SET message_text = CASE message_id
        WHEN '9650000000000001006' THEN '位置类型'
        WHEN '9650000000000001007' THEN '位置'
        ELSE message_text END,
    version = version + 1, updated_by = 'SYSTEM_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE locale_code = 'zh-CN'
  AND message_id IN ('9650000000000001006', '9650000000000001007');
