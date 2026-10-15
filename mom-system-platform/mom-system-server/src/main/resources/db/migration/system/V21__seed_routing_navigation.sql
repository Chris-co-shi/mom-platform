-- System 只持有工艺路线三级导航标签；业务页面文案由 MDM 持有。
INSERT INTO system_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES ('9650000000000004102', 'navigation.mdmroutings', NULL,
    true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO system_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES
    ('9660000000000004103', '9650000000000004102', 'zh-CN', '工艺路线', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004104', '9650000000000004102', 'en-US', 'Routings', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);
