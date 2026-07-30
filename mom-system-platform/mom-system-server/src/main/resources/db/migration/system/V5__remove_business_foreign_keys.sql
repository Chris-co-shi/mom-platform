DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM system_dictionary_item item
          LEFT JOIN system_dictionary dictionary ON dictionary.id = item.dictionary_id
         WHERE dictionary.id IS NULL
    ) THEN
        RAISE EXCEPTION 'system_dictionary_item contains orphan dictionary_id';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM system_i18n_message message
          LEFT JOIN system_i18n_resource resource ON resource.id = message.resource_id
         WHERE resource.id IS NULL
    ) THEN
        RAISE EXCEPTION 'system_i18n_message contains orphan resource_id';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM system_i18n_release release
          LEFT JOIN system_i18n_resource resource ON resource.id = release.resource_id
         WHERE resource.id IS NULL
    ) THEN
        RAISE EXCEPTION 'system_i18n_release contains orphan resource_id';
    END IF;
END;
$$;

ALTER TABLE system_dictionary_item
    DROP CONSTRAINT fk_system_dictionary_item_dictionary;
ALTER TABLE system_i18n_message
    DROP CONSTRAINT fk_system_i18n_message_resource;
ALTER TABLE system_i18n_release
    DROP CONSTRAINT fk_system_i18n_release_resource;

COMMENT ON COLUMN system_dictionary_item.dictionary_id IS
    '同 mom_system Schema 字典引用；Application 创建时校验，停用不级联，禁止物理外键';
COMMENT ON COLUMN system_i18n_message.resource_id IS
    'System I18n Resource 引用；Application 创建和读取时校验，禁止物理外键';
COMMENT ON COLUMN system_i18n_release.resource_id IS
    '不可变发布快照所属 Resource；仅受控发布/回滚写入，禁止物理外键';
