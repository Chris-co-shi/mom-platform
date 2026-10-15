-- System 只持有生产版本三级导航标签。
INSERT INTO system_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES ('9650000000000004103', 'navigation.mdmproductionversions', NULL,
    true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO system_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES
    ('9660000000000004105', '9650000000000004103', 'zh-CN', '生产版本', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004106', '9650000000000004103', 'en-US', 'Production Versions', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);
