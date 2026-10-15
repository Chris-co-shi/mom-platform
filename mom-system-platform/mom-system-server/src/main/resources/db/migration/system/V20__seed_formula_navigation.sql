-- System 只持有三级导航名称，Formula 内容文案由 MDM 持有。
INSERT INTO system_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES ('9650000000000004101', 'navigation.mdmformulas', NULL,
    true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO system_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES
    ('9660000000000004101', '9650000000000004101', 'zh-CN', '配方', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004102', '9650000000000004101', 'en-US', 'Formulas', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);
