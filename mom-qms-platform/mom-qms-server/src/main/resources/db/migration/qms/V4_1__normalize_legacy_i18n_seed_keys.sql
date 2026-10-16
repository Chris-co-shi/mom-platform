-- V3 的七个驼峰种子 Key 已核实兼容小写查询；翻译按 Message ID 关联。
-- 必须在 V5 收紧约束前执行；不改写历史 Migration，不自动处理未知 Key 或合并冲突。
LOCK TABLE qms_i18n_message_definition IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    verified_keys CONSTANT text[] := ARRAY[
        'qms.common.noData', 'qms.common.defaultUom', 'qms.common.methodDescription',
        'qms.spec.noData', 'qms.spec.addItem', 'qms.spec.removeItem', 'qms.spec.nextRevision'
    ];
BEGIN
    -- 不过滤 enabled/deleted：历史行同样占用稳定 Key，必须全部通过预检。
    IF EXISTS (
        SELECT 1 FROM qms_i18n_message_definition
        WHERE NOT (lower(message_key COLLATE "C") ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
                   AND char_length(message_key) BETWEEN 1 AND 160)
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'QMS I18n normalization precheck failed: invalid normalized key';
    END IF;

    IF EXISTS (
        SELECT 1 FROM qms_i18n_message_definition
        GROUP BY lower(message_key COLLATE "C") HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23505',
            MESSAGE = 'QMS I18n normalization precheck failed: normalized key collision';
    END IF;

    -- 未核实的驼峰 Key 可能被外部按原值引用，不推断其可改名。
    IF EXISTS (
        SELECT 1 FROM qms_i18n_message_definition
        WHERE message_key COLLATE "C" <> lower(message_key COLLATE "C")
          AND NOT (message_key = ANY(verified_keys))
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'QMS I18n normalization precheck failed: unverified legacy key reference';
    END IF;

    -- 正式模型使用 Message ID；发现直接引用 Key 的外键时，即使配置了级联也必须拒绝。
    IF EXISTS (
        SELECT 1 FROM qms_i18n_message_definition WHERE message_key = ANY(verified_keys)
    ) AND EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.confrelid AND a.attnum = ANY(c.confkey)
        WHERE c.contype = 'f' AND c.confrelid = 'qms_i18n_message_definition'::regclass
          AND a.attname = 'message_key'
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'QMS I18n normalization precheck failed: incompatible message_key reference';
    END IF;

    -- 仅更新已核实 Key；ID、审计字段、版本、启停状态和所有翻译记录保持原值。
    UPDATE qms_i18n_message_definition
    SET message_key = lower(message_key COLLATE "C")
    WHERE message_key = ANY(verified_keys);
END $$;
