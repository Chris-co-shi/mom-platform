CREATE TABLE system_i18n_resource (
    id varchar(19) PRIMARY KEY,
    application_code varchar(64) NOT NULL,
    resource_code varchar(64) NOT NULL,
    resource_name varchar(200) NOT NULL,
    default_locale varchar(10) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    published_version bigint,
    published_by varchar(128),
    published_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    description varchar(1000),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_system_i18n_resource_code UNIQUE (application_code, resource_code),
    CONSTRAINT ck_system_i18n_resource_application_code
        CHECK (application_code ~ '^[a-z][a-z0-9-]{1,63}$'),
    CONSTRAINT ck_system_i18n_resource_resource_code
        CHECK (resource_code ~ '^[a-z][a-z0-9-]{1,63}$'),
    CONSTRAINT ck_system_i18n_resource_name CHECK (length(btrim(resource_name)) > 0),
    CONSTRAINT ck_system_i18n_resource_default_locale CHECK (default_locale IN ('zh-CN', 'en-US')),
    CONSTRAINT ck_system_i18n_resource_published_version
        CHECK (published_version IS NULL OR published_version > 0),
    CONSTRAINT ck_system_i18n_resource_published_audit CHECK (
        (published_version IS NULL AND published_by IS NULL AND published_at IS NULL)
        OR (published_version IS NOT NULL AND published_by IS NOT NULL AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_system_i18n_resource_version CHECK (version >= 0)
);

CREATE TABLE system_i18n_message (
    id varchar(19) PRIMARY KEY,
    resource_id varchar(19) NOT NULL,
    message_key varchar(128) NOT NULL,
    locale varchar(10) NOT NULL,
    message_value varchar(4096) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    description varchar(1000),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_system_i18n_message_resource
        FOREIGN KEY (resource_id) REFERENCES system_i18n_resource (id) ON DELETE RESTRICT,
    CONSTRAINT uk_system_i18n_message_key_locale UNIQUE (resource_id, message_key, locale),
    CONSTRAINT ck_system_i18n_message_key
        CHECK (message_key ~ '^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$'),
    CONSTRAINT ck_system_i18n_message_locale CHECK (locale IN ('zh-CN', 'en-US')),
    CONSTRAINT ck_system_i18n_message_value CHECK (
        length(message_value) > 0
        AND message_value !~ E'[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]'
    ),
    CONSTRAINT ck_system_i18n_message_version CHECK (version >= 0)
);

CREATE TABLE system_i18n_release (
    resource_id varchar(19) NOT NULL,
    release_version bigint NOT NULL,
    locale varchar(10) NOT NULL,
    messages_json jsonb NOT NULL,
    message_count integer NOT NULL,
    fallback_count integer NOT NULL,
    checksum varchar(64) NOT NULL,
    source_release_version bigint,
    change_note varchar(1000) NOT NULL,
    published_by varchar(128) NOT NULL,
    published_at timestamptz NOT NULL,
    CONSTRAINT pk_system_i18n_release PRIMARY KEY (resource_id, release_version, locale),
    CONSTRAINT fk_system_i18n_release_resource
        FOREIGN KEY (resource_id) REFERENCES system_i18n_resource (id) ON DELETE RESTRICT,
    CONSTRAINT ck_system_i18n_release_version CHECK (release_version > 0),
    CONSTRAINT ck_system_i18n_release_locale CHECK (locale IN ('zh-CN', 'en-US')),
    CONSTRAINT ck_system_i18n_release_messages_json CHECK (jsonb_typeof(messages_json) = 'object'),
    CONSTRAINT ck_system_i18n_release_message_count CHECK (message_count > 0),
    CONSTRAINT ck_system_i18n_release_fallback_count
        CHECK (fallback_count BETWEEN 0 AND message_count),
    CONSTRAINT ck_system_i18n_release_checksum CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_system_i18n_release_source_version
        CHECK (source_release_version IS NULL OR source_release_version > 0),
    CONSTRAINT ck_system_i18n_release_change_note CHECK (length(btrim(change_note)) > 0)
);

CREATE INDEX ix_system_i18n_resource_runtime
    ON system_i18n_resource (application_code, resource_code, enabled, published_version);
CREATE INDEX ix_system_i18n_message_publish
    ON system_i18n_message (resource_id, enabled, message_key, locale);
CREATE INDEX ix_system_i18n_release_history
    ON system_i18n_release (resource_id, release_version DESC);

CREATE FUNCTION reject_system_i18n_release_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'system_i18n_release is immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_system_i18n_release_immutable
BEFORE UPDATE OR DELETE ON system_i18n_release
FOR EACH ROW EXECUTE FUNCTION reject_system_i18n_release_mutation();

COMMENT ON TABLE system_i18n_resource IS 'System Dynamic I18n 稳定资源头；disabled 是运行时 Kill Switch，禁止物理删除';
COMMENT ON COLUMN system_i18n_resource.application_code IS 'System 稳定 Application Reference，不等同 IAM OAuth clientId';
COMMENT ON COLUMN system_i18n_resource.resource_code IS 'applicationCode 内唯一稳定资源 Code，创建后不可修改';
COMMENT ON COLUMN system_i18n_resource.default_locale IS 'V1 默认 Locale，创建后不可修改，仅 zh-CN/en-US';
COMMENT ON COLUMN system_i18n_resource.published_version IS '当前完整发布版本；回滚创建新版本而不倒退此值';
COMMENT ON TABLE system_i18n_message IS 'Dynamic I18n 可编辑 Draft；修改或禁用只在下一次显式 Publish 生效';
COMMENT ON COLUMN system_i18n_message.message_key IS '资源内稳定消息 Key，与 locale/resourceId 创建后不可修改';
COMMENT ON COLUMN system_i18n_message.message_value IS '最大 4096 字符普通文本；客户端必须按文本渲染，不执行 HTML、Markdown、Script 或表达式';
COMMENT ON TABLE system_i18n_release IS '每版本每 Locale 的完整不可变 JSONB Snapshot；仅允许 INSERT';
COMMENT ON COLUMN system_i18n_release.checksum IS '按 Key 字典序确定性 JSON UTF-8 的 SHA-256';
COMMENT ON COLUMN system_i18n_release.source_release_version IS 'Rollback 新版本所复制的历史版本；普通 Publish 为 NULL';
