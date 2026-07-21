-- P1.5 S02 RBAC、Factory Scope、Mobile Access 与 OAuth Client Policy。
CREATE TABLE iam_role (
    id varchar(19) PRIMARY KEY,
    code varchar(100) NOT NULL,
    name varchar(200) NOT NULL,
    applicable_user_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    built_in boolean NOT NULL DEFAULT false,
    description varchar(1000),
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT uk_iam_role_code UNIQUE (code),
    CONSTRAINT ck_iam_role_user_type CHECK (applicable_user_type IN ('INTERNAL', 'SUPPLIER', 'CUSTOMER')),
    CONSTRAINT ck_iam_role_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
-- 支持按适用用户类型和状态列出可分配角色。
CREATE INDEX idx_iam_role_user_type_status
    ON iam_role(applicable_user_type, status) WHERE deleted = false;
COMMENT ON TABLE iam_role IS '定义适用于单一用户类型的职责集合；角色不绑定工厂，不支持继承、Deny 或 ABAC';
COMMENT ON COLUMN iam_role.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_role.code IS '角色编码，全局唯一，用于初始化、授权计算和管理关联；内置角色发布后不得直接改码';
COMMENT ON COLUMN iam_role.name IS '角色中文名称，用于管理界面展示';
COMMENT ON COLUMN iam_role.applicable_user_type IS '角色适用用户类型，仅允许 INTERNAL、SUPPLIER、CUSTOMER；跨类型分配由领域层拒绝';
COMMENT ON COLUMN iam_role.status IS '角色状态，仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_role.built_in IS '是否为系统内置角色；内置角色删除与关键修改保护由 S07 管理 API 实现';
COMMENT ON COLUMN iam_role.description IS '角色职责、授权范围和管理限制的完整说明';
COMMENT ON COLUMN iam_role.created_at IS '角色首次持久化 UTC 时间';
COMMENT ON COLUMN iam_role.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_role.updated_at IS '角色最近修改 UTC 时间';
COMMENT ON COLUMN iam_role.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_role.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN iam_role.deleted IS '逻辑删除标识；角色编码唯一性跨删除记录保留';

CREATE TABLE iam_permission (
    id varchar(19) PRIMARY KEY,
    code varchar(160) NOT NULL,
    name varchar(200) NOT NULL,
    domain_code varchar(60) NOT NULL,
    resource_code varchar(60) NOT NULL,
    action_code varchar(60) NOT NULL,
    risk_level varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    description varchar(1000) NOT NULL,
    built_in boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT uk_iam_permission_code UNIQUE (code),
    CONSTRAINT ck_iam_permission_code CHECK (
        code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$'
    ),
    CONSTRAINT ck_iam_permission_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_iam_permission_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
-- 支持 S07 读取有效 Permission 目录。
CREATE INDEX idx_iam_permission_status ON iam_permission(status) WHERE deleted = false;
COMMENT ON TABLE iam_permission IS '保存由代码和 Flyway 管理的系统 Permission 目录；管理 API 只能查看，不能动态创建或修改 Permission Code';
COMMENT ON COLUMN iam_permission.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_permission.code IS '全局唯一 Permission Code，格式固定为 domain:resource:action，不注册为 OAuth Scope';
COMMENT ON COLUMN iam_permission.name IS 'Permission 中文名称';
COMMENT ON COLUMN iam_permission.domain_code IS '权限所属业务或平台领域编码，例如 iam';
COMMENT ON COLUMN iam_permission.resource_code IS '权限保护的资源编码，例如 user、role、session';
COMMENT ON COLUMN iam_permission.action_code IS '权限动作编码，例如 read、create、revoke';
COMMENT ON COLUMN iam_permission.risk_level IS '风险等级，仅允许 LOW、MEDIUM、HIGH，用于管理确认和安全审计分级';
COMMENT ON COLUMN iam_permission.status IS 'Permission 状态，仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_permission.description IS 'Permission 的业务用途、风险和适用管理动作说明';
COMMENT ON COLUMN iam_permission.built_in IS '是否为系统内置 Permission；S02 初始化项均为 true';
COMMENT ON COLUMN iam_permission.created_at IS 'Permission 首次持久化 UTC 时间';
COMMENT ON COLUMN iam_permission.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_permission.updated_at IS 'Permission 最近修改 UTC 时间';
COMMENT ON COLUMN iam_permission.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_permission.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN iam_permission.deleted IS '逻辑删除标识；Permission Code 唯一性跨删除记录保留';

CREATE TABLE iam_user_role (
    id varchar(19) PRIMARY KEY,
    user_id varchar(19) NOT NULL,
    role_id varchar(19) NOT NULL,
    status varchar(20) NOT NULL,
    valid_from timestamptz,
    valid_until timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_user_role UNIQUE (user_id, role_id),
    CONSTRAINT ck_iam_user_role_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_iam_user_role_period CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from
    ),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_iam_user_role_role FOREIGN KEY (role_id) REFERENCES iam_role(id) ON DELETE RESTRICT
);
-- 支持 S04 按用户加载全部有效角色。
CREATE INDEX idx_iam_user_role_user_status ON iam_user_role(user_id, status);
-- 支持 S07 查询某角色的有效用户分配。
CREATE INDEX idx_iam_user_role_role_status ON iam_user_role(role_id, status);
COMMENT ON TABLE iam_user_role IS '保存用户角色分配；一个用户可拥有多个角色，但用户类型必须与角色 applicable_user_type 匹配';
COMMENT ON COLUMN iam_user_role.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_user_role.user_id IS '被分配角色的 IAM 用户 ID';
COMMENT ON COLUMN iam_user_role.role_id IS '分配给用户的 IAM 角色 ID';
COMMENT ON COLUMN iam_user_role.status IS '分配状态，仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_user_role.valid_from IS '可选分配生效 UTC 时间';
COMMENT ON COLUMN iam_user_role.valid_until IS '可选分配失效 UTC 时间，存在开始时间时必须严格晚于 valid_from';
COMMENT ON COLUMN iam_user_role.created_at IS '分配关系首次持久化 UTC 时间';
COMMENT ON COLUMN iam_user_role.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_role.updated_at IS '分配关系最近修改 UTC 时间';
COMMENT ON COLUMN iam_user_role.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_role.version IS 'MyBatis-Plus 乐观锁版本号';

CREATE TABLE iam_role_permission (
    id varchar(19) PRIMARY KEY,
    role_id varchar(19) NOT NULL,
    permission_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT uk_iam_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_iam_role_permission_role FOREIGN KEY (role_id) REFERENCES iam_role(id) ON DELETE RESTRICT,
    CONSTRAINT fk_iam_role_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission(id) ON DELETE RESTRICT
);
-- 支持按 Permission 反查使用该能力的角色。
CREATE INDEX idx_iam_role_permission_permission ON iam_role_permission(permission_id);
COMMENT ON TABLE iam_role_permission IS '保存角色与 Permission 的纯创建关系；不支持 Deny、优先级或角色继承';
COMMENT ON COLUMN iam_role_permission.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_role_permission.role_id IS '角色 ID；关系删除前需显式处理，避免物理级联丢失配置历史';
COMMENT ON COLUMN iam_role_permission.permission_id IS 'Permission ID；同一角色与 Permission 组合全局唯一';
COMMENT ON COLUMN iam_role_permission.created_at IS '关系首次创建 UTC 时间；删除后重建产生新记录';
COMMENT ON COLUMN iam_role_permission.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';

CREATE TABLE iam_user_application (
    id varchar(19) PRIMARY KEY,
    user_id varchar(19) NOT NULL,
    application_code varchar(100) NOT NULL,
    status varchar(20) NOT NULL,
    valid_from timestamptz,
    valid_until timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_user_application UNIQUE (user_id, application_code),
    CONSTRAINT ck_iam_user_application_code CHECK (application_code = 'MOM_MOBILE_PDA'),
    CONSTRAINT ck_iam_user_application_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_iam_user_application_period CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from
    ),
    CONSTRAINT fk_iam_user_application_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE RESTRICT
);
-- 支持登录时检查用户级 Mobile Access 是否有效。
CREATE INDEX idx_iam_user_application_user_status ON iam_user_application(user_id, status);
COMMENT ON TABLE iam_user_application IS '保存需要用户级单独授权的应用访问能力；P1.5 仅用于 INTERNAL 用户的 MOM_MOBILE_PDA';
COMMENT ON COLUMN iam_user_application.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_user_application.user_id IS '获得应用级访问能力的 IAM 用户 ID';
COMMENT ON COLUMN iam_user_application.application_code IS '用户级应用授权编码；P1.5 仅允许 MOM_MOBILE_PDA，Web Portal 基本访问不通过该表模拟业务 Permission';
COMMENT ON COLUMN iam_user_application.status IS '应用访问状态，仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_user_application.valid_from IS '可选应用访问生效 UTC 时间';
COMMENT ON COLUMN iam_user_application.valid_until IS '可选应用访问失效 UTC 时间，存在开始时间时必须严格晚于 valid_from';
COMMENT ON COLUMN iam_user_application.created_at IS '应用授权首次持久化 UTC 时间';
COMMENT ON COLUMN iam_user_application.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_application.updated_at IS '应用授权最近修改 UTC 时间';
COMMENT ON COLUMN iam_user_application.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_application.version IS 'MyBatis-Plus 乐观锁版本号';

CREATE TABLE iam_user_factory_scope (
    id varchar(19) PRIMARY KEY,
    user_id varchar(19) NOT NULL,
    factory_id varchar(19) NOT NULL,
    status varchar(20) NOT NULL,
    valid_from timestamptz,
    valid_until timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_user_factory_scope UNIQUE (user_id, factory_id),
    CONSTRAINT ck_iam_user_factory_scope_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_iam_user_factory_scope_period CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from
    ),
    CONSTRAINT fk_iam_user_factory_scope_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE RESTRICT
);
-- 支持 S04 按用户加载有效 Factory Scope。
CREATE INDEX idx_iam_user_factory_scope_user_status
    ON iam_user_factory_scope(user_id, status);
COMMENT ON TABLE iam_user_factory_scope IS '保存用户可访问的 MDM Factory ID；不支持 ALL_FACTORIES、组织树继承或按工厂分配角色';
COMMENT ON COLUMN iam_user_factory_scope.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_user_factory_scope.user_id IS '获得 Factory Scope 的 IAM 用户 ID';
COMMENT ON COLUMN iam_user_factory_scope.factory_id IS 'MDM Factory 引用 ID；不建立跨 Schema 外键，不复制工厂名称或组织属性';
COMMENT ON COLUMN iam_user_factory_scope.status IS 'Factory Scope 状态，仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_user_factory_scope.valid_from IS '可选 Scope 生效 UTC 时间';
COMMENT ON COLUMN iam_user_factory_scope.valid_until IS '可选 Scope 失效 UTC 时间，存在开始时间时必须严格晚于 valid_from';
COMMENT ON COLUMN iam_user_factory_scope.created_at IS 'Factory Scope 首次持久化 UTC 时间';
COMMENT ON COLUMN iam_user_factory_scope.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_factory_scope.updated_at IS 'Factory Scope 最近修改 UTC 时间';
COMMENT ON COLUMN iam_user_factory_scope.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_factory_scope.version IS 'MyBatis-Plus 乐观锁版本号';

CREATE TABLE iam_oauth_client_policy (
    id varchar(19) PRIMARY KEY,
    client_id varchar(100) NOT NULL,
    application_code varchar(100) NOT NULL,
    channel varchar(20) NOT NULL,
    allowed_user_type varchar(20) NOT NULL,
    mobile_access_required boolean NOT NULL DEFAULT false,
    status varchar(20) NOT NULL,
    description varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_client_policy_client UNIQUE (client_id),
    CONSTRAINT uk_iam_client_policy_application UNIQUE (application_code),
    CONSTRAINT ck_iam_client_policy_channel CHECK (channel IN ('WEB', 'MOBILE')),
    CONSTRAINT ck_iam_client_policy_user_type CHECK (allowed_user_type IN ('INTERNAL', 'SUPPLIER', 'CUSTOMER')),
    CONSTRAINT ck_iam_client_policy_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_iam_client_policy_matrix CHECK (
        (client_id = 'mom-admin-web' AND application_code = 'MOM_ADMIN'
            AND channel = 'WEB' AND allowed_user_type = 'INTERNAL' AND mobile_access_required = false)
        OR
        (client_id = 'mom-supplier-web' AND application_code = 'MOM_SUPPLIER_PORTAL'
            AND channel = 'WEB' AND allowed_user_type = 'SUPPLIER' AND mobile_access_required = false)
        OR
        (client_id = 'mom-customer-web' AND application_code = 'MOM_CUSTOMER_PORTAL'
            AND channel = 'WEB' AND allowed_user_type = 'CUSTOMER' AND mobile_access_required = false)
        OR
        (client_id = 'mom-mobile-pda' AND application_code = 'MOM_MOBILE_PDA'
            AND channel = 'MOBILE' AND allowed_user_type = 'INTERNAL' AND mobile_access_required = true)
    )
);
COMMENT ON TABLE iam_oauth_client_policy IS '保存 MOM 应用级 Client 访问策略；redirect_uri、grant_types、scopes、client_settings、token_settings 仍属于官方 Registered Client Store';
COMMENT ON COLUMN iam_oauth_client_policy.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_oauth_client_policy.client_id IS 'OAuth Client ID，唯一并与未来官方 oauth2_registered_client.client_id 对齐';
COMMENT ON COLUMN iam_oauth_client_policy.application_code IS 'MOM 应用稳定编码，唯一映射一个 Client Policy';
COMMENT ON COLUMN iam_oauth_client_policy.channel IS '客户端渠道，仅允许 WEB 或 MOBILE';
COMMENT ON COLUMN iam_oauth_client_policy.allowed_user_type IS '允许登录该 Client 的唯一用户类型';
COMMENT ON COLUMN iam_oauth_client_policy.mobile_access_required IS '是否要求用户同时具备 iam_user_application 中的 MOM_MOBILE_PDA 授权；仅 mom-mobile-pda 为 true';
COMMENT ON COLUMN iam_oauth_client_policy.status IS 'Client Policy 状态，仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_oauth_client_policy.description IS 'Client 对应应用、用户类型、渠道和额外访问条件的完整说明';
COMMENT ON COLUMN iam_oauth_client_policy.created_at IS 'Client Policy 首次持久化 UTC 时间';
COMMENT ON COLUMN iam_oauth_client_policy.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_oauth_client_policy.updated_at IS 'Client Policy 最近修改 UTC 时间';
COMMENT ON COLUMN iam_oauth_client_policy.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_oauth_client_policy.version IS 'MyBatis-Plus 乐观锁版本号';
