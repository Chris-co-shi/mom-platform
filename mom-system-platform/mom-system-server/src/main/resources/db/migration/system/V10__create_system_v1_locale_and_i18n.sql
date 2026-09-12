-- System V1 现行模型。V1～V9 历史表保留但不再由运行时代码读写。
CREATE TABLE system_supported_locale (
    id varchar(19) NOT NULL,
    locale_code varchar(35) NOT NULL,
    display_name varchar(100) NOT NULL,
    native_name varchar(100) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    is_default boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_supported_locale PRIMARY KEY (id),
    CONSTRAINT uk_system_supported_locale_code UNIQUE (locale_code),
    CONSTRAINT ck_system_supported_locale_code CHECK (
        locale_code ~ '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$'),
    CONSTRAINT ck_system_supported_locale_names CHECK (
        length(btrim(display_name)) > 0 AND length(btrim(native_name)) > 0),
    CONSTRAINT ck_system_supported_locale_default_enabled CHECK (NOT is_default OR enabled),
    CONSTRAINT ck_system_supported_locale_sort_order CHECK (sort_order BETWEEN 0 AND 1000000),
    CONSTRAINT ck_system_supported_locale_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_system_supported_locale_single_default
    ON system_supported_locale (is_default) WHERE is_default AND NOT deleted;
CREATE INDEX ix_system_supported_locale_list
    ON system_supported_locale (enabled, sort_order, locale_code, id) WHERE NOT deleted;

CREATE TABLE system_i18n_message_definition (
    id varchar(19) NOT NULL,
    namespace varchar(128) NOT NULL,
    message_key varchar(160) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_system_i18n_message_definition_key UNIQUE (namespace, message_key),
    CONSTRAINT ck_system_i18n_message_definition_namespace CHECK (
        namespace = 'system' OR namespace ~ '^system(?:\.[a-z][a-z0-9-]*)+$'),
    CONSTRAINT ck_system_i18n_message_definition_key CHECK (
        message_key ~ '^[a-zA-Z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)*$'),
    CONSTRAINT ck_system_i18n_message_definition_version CHECK (version >= 0)
);

CREATE INDEX ix_system_i18n_message_definition_runtime
    ON system_i18n_message_definition (namespace, enabled, message_key, id) WHERE NOT deleted;

CREATE TABLE system_i18n_translation (
    id varchar(19) NOT NULL,
    message_id varchar(19) NOT NULL,
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_system_i18n_translation_message_locale UNIQUE (message_id, locale_code),
    CONSTRAINT ck_system_i18n_translation_locale CHECK (
        locale_code ~ '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$'),
    CONSTRAINT ck_system_i18n_translation_text CHECK (length(message_text) BETWEEN 1 AND 4096 AND message_text !~ E'[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]' AND message_text !~ '<[^>]*>'),
    CONSTRAINT ck_system_i18n_translation_version CHECK (version >= 0)
);

CREATE INDEX ix_system_i18n_translation_runtime
    ON system_i18n_translation (locale_code, message_id, id) WHERE NOT deleted;

INSERT INTO system_supported_locale (
    id, locale_code, display_name, native_name, enabled, is_default, sort_order,
    version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9100000000000000001', 'zh-CN', '简体中文', '简体中文', true, true, 10,
     0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9100000000000000002', 'en-US', '英语（美国）', 'English (United States)', true, false, 20,
     0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

-- System 自己产生的核心管理错误随现行模型初始化；管理员后续可动态调整 Translation 文案。
INSERT INTO system_i18n_message_definition (
    id, namespace, message_key, description, enabled, version, deleted,
    created_by, created_at, updated_by, updated_at)
VALUES
    ('9200000000000000001', 'system', 'dictionary.error.code_conflict', '字典 Code 唯一冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000002', 'system', 'dictionary.error.item_key_conflict', '字典条目 Key 唯一冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000003', 'system', 'dictionary.error.type_not_found', '字典类型不存在', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000004', 'system', 'dictionary.error.item_not_found', '字典条目不存在', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000005', 'system', 'dictionary.error.stale_version', '字典乐观版本冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000006', 'system', 'locale.error.code_conflict', 'Locale Code 唯一冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000007', 'system', 'locale.error.default_cannot_disable', '默认 Locale 禁用冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000008', 'system', 'locale.error.default_must_enable', '默认 Locale 必须启用', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000009', 'system', 'locale.error.not_found', 'Locale 不存在', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000010', 'system', 'locale.error.stale_version', 'Locale 乐观版本冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000011', 'system', 'locale.error.default_conflict', '默认 Locale 唯一冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000012', 'system', 'i18n.error.message_key_conflict', 'I18n Message Key 唯一冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000013', 'system', 'i18n.error.stale_version', 'I18n 乐观版本冲突', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000014', 'system', 'i18n.error.placeholder_mismatch', '不同 Locale Placeholder 不一致', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9200000000000000015', 'system', 'i18n.error.message_not_found', 'I18n Message 不存在', true, 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO system_i18n_translation (
    id, message_id, locale_code, message_text, version, deleted,
    created_by, created_at, updated_by, updated_at)
VALUES
    ('9300000000000000001', '9200000000000000001', 'zh-CN', '字典 Code 已存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000002', '9200000000000000001', 'en-US', 'The dictionary code already exists', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000003', '9200000000000000002', 'zh-CN', '字典条目 Key 已存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000004', '9200000000000000002', 'en-US', 'The dictionary item key already exists', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000005', '9200000000000000003', 'zh-CN', '字典类型不存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000006', '9200000000000000003', 'en-US', 'The dictionary type does not exist', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000007', '9200000000000000004', 'zh-CN', '字典条目不存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000008', '9200000000000000004', 'en-US', 'The dictionary item does not exist', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000009', '9200000000000000005', 'zh-CN', '字典已被其他请求修改', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000010', '9200000000000000005', 'en-US', 'The dictionary was modified by another request', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000011', '9200000000000000006', 'zh-CN', 'Locale Code 已存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000012', '9200000000000000006', 'en-US', 'The locale code already exists', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000013', '9200000000000000007', 'zh-CN', '默认 Locale 不能直接禁用', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000014', '9200000000000000007', 'en-US', 'The default locale cannot be disabled', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000015', '9200000000000000008', 'zh-CN', '默认 Locale 必须启用', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000016', '9200000000000000008', 'en-US', 'The default locale must be enabled', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000017', '9200000000000000009', 'zh-CN', 'Locale 不存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000018', '9200000000000000009', 'en-US', 'The locale does not exist', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000019', '9200000000000000010', 'zh-CN', 'Locale 已被其他请求修改', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000020', '9200000000000000010', 'en-US', 'The locale was modified by another request', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000021', '9200000000000000011', 'zh-CN', '全平台只能存在一个默认 Locale', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000022', '9200000000000000011', 'en-US', 'Only one platform default locale is allowed', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000023', '9200000000000000012', 'zh-CN', 'namespace 与 messageKey 已存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000024', '9200000000000000012', 'en-US', 'The namespace and message key already exist', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000025', '9200000000000000013', 'zh-CN', 'I18n 数据已被其他请求修改', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000026', '9200000000000000013', 'en-US', 'The i18n data was modified by another request', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000027', '9200000000000000014', 'zh-CN', '不同 Locale 的 placeholder 集合必须一致', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000028', '9200000000000000014', 'en-US', 'All locale translations must use the same placeholders', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000029', '9200000000000000015', 'zh-CN', 'I18n Message 不存在', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9300000000000000030', '9200000000000000015', 'en-US', 'The i18n message does not exist', 0, false,
     'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

COMMENT ON TABLE system_supported_locale IS 'System V1 全平台可选择 Locale 与唯一默认 Locale 权威';
COMMENT ON COLUMN system_supported_locale.id IS '应用侧 ASSIGN_ID 生成的 varchar(19) String 技术主键';
COMMENT ON COLUMN system_supported_locale.locale_code IS '跨服务引用的规范 BCP 47 Locale Tag';
COMMENT ON COLUMN system_supported_locale.enabled IS '是否允许新请求选择；默认 Locale 必须启用';
COMMENT ON COLUMN system_supported_locale.is_default IS '全平台唯一默认 Locale；用于不存在、禁用或缺失翻译时回退';
COMMENT ON COLUMN system_supported_locale.version IS 'MyBatis-Plus 乐观锁版本';
COMMENT ON TABLE system_i18n_message_definition IS 'System V1 只拥有 system.* namespace 的稳定消息定义';
COMMENT ON COLUMN system_i18n_message_definition.id IS '应用侧 ASSIGN_ID 生成的 varchar(19) String 技术主键';
COMMENT ON COLUMN system_i18n_message_definition.namespace IS 'System 拥有的 namespace；禁止 framework.* 与业务域 namespace';
COMMENT ON COLUMN system_i18n_message_definition.message_key IS 'namespace 内唯一且创建后不可修改的稳定消息键';
COMMENT ON COLUMN system_i18n_message_definition.enabled IS '禁用后不进入 Runtime Bundle但历史定义保留';
COMMENT ON COLUMN system_i18n_message_definition.version IS 'MyBatis-Plus 乐观锁版本';
COMMENT ON TABLE system_i18n_translation IS 'System V1 消息的可动态更新普通文本 Translation';
COMMENT ON COLUMN system_i18n_translation.id IS '应用侧 ASSIGN_ID 生成的 varchar(19) String 技术主键';
COMMENT ON COLUMN system_i18n_translation.message_id IS '同 Schema 消息定义引用，由 Application 校验且无物理外键';
COMMENT ON COLUMN system_i18n_translation.locale_code IS 'System SupportedLocale 稳定 Code 引用且无物理外键';
COMMENT ON COLUMN system_i18n_translation.message_text IS '仅允许普通文本和数字位置占位符，不允许 HTML 或可执行模板';
COMMENT ON COLUMN system_i18n_translation.version IS 'MyBatis-Plus 乐观锁版本';
