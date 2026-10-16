-- 与 I18nRules.messageKey 的规范化结果一致：Owner 内唯一、ASCII 小写、无需 Owner 前缀。
-- 锁住写入后检查所有历史行（含停用和逻辑删除），避免预检与约束替换间出现并发写入。
LOCK TABLE qms_i18n_message_definition IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM qms_i18n_message_definition
        WHERE NOT (message_key COLLATE "C" ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
                   AND char_length(message_key) BETWEEN 1 AND 160)
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'QMS I18n message_key precheck failed: existing keys violate I18nRules',
            HINT = 'Review all existing keys, including disabled and deleted rows. Resolve incompatible keys explicitly before retrying; this migration never renames, deletes or merges data.';
    END IF;
END $$;

ALTER TABLE qms_i18n_message_definition DROP CONSTRAINT ck_qms_i18n_message_key;
ALTER TABLE qms_i18n_message_definition
    ADD CONSTRAINT ck_qms_i18n_message_key
    CHECK (message_key COLLATE "C" ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
           AND char_length(message_key) BETWEEN 1 AND 160);
