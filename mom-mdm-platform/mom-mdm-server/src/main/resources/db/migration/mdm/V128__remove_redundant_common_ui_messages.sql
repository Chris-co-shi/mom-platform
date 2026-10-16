-- 仅清理已迁移到 System 普通 UI 操作的四组原始种子；不读取其他 Owner 的数据。
-- 部署前必须先发布使用 system.common.cancel / system.common.close 的消费者。
-- 当前仓库业务引用已迁移；前端 MessageKey 联合类型中的历史声明不参与运行时取词。
-- 任一目标缺失、被修改、增加语言或存在引用均拒绝整个迁移，禁止自动修复用户数据。
LOCK TABLE mdm_i18n_message_definition, mdm_i18n_translation IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE mdm_ui_cleanup_message (
    id varchar(19) PRIMARY KEY,
    message_key varchar(160) NOT NULL
) ON COMMIT DROP;
INSERT INTO mdm_ui_cleanup_message VALUES
    ('9730000000000000035', 'mdm.plants.cancel'),
    ('9730000000000000037', 'mdm.plants.close'),
    ('9730000000000001034', 'mdm.struct.close'),
    ('9730000000000001035', 'mdm.struct.cancel');

CREATE TEMP TABLE mdm_ui_cleanup_translation (
    id varchar(19) PRIMARY KEY,
    message_id varchar(19) NOT NULL,
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL
) ON COMMIT DROP;
INSERT INTO mdm_ui_cleanup_translation VALUES
    ('9740000000000000069', '9730000000000000035', 'zh-CN', '取消'),
    ('9740000000000000070', '9730000000000000035', 'en-US', 'Cancel'),
    ('9740000000000000073', '9730000000000000037', 'zh-CN', '关闭'),
    ('9740000000000000074', '9730000000000000037', 'en-US', 'Close'),
    ('9740000000000001067', '9730000000000001034', 'zh-CN', '关闭'),
    ('9740000000000001068', '9730000000000001034', 'en-US', 'Close'),
    ('9740000000000001069', '9730000000000001035', 'zh-CN', '取消'),
    ('9740000000000001070', '9730000000000001035', 'en-US', 'Cancel');

DO $$
DECLARE
    reference_column record;
    reference_found boolean;
    reference_tokens text[];
    reference_patterns text[];
    affected bigint;
BEGIN
    IF (SELECT count(*) FROM mdm_ui_cleanup_message e
        JOIN mdm_i18n_message_definition m ON m.id = e.id AND m.message_key = e.message_key
        WHERE m.description IS NULL AND m.enabled AND NOT m.deleted AND m.version = 0
          AND m.created_by = 'MDM_WEB_MIGRATION' AND m.updated_by = 'MDM_WEB_MIGRATION'
          AND m.created_at = m.updated_at) <> 4 THEN
        RAISE EXCEPTION 'MDM I18n cleanup blocked: message seed mismatch';
    END IF;

    IF (SELECT count(*) FROM mdm_ui_cleanup_translation e
        JOIN mdm_i18n_translation t ON t.id = e.id AND t.message_id = e.message_id
            AND t.locale_code = e.locale_code AND t.message_text = e.message_text
        JOIN mdm_i18n_message_definition m ON m.id = t.message_id
        WHERE NOT t.deleted AND t.version = 0
          AND t.created_by = 'MDM_WEB_MIGRATION' AND t.updated_by = 'MDM_WEB_MIGRATION'
          AND t.created_at = t.updated_at AND t.created_at = m.created_at) <> 8
       OR (SELECT count(*) FROM mdm_i18n_translation t
           WHERE t.message_id IN (SELECT id FROM mdm_ui_cleanup_message)
              OR t.id IN (SELECT id FROM mdm_ui_cleanup_translation)) <> 8 THEN
        RAISE EXCEPTION 'MDM I18n cleanup blocked: translation seed mismatch';
    END IF;

    -- 当前模型没有业务外键；意外新增的引用约束一律拒绝，避免 ON DELETE CASCADE。
    -- 这里只读 PostgreSQL 目录元数据，不查询 System 或其他 Schema 的业务数据。
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE contype = 'f' AND confrelid IN (
                   'mdm_i18n_message_definition'::regclass, 'mdm_i18n_translation'::regclass)) THEN
        RAISE EXCEPTION 'MDM I18n cleanup blocked: foreign reference';
    END IF;

    SELECT array_agg(token), array_agg('%' || token || '%')
    INTO reference_tokens, reference_patterns
    FROM (SELECT id::text AS token FROM mdm_ui_cleanup_message
          UNION ALL SELECT message_key FROM mdm_ui_cleanup_message
          UNION ALL SELECT id FROM mdm_ui_cleanup_translation) tokens;

    -- 同 Owner 的译文/说明也可能嵌入旧 Key 或 ID；原始种子以外的此类内容必须人工确认。
    IF EXISTS (SELECT 1 FROM mdm_i18n_translation
               WHERE message_id NOT IN (SELECT id FROM mdm_ui_cleanup_message)
                 AND message_text LIKE ANY (reference_patterns))
       OR EXISTS (SELECT 1 FROM mdm_i18n_message_definition
                  WHERE id NOT IN (SELECT id FROM mdm_ui_cleanup_message)
                    AND description LIKE ANY (reference_patterns)) THEN
        RAISE EXCEPTION 'MDM I18n cleanup blocked: embedded message reference';
    END IF;

    -- 检查本 Schema 新增的约定引用列及 JSON 内容（含逻辑删除行）；不扫描跨 Owner 业务表。
    FOR reference_column IN
        SELECT n.nspname, c.relname, a.attname
        FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = current_schema() AND c.relkind IN ('r', 'p')
          AND c.relname NOT IN ('mdm_i18n_message_definition', 'mdm_i18n_translation')
          AND a.attnum > 0 AND NOT a.attisdropped
          AND (a.attname ~ '(^|_)(message|translation|i18n)(_|$)' OR a.atttypid IN ('json'::regtype, 'jsonb'::regtype))
          AND a.atttypid IN ('text'::regtype, 'varchar'::regtype, 'json'::regtype, 'jsonb'::regtype)
        ORDER BY c.relname, a.attname
    LOOP
        -- 阻止预检后并发写入引用；锁持续到 Flyway 本次迁移事务结束。
        EXECUTE format('LOCK TABLE %I.%I IN SHARE ROW EXCLUSIVE MODE',
                       reference_column.nspname, reference_column.relname);
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I.%I WHERE %I::text = ANY ($1) OR %I::text LIKE ANY ($2))',
                       reference_column.nspname, reference_column.relname,
                       reference_column.attname, reference_column.attname)
            INTO reference_found USING reference_tokens, reference_patterns;
        IF reference_found THEN
            RAISE EXCEPTION 'MDM I18n cleanup blocked: logical reference in %.%',
                reference_column.relname, reference_column.attname;
        END IF;
    END LOOP;

    DELETE FROM mdm_i18n_translation WHERE id IN (SELECT id FROM mdm_ui_cleanup_translation);
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 8 THEN
        RAISE EXCEPTION 'MDM I18n cleanup blocked: translation delete count mismatch';
    END IF;
    DELETE FROM mdm_i18n_message_definition WHERE id IN (SELECT id FROM mdm_ui_cleanup_message);
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 4 THEN
        RAISE EXCEPTION 'MDM I18n cleanup blocked: message delete count mismatch';
    END IF;
END $$;
