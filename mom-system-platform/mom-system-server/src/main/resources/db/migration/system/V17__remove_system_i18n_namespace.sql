-- 在变更身份规则前验证生产数据；冲突必须由业务确认，迁移禁止自动合并。
DO $$
DECLARE collisions text;
BEGIN
    SELECT string_agg(key, ', ' ORDER BY key) INTO collisions
    FROM (
        SELECT CASE WHEN namespace = 'system' THEN 'system.' || lower(message_key) ELSE lower(message_key) END AS key
        FROM system_i18n_message_definition
        GROUP BY CASE WHEN namespace = 'system' THEN 'system.' || lower(message_key) ELSE lower(message_key) END
        HAVING count(*) > 1
    ) duplicates;
    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION 'System I18n namespace removal blocked by duplicate normalized keys: %', collisions;
    END IF;
END $$;

-- 根 namespace 的局部错误键折叠为 System 异常实际使用的完整稳定 Key。
UPDATE system_i18n_message_definition
SET message_key = 'system.' || lower(message_key)
WHERE namespace = 'system';
UPDATE system_i18n_message_definition
SET message_key = lower(message_key)
WHERE namespace <> 'system';

DROP INDEX ix_system_i18n_message_definition_runtime;
ALTER TABLE system_i18n_message_definition
    DROP CONSTRAINT uk_system_i18n_message_definition_key,
    DROP CONSTRAINT ck_system_i18n_message_definition_namespace,
    DROP CONSTRAINT ck_system_i18n_message_definition_key,
    DROP COLUMN namespace,
    ADD CONSTRAINT uk_system_i18n_message_definition_key UNIQUE (message_key),
    ADD CONSTRAINT ck_system_i18n_message_definition_key CHECK (
        message_key ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$');
CREATE INDEX ix_system_i18n_message_definition_runtime
    ON system_i18n_message_definition (enabled, message_key, id) WHERE NOT deleted;

UPDATE system_i18n_translation
SET message_text = CASE locale_code
    WHEN 'zh-CN' THEN 'Message Key 已存在'
    WHEN 'en-US' THEN 'The message key already exists'
    ELSE message_text
END
WHERE message_id = '9200000000000000012';

INSERT INTO system_i18n_message_definition (
    id, message_key, description, enabled, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9200000000000000016', 'system.messages.keyinvalid', 'I18n Key 格式提示', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000017', 'system.messages.duplicatetextwarning', '相同译文提醒', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000018', 'system.messages.continuesave', '重复译文确认操作', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000019', 'system.messages.keyalreadyexists', 'I18n Key 已存在提示', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO system_i18n_translation (
    id, message_id, locale_code, message_text, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9300000000000000030', '9200000000000000016', 'zh-CN', 'Key 仅允许英文字母、数字及 . _ - 分段；不得包含空格或全角字符，保存时会统一为小写。', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000031', '9200000000000000016', 'en-US', 'Use ASCII letters, digits, and . _ - separators only; no spaces or full-width characters. Keys are stored in lowercase.', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000032', '9200000000000000017', 'zh-CN', '该译文与以下 Key 的内容完全相同：{0}。如果这是有意复用，请确认后继续保存。', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000033', '9200000000000000017', 'en-US', 'This exact translation is already used by these keys: {0}. If this reuse is intentional, confirm and continue saving.', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000034', '9200000000000000018', 'zh-CN', '仍然保存', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000035', '9200000000000000018', 'en-US', 'Save anyway', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000036', '9200000000000000019', 'zh-CN', '该 Key 已存在。Key 是文案的唯一身份，请使用已有 Key 管理译文，不要为相同文案创建另一个 Key。', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000037', '9200000000000000019', 'en-US', 'This key already exists. A key is the unique identity of a message; use the existing key instead of creating another one for the same message.', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

UPDATE system_i18n_translation SET message_text = CASE locale_code
    WHEN 'zh-CN' THEN 'Key 保存前会统一转为小写；不得含空格、全角或非 ASCII 字符，仅允许字母、数字及 . _ - 分段。'
    WHEN 'en-US' THEN 'Keys are stored in lowercase. Do not use spaces, full-width, or non-ASCII characters; use letters, digits, and . _ - separators only.'
    ELSE message_text END
WHERE message_id = '9200000000000000016';

COMMENT ON TABLE system_i18n_message_definition IS 'System Owner 内唯一、不可变的稳定 I18n Key 定义';
COMMENT ON COLUMN system_i18n_message_definition.message_key IS 'Owner 内唯一且创建后不可修改的小写稳定消息键';
