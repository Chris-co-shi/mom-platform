-- 在变更身份规则前验证生产数据；冲突必须由业务确认，迁移禁止自动合并。
DO $$
DECLARE collisions text;
BEGIN
    SELECT string_agg(key, ', ' ORDER BY key) INTO collisions
    FROM (
        SELECT CASE WHEN namespace = 'auth' THEN 'auth.' || lower(message_key) ELSE lower(message_key) END AS key
        FROM auth_i18n_message_definition
        GROUP BY CASE WHEN namespace = 'auth' THEN 'auth.' || lower(message_key) ELSE lower(message_key) END
        HAVING count(*) > 1
    ) duplicates;
    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION 'Auth I18n namespace removal blocked by duplicate normalized keys: %', collisions;
    END IF;
END $$;

-- 根 namespace 的局部错误键折叠为 ErrorCode 已使用的完整稳定 Key。
UPDATE auth_i18n_message_definition
SET message_key = 'auth.' || lower(message_key)
WHERE namespace = 'auth';
UPDATE auth_i18n_message_definition
SET message_key = lower(message_key)
WHERE namespace <> 'auth';

DROP INDEX ix_auth_i18n_message_definition_runtime;
ALTER TABLE auth_i18n_message_definition
    DROP CONSTRAINT uk_auth_i18n_message_definition_key,
    DROP CONSTRAINT ck_auth_i18n_message_definition_namespace,
    DROP CONSTRAINT ck_auth_i18n_message_definition_key,
    DROP COLUMN namespace,
    ADD CONSTRAINT uk_auth_i18n_message_definition_key UNIQUE (message_key),
    ADD CONSTRAINT ck_auth_i18n_message_definition_key CHECK (
        message_key ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$');
CREATE INDEX ix_auth_i18n_message_definition_runtime
    ON auth_i18n_message_definition (enabled, message_key, id) WHERE NOT deleted;

COMMENT ON TABLE auth_i18n_message_definition IS 'Auth Owner 内唯一、不可变的稳定 I18n Key 定义';
COMMENT ON COLUMN auth_i18n_message_definition.message_key IS 'Owner 内唯一且创建后不可修改的小写稳定消息键';
