-- B2 界面文案：仅本服务既有 Runtime 表；新增版本，不改历史迁移或业务模型。
INSERT INTO system_i18n_message_definition (id,namespace,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9630000000000009001','system.web','system.common.backDirectory','返回目录',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO system_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9640000000000009001','9630000000000009001','zh-CN','返回目录',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9640000000000009002','9630000000000009001','en-US','Back to directory',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);

-- 目录中的技术标识保留原值，中文标签明确其含义，不把 Key 编造成业务名称。
UPDATE system_i18n_translation t
SET message_text = CASE d.message_key
    WHEN 'system.messages.key' THEN '文案标识'
    WHEN 'system.navigation' THEN '系统管理'
  END,
  version = t.version + 1, updated_by = 'B2_UI_MIGRATION', updated_at = CURRENT_TIMESTAMP
FROM system_i18n_message_definition d
WHERE t.message_id = d.id AND d.namespace = 'system.web'
  AND d.message_key IN ('system.messages.key', 'system.navigation')
  AND t.locale_code = 'zh-CN' AND NOT t.deleted AND NOT d.deleted;
