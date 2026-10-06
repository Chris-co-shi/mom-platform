-- Permission Resource 是 Auth 权限目录元数据；运行时授权仍使用 auth_permission.code。
-- 旧数据只迁移已发布的六条权限。发现其他历史编码时先人工确认归属，禁止自动猜测。

CREATE TABLE auth_permission_resource (
    id varchar(19) NOT NULL,
    domain_code varchar(32) NOT NULL,
    resource_code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    sort_order integer NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_auth_permission_resource PRIMARY KEY (id),
    CONSTRAINT uk_auth_permission_resource_domain_code UNIQUE (domain_code, resource_code),
    CONSTRAINT ck_auth_permission_resource_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_auth_permission_resource_sort_non_negative CHECK (sort_order >= 0),
    CONSTRAINT ck_auth_permission_resource_domain_format CHECK (domain_code ~ '^[A-Z][A-Z0-9_-]*$'),
    CONSTRAINT ck_auth_permission_resource_code_format CHECK (resource_code ~ '^[A-Z][A-Z0-9_-]*$')
);

COMMENT ON TABLE auth_permission_resource IS 'Auth 权限目录中的业务授权资源，不是菜单、路由或运行时 authority';
COMMENT ON COLUMN auth_permission_resource.id IS 'MOM String 技术主键';
COMMENT ON COLUMN auth_permission_resource.domain_code IS '授权域稳定编码，统一大写';
COMMENT ON COLUMN auth_permission_resource.resource_code IS '域内业务资源稳定编码，统一大写';
COMMENT ON COLUMN auth_permission_resource.name IS '资源展示名称，不参与权限判断';
COMMENT ON COLUMN auth_permission_resource.description IS '资源用途说明';
COMMENT ON COLUMN auth_permission_resource.sort_order IS '目录显示顺序，小值在前';
COMMENT ON COLUMN auth_permission_resource.enabled IS '是否允许新建或重新启用该资源下的权限，不级联修改现有权限';
COMMENT ON COLUMN auth_permission_resource.created_at IS '创建 UTC 时间';
COMMENT ON COLUMN auth_permission_resource.created_by IS '可信创建 Actor';
COMMENT ON COLUMN auth_permission_resource.updated_at IS '最近修改 UTC 时间';
COMMENT ON COLUMN auth_permission_resource.updated_by IS '最近修改 Actor';
COMMENT ON COLUMN auth_permission_resource.version IS '乐观锁版本号';
COMMENT ON COLUMN auth_permission_resource.deleted IS '逻辑删除标识，false 表示有效；资源被权限引用时禁止删除';

INSERT INTO auth_permission_resource (
    id, domain_code, resource_code, name, description, sort_order, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
) VALUES
    ('1000000000000000030', 'AUTH', 'USER', '用户管理', 'Auth 用户账号授权资源', 10, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false),
    ('1000000000000000031', 'AUTH', 'ROLE', '角色管理', 'Auth 角色授权资源', 20, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false),
    ('1000000000000000032', 'AUTH', 'PERMISSION', '权限管理', 'Auth 权限与资源目录授权资源', 30, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false);

ALTER TABLE auth_permission
    ADD COLUMN resource_id varchar(19),
    ADD COLUMN action_code varchar(60);

-- 非已发布的编码没有可确认的资源归属；事务回滚比产生错误授权元数据安全。
DO $$
DECLARE unknown_count bigint;
BEGIN
    SELECT count(*) INTO unknown_count FROM auth_permission
    WHERE code NOT IN (
        'auth:user:read', 'auth:user:write',
        'auth:role:read', 'auth:role:write',
        'auth:permission:read', 'auth:permission:write'
    );
    IF unknown_count > 0 THEN
        RAISE EXCEPTION 'V5 permission resource migration needs explicit mapping for % historical permissions', unknown_count;
    END IF;
END $$;

UPDATE auth_permission SET
    resource_id = CASE split_part(code, ':', 2)
        WHEN 'user' THEN '1000000000000000030'
        WHEN 'role' THEN '1000000000000000031'
        WHEN 'permission' THEN '1000000000000000032'
    END,
    action_code = upper(split_part(code, ':', 3));

ALTER TABLE auth_permission
    ALTER COLUMN resource_id SET NOT NULL,
    ALTER COLUMN action_code SET NOT NULL,
    ADD CONSTRAINT uk_auth_permission_resource_action UNIQUE (resource_id, action_code),
    ADD CONSTRAINT ck_auth_permission_action_format CHECK (action_code ~ '^[A-Z][A-Z0-9_-]*$');

CREATE INDEX ix_auth_permission_resource_code
    ON auth_permission(resource_id, code, id) WHERE deleted = false;

COMMENT ON COLUMN auth_permission.resource_id IS '所属 Auth Permission Resource 技术主键，由 Application 校验存在性';
COMMENT ON COLUMN auth_permission.action_code IS '资源下的稳定动作编码，统一大写；完整 authority code 由服务端生成';
