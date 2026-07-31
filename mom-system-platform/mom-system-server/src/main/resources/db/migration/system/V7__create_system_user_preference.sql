CREATE TABLE system_user_preference (
    id varchar(19) NOT NULL,
    user_id varchar(19) NOT NULL,
    locale varchar(5),
    display_timezone varchar(64),
    theme_mode varchar(16),
    density varchar(16),
    page_size integer,
    version bigint NOT NULL DEFAULT 0,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_user_preference PRIMARY KEY (id),
    CONSTRAINT uk_system_user_preference_user UNIQUE (user_id),
    CONSTRAINT ck_system_user_preference_locale CHECK (locale IS NULL OR locale IN ('zh-CN', 'en-US')),
    CONSTRAINT ck_system_user_preference_timezone CHECK (
        display_timezone IS NULL OR (display_timezone <> '' AND display_timezone !~ '^(GMT|UTC)[+-]')),
    CONSTRAINT ck_system_user_preference_theme CHECK (
        theme_mode IS NULL OR theme_mode IN ('SYSTEM', 'LIGHT', 'DARK')),
    CONSTRAINT ck_system_user_preference_density CHECK (
        density IS NULL OR density IN ('COMFORTABLE', 'COMPACT')),
    CONSTRAINT ck_system_user_preference_page_size CHECK (
        page_size IS NULL OR page_size IN (10, 20, 50, 100)),
    CONSTRAINT ck_system_user_preference_version_non_negative CHECK (version >= 0)
);

COMMENT ON TABLE system_user_preference IS 'System 用户显示偏好；NULL 表示使用平台默认值，不参与授权或业务事实';
COMMENT ON COLUMN system_user_preference.id IS 'MOM String 技术主键';
COMMENT ON COLUMN system_user_preference.user_id IS 'JWT sub 提供的 IAM 用户稳定引用，不是 IAM 用户副本';
COMMENT ON COLUMN system_user_preference.locale IS '用户显式保存的显示 Locale，仅支持 zh-CN/en-US';
COMMENT ON COLUMN system_user_preference.display_timezone IS '用户显示用 IANA Zone ID，不决定 Factory 业务日期';
COMMENT ON COLUMN system_user_preference.theme_mode IS '显示主题覆盖：SYSTEM/LIGHT/DARK';
COMMENT ON COLUMN system_user_preference.density IS '页面显示密度覆盖：COMFORTABLE/COMPACT';
COMMENT ON COLUMN system_user_preference.page_size IS '默认分页大小覆盖：10/20/50/100';
COMMENT ON COLUMN system_user_preference.version IS '从零开始的乐观锁版本';
COMMENT ON COLUMN system_user_preference.created_by IS '创建 Actor ID';
COMMENT ON COLUMN system_user_preference.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_user_preference.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN system_user_preference.updated_at IS 'UTC 最近修改时间点';

CREATE TABLE system_user_view_setting (
    id varchar(19) NOT NULL,
    user_id varchar(19) NOT NULL,
    application_code varchar(64) NOT NULL,
    view_key varchar(128) NOT NULL,
    schema_version integer NOT NULL,
    columns_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    sort_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    filters_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    page_size integer,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_user_view_setting PRIMARY KEY (id),
    CONSTRAINT uk_system_user_view_setting_user_application_view
        UNIQUE (user_id, application_code, view_key),
    CONSTRAINT ck_system_user_view_setting_application_code CHECK (
        application_code ~ '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_system_user_view_setting_view_key CHECK (
        view_key ~ '^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$'),
    CONSTRAINT ck_system_user_view_setting_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_system_user_view_setting_columns_root CHECK (jsonb_typeof(columns_json) = 'array'),
    CONSTRAINT ck_system_user_view_setting_sort_root CHECK (jsonb_typeof(sort_json) = 'array'),
    CONSTRAINT ck_system_user_view_setting_filters_root CHECK (jsonb_typeof(filters_json) = 'array'),
    CONSTRAINT ck_system_user_view_setting_page_size CHECK (
        page_size IS NULL OR page_size IN (10, 20, 50, 100)),
    CONSTRAINT ck_system_user_view_setting_version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_system_user_view_setting_user_application_list
    ON system_user_view_setting (user_id, application_code, view_key, id);

COMMENT ON TABLE system_user_view_setting IS 'System 用户受限视图设置；只保存类型化客户端显示状态，不保存查询授权规则';
COMMENT ON COLUMN system_user_view_setting.id IS 'MOM String 技术主键';
COMMENT ON COLUMN system_user_view_setting.user_id IS 'JWT sub 提供的 IAM 用户稳定引用';
COMMENT ON COLUMN system_user_view_setting.application_code IS '稳定小写 kebab-case 应用引用；不是 IAM Client ID 或 S17 外键';
COMMENT ON COLUMN system_user_view_setting.view_key IS '应用内稳定小写点分视图 Code';
COMMENT ON COLUMN system_user_view_setting.schema_version IS '客户端解释列、排序和过滤结构的正整数版本';
COMMENT ON COLUMN system_user_view_setting.columns_json IS '受控 Column Setting 数组 JSONB';
COMMENT ON COLUMN system_user_view_setting.sort_json IS '最多三项的受控 Sort Setting 数组 JSONB';
COMMENT ON COLUMN system_user_view_setting.filters_json IS '最多二十项的受控 Saved Filter 数组 JSONB';
COMMENT ON COLUMN system_user_view_setting.page_size IS '该视图分页大小覆盖';
COMMENT ON COLUMN system_user_view_setting.enabled IS 'false 表示已 Reset，读取时返回默认空视图';
COMMENT ON COLUMN system_user_view_setting.version IS '从零开始的乐观锁版本';
COMMENT ON COLUMN system_user_view_setting.created_by IS '创建 Actor ID';
COMMENT ON COLUMN system_user_view_setting.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_user_view_setting.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN system_user_view_setting.updated_at IS 'UTC 最近修改时间点';
