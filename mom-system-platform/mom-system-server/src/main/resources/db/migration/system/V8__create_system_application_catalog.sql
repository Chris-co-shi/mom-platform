CREATE TABLE system_application (
    id varchar(19) NOT NULL,
    application_code varchar(64) NOT NULL,
    application_type varchar(16) NOT NULL,
    i18n_resource_code varchar(64) NOT NULL,
    i18n_message_key varchar(128) NOT NULL,
    icon_key varchar(128),
    description varchar(1000),
    route_contract_version integer NOT NULL DEFAULT 1,
    sort_order integer NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT true,
    published_release_id varchar(19),
    published_version bigint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_application PRIMARY KEY (id),
    CONSTRAINT uk_system_application_code UNIQUE (application_code),
    CONSTRAINT ck_system_application_code CHECK (
        application_code ~ '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_system_application_type CHECK (application_type IN ('PLATFORM', 'BUSINESS')),
    CONSTRAINT ck_system_application_i18n_resource CHECK (
        i18n_resource_code ~ '^[a-z][a-z0-9-]{1,63}$'),
    CONSTRAINT ck_system_application_i18n_key CHECK (
        i18n_message_key ~ '^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$'),
    CONSTRAINT ck_system_application_route_contract CHECK (route_contract_version > 0),
    CONSTRAINT ck_system_application_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_system_application_published_pointer CHECK (
        published_version >= 0
        AND ((published_release_id IS NULL) = (published_version = 0))),
    CONSTRAINT ck_system_application_version CHECK (version >= 0)
);

CREATE INDEX ix_system_application_enabled_sort
    ON system_application (enabled, sort_order, application_code, id);
CREATE INDEX ix_system_application_i18n_reference
    ON system_application (i18n_resource_code, i18n_message_key);

COMMENT ON TABLE system_application IS 'System 拥有的逻辑应用目录；不是 OAuth Client，不保存可执行前端组件';
COMMENT ON COLUMN system_application.id IS 'MOM String 技术主键';
COMMENT ON COLUMN system_application.application_code IS '全局稳定小写 kebab-case Application Code，创建后不可修改';
COMMENT ON COLUMN system_application.application_type IS 'V1 Application 分类：PLATFORM/BUSINESS';
COMMENT ON COLUMN system_application.i18n_resource_code IS 'Dynamic I18n 资源稳定引用';
COMMENT ON COLUMN system_application.i18n_message_key IS 'Dynamic I18n 消息 Key 稳定引用';
COMMENT ON COLUMN system_application.icon_key IS '客户端静态 Icon Registry Key，不是 URL 或可执行表达式';
COMMENT ON COLUMN system_application.route_contract_version IS '客户端静态 Route Registry 最低契约版本';
COMMENT ON COLUMN system_application.enabled IS '运行时即时 Kill Switch；false 时不返回最后发布目录';
COMMENT ON COLUMN system_application.published_release_id IS '当前不可变 Release 技术引用，无物理外键';
COMMENT ON COLUMN system_application.published_version IS '当前发布版本，零表示尚未发布';
COMMENT ON COLUMN system_application.version IS 'Application Catalog Draft 聚合乐观锁版本';
COMMENT ON COLUMN system_application.created_by IS '创建 Actor ID';
COMMENT ON COLUMN system_application.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_application.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN system_application.updated_at IS 'UTC 最近修改时间点';

CREATE TABLE system_navigation_item (
    id varchar(19) NOT NULL,
    application_id varchar(19) NOT NULL,
    parent_id varchar(19),
    client_channel varchar(16) NOT NULL,
    navigation_type varchar(16) NOT NULL,
    route_key varchar(128) NOT NULL,
    i18n_resource_code varchar(64) NOT NULL,
    i18n_message_key varchar(128) NOT NULL,
    permission_code varchar(160),
    icon_key varchar(128),
    visible_in_menu boolean NOT NULL DEFAULT true,
    visible_in_breadcrumb boolean NOT NULL DEFAULT true,
    visible_in_tab boolean NOT NULL DEFAULT true,
    keep_alive boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_navigation_item PRIMARY KEY (id),
    CONSTRAINT uk_system_navigation_item_route
        UNIQUE (application_id, client_channel, route_key),
    CONSTRAINT ck_system_navigation_item_channel CHECK (client_channel IN ('WEB', 'MOBILE')),
    CONSTRAINT ck_system_navigation_item_type CHECK (navigation_type IN ('GROUP', 'ROUTE')),
    CONSTRAINT ck_system_navigation_item_route_key CHECK (
        route_key ~ '^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$'),
    CONSTRAINT ck_system_navigation_item_i18n_resource CHECK (
        i18n_resource_code ~ '^[a-z][a-z0-9-]{1,63}$'),
    CONSTRAINT ck_system_navigation_item_i18n_key CHECK (
        i18n_message_key ~ '^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$'),
    CONSTRAINT ck_system_navigation_item_permission CHECK (
        permission_code IS NULL OR permission_code ~
        '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$'),
    CONSTRAINT ck_system_navigation_item_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_system_navigation_item_group_keep_alive CHECK (
        navigation_type = 'ROUTE' OR keep_alive = false),
    CONSTRAINT ck_system_navigation_item_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_system_navigation_item_version CHECK (version >= 0)
);

CREATE INDEX ix_system_navigation_item_tree
    ON system_navigation_item (
        application_id, client_channel, parent_id, sort_order, route_key, id);
CREATE INDEX ix_system_navigation_item_permission_reference
    ON system_navigation_item (permission_code)
    WHERE permission_code IS NOT NULL;
CREATE INDEX ix_system_navigation_item_i18n_reference
    ON system_navigation_item (i18n_resource_code, i18n_message_key);

COMMENT ON TABLE system_navigation_item IS 'Application 内可编辑 Navigation Draft；System 只保存元数据和稳定引用';
COMMENT ON COLUMN system_navigation_item.id IS 'MOM String 技术主键';
COMMENT ON COLUMN system_navigation_item.application_id IS 'System Application 技术引用，无物理外键';
COMMENT ON COLUMN system_navigation_item.parent_id IS '同 Application、同 Channel 父节点引用，无物理外键';
COMMENT ON COLUMN system_navigation_item.client_channel IS '静态客户端执行渠道：WEB/MOBILE';
COMMENT ON COLUMN system_navigation_item.navigation_type IS 'V1 节点类型：GROUP/ROUTE';
COMMENT ON COLUMN system_navigation_item.route_key IS '客户端静态 Route Registry Key；不是 Path、Component 或动态 import';
COMMENT ON COLUMN system_navigation_item.i18n_resource_code IS 'Dynamic I18n 资源稳定引用';
COMMENT ON COLUMN system_navigation_item.i18n_message_key IS 'Dynamic I18n 消息 Key 稳定引用';
COMMENT ON COLUMN system_navigation_item.permission_code IS 'IAM Permission Code Reference；NULL 表示已认证即可见';
COMMENT ON COLUMN system_navigation_item.enabled IS 'Draft 启停状态，仅在下一次 Publish 后影响 Runtime';
COMMENT ON COLUMN system_navigation_item.version IS '节点乐观锁版本';
COMMENT ON COLUMN system_navigation_item.created_by IS '创建 Actor ID';
COMMENT ON COLUMN system_navigation_item.created_at IS 'UTC 创建时间点';
COMMENT ON COLUMN system_navigation_item.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN system_navigation_item.updated_at IS 'UTC 最近修改时间点';

CREATE TABLE system_catalog_release (
    id varchar(19) NOT NULL,
    application_id varchar(19) NOT NULL,
    application_code varchar(64) NOT NULL,
    release_version bigint NOT NULL,
    snapshot_schema_version integer NOT NULL DEFAULT 1,
    route_contract_version integer NOT NULL,
    source_application_version bigint NOT NULL,
    source_release_version bigint,
    snapshot_json jsonb NOT NULL,
    node_count integer NOT NULL,
    checksum varchar(64) NOT NULL,
    change_note varchar(1000),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_system_catalog_release PRIMARY KEY (id),
    CONSTRAINT uk_system_catalog_release_application_version
        UNIQUE (application_id, release_version),
    CONSTRAINT ck_system_catalog_release_application_code CHECK (
        application_code ~ '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_system_catalog_release_version CHECK (release_version > 0),
    CONSTRAINT ck_system_catalog_release_schema_version CHECK (snapshot_schema_version > 0),
    CONSTRAINT ck_system_catalog_release_route_contract CHECK (route_contract_version > 0),
    CONSTRAINT ck_system_catalog_release_source_application CHECK (source_application_version >= 0),
    CONSTRAINT ck_system_catalog_release_source_release CHECK (
        source_release_version IS NULL OR source_release_version > 0),
    CONSTRAINT ck_system_catalog_release_snapshot_root CHECK (
        jsonb_typeof(snapshot_json) = 'object'),
    CONSTRAINT ck_system_catalog_release_node_count CHECK (node_count BETWEEN 0 AND 1000),
    CONSTRAINT ck_system_catalog_release_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_system_catalog_release_history
    ON system_catalog_release (application_id, release_version DESC, id);

COMMENT ON TABLE system_catalog_release IS 'Application Catalog 不可变完整发布快照；Rollback 创建新版本而不修改历史';
COMMENT ON COLUMN system_catalog_release.id IS 'MOM String 技术主键';
COMMENT ON COLUMN system_catalog_release.application_id IS 'System Application 技术引用，无物理外键';
COMMENT ON COLUMN system_catalog_release.application_code IS '发布时 Application Code 快照';
COMMENT ON COLUMN system_catalog_release.release_version IS 'Application 内单调递增发布版本';
COMMENT ON COLUMN system_catalog_release.snapshot_schema_version IS '受控 Snapshot JSON 契约版本';
COMMENT ON COLUMN system_catalog_release.route_contract_version IS '发布时客户端 Route Registry 契约版本';
COMMENT ON COLUMN system_catalog_release.source_application_version IS '发布时 Application Draft 聚合版本';
COMMENT ON COLUMN system_catalog_release.snapshot_json IS '受控 Catalog Snapshot JSONB，不含 Path、Component、JavaScript 或数据库内部 ID';
COMMENT ON COLUMN system_catalog_release.node_count IS 'WEB 与 MOBILE 两个 Channel 的发布节点总数';
COMMENT ON COLUMN system_catalog_release.checksum IS '确定性 Snapshot SHA-256，用于 ETag 与 No-op Publish';
COMMENT ON COLUMN system_catalog_release.source_release_version IS 'Rollback 时被复制的历史版本；普通 Publish 为 NULL';
COMMENT ON COLUMN system_catalog_release.created_by IS '发布 Actor ID';
COMMENT ON COLUMN system_catalog_release.created_at IS 'UTC 发布时间点';
COMMENT ON COLUMN system_catalog_release.updated_by IS '插入时与发布 Actor 相同';
COMMENT ON COLUMN system_catalog_release.updated_at IS '插入时与发布时间相同';

CREATE OR REPLACE FUNCTION reject_system_catalog_release_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'system_catalog_release is immutable';
END;
$$;

CREATE TRIGGER trg_system_catalog_release_immutable
BEFORE UPDATE OR DELETE ON system_catalog_release
FOR EACH ROW EXECUTE FUNCTION reject_system_catalog_release_mutation();
