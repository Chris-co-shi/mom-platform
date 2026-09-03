-- Mini Auth V1 核心认证授权表。
-- Flyway 默认 Schema 由 spring.flyway.default-schema 决定；本脚本不硬编码物理 Schema。
-- 当前只建立 User → Role → Permission 最小关系，不包含 OAuth/OIDC、Session、Refresh Token、Factory/Party Scope。
-- 按 ADR-026，MOM 自主业务表和关系表不建立物理外键，引用完整性由 Application、本地事务、唯一约束和测试保证。

CREATE TABLE auth_user (
    id varchar(19) NOT NULL,
    username varchar(120) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(200) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_auth_user PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_username UNIQUE (username)
);

CREATE INDEX ix_auth_user_enabled
    ON auth_user(enabled)
    WHERE deleted = false;

COMMENT ON TABLE auth_user IS 'Mini Auth V1 登录账号；保存账号标识、密码摘要、展示名称和启停状态，不承载角色、组织、员工、Session 或 Token 数据';
COMMENT ON COLUMN auth_user.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN auth_user.username IS '全局唯一登录账号；应用层负责 trim 和 lowercase 规范化，逻辑删除后默认不复用原账号';
COMMENT ON COLUMN auth_user.password_hash IS '密码摘要，仅认证流程可读取；禁止返回 API、写入日志、Trace 或审计事件';
COMMENT ON COLUMN auth_user.display_name IS '账号展示名称，仅用于界面显示，不作为登录标识或授权依据';
COMMENT ON COLUMN auth_user.enabled IS '账号是否允许认证；true 可登录，false 禁止登录';
COMMENT ON COLUMN auth_user.created_at IS '记录首次持久化 UTC 时间，由服务端审计处理器或 Flyway 显式写入';
COMMENT ON COLUMN auth_user.created_by IS '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN auth_user.updated_at IS '最近一次持久化修改 UTC 时间，由服务端审计处理器维护';
COMMENT ON COLUMN auth_user.updated_by IS '最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN auth_user.version IS 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始';
COMMENT ON COLUMN auth_user.deleted IS 'MyBatis-Plus 逻辑删除标识，false 有效，true 已删除';

CREATE TABLE auth_role (
    id varchar(19) NOT NULL,
    code varchar(100) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_auth_role PRIMARY KEY (id),
    CONSTRAINT uk_auth_role_code UNIQUE (code)
);

CREATE INDEX ix_auth_role_enabled
    ON auth_role(enabled)
    WHERE deleted = false;

COMMENT ON TABLE auth_role IS 'Mini Auth V1 角色；角色表示职责集合，通过关系表关联用户和 Permission';
COMMENT ON COLUMN auth_role.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN auth_role.code IS '全局唯一角色编码，用于稳定识别角色；逻辑删除后默认不复用原编码';
COMMENT ON COLUMN auth_role.name IS '角色展示名称';
COMMENT ON COLUMN auth_role.description IS '角色职责说明，可为空';
COMMENT ON COLUMN auth_role.enabled IS '角色是否参与授权计算；true 有效，false 停用';
COMMENT ON COLUMN auth_role.created_at IS '角色首次持久化 UTC 时间';
COMMENT ON COLUMN auth_role.created_by IS '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN auth_role.updated_at IS '角色最近修改 UTC 时间';
COMMENT ON COLUMN auth_role.updated_by IS '最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN auth_role.version IS 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始';
COMMENT ON COLUMN auth_role.deleted IS 'MyBatis-Plus 逻辑删除标识，false 有效，true 已删除';

CREATE TABLE auth_permission (
    id varchar(19) NOT NULL,
    code varchar(160) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_auth_permission PRIMARY KEY (id),
    CONSTRAINT uk_auth_permission_code UNIQUE (code)
);

CREATE INDEX ix_auth_permission_enabled
    ON auth_permission(enabled)
    WHERE deleted = false;

COMMENT ON TABLE auth_permission IS 'Mini Auth V1 权限目录；Permission Code 最终转换为 Spring Security GrantedAuthority';
COMMENT ON COLUMN auth_permission.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN auth_permission.code IS '全局唯一权限编码，推荐使用 domain:resource:action，例如 mes:work-order:create';
COMMENT ON COLUMN auth_permission.name IS '权限展示名称';
COMMENT ON COLUMN auth_permission.description IS '权限用途说明，可为空';
COMMENT ON COLUMN auth_permission.enabled IS '权限是否参与授权计算；true 有效，false 停用';
COMMENT ON COLUMN auth_permission.created_at IS '权限首次持久化 UTC 时间';
COMMENT ON COLUMN auth_permission.created_by IS '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN auth_permission.updated_at IS '权限最近修改 UTC 时间';
COMMENT ON COLUMN auth_permission.updated_by IS '最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN auth_permission.version IS 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始';
COMMENT ON COLUMN auth_permission.deleted IS 'MyBatis-Plus 逻辑删除标识，false 有效，true 已删除';

CREATE TABLE auth_user_role (
    id varchar(19) NOT NULL,
    user_id varchar(19) NOT NULL,
    role_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_auth_user_role PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_role_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX ix_auth_user_role_user
    ON auth_user_role(user_id);
CREATE INDEX ix_auth_user_role_role
    ON auth_user_role(role_id);

COMMENT ON TABLE auth_user_role IS '用户与角色关系；一个用户可拥有多个角色，同一用户与角色组合只允许一条记录；不建立物理外键';
COMMENT ON COLUMN auth_user_role.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN auth_user_role.user_id IS 'auth_user.id 引用；存在性和删除保护由 Application Service 保证';
COMMENT ON COLUMN auth_user_role.role_id IS 'auth_role.id 引用；存在性和删除保护由 Application Service 保证';
COMMENT ON COLUMN auth_user_role.created_at IS '关系首次创建 UTC 时间；关系移除采用物理删除，重新分配时创建新记录';
COMMENT ON COLUMN auth_user_role.created_by IS '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';

CREATE TABLE auth_role_permission (
    id varchar(19) NOT NULL,
    role_id varchar(19) NOT NULL,
    permission_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_auth_role_permission PRIMARY KEY (id),
    CONSTRAINT uk_auth_role_permission_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX ix_auth_role_permission_role
    ON auth_role_permission(role_id);
CREATE INDEX ix_auth_role_permission_permission
    ON auth_role_permission(permission_id);

COMMENT ON TABLE auth_role_permission IS '角色与权限关系；同一角色与 Permission 组合只允许一条记录；不建立物理外键';
COMMENT ON COLUMN auth_role_permission.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN auth_role_permission.role_id IS 'auth_role.id 引用；存在性和删除保护由 Application Service 保证';
COMMENT ON COLUMN auth_role_permission.permission_id IS 'auth_permission.id 引用；存在性和删除保护由 Application Service 保证';
COMMENT ON COLUMN auth_role_permission.created_at IS '关系首次创建 UTC 时间；关系移除采用物理删除，重新授权时创建新记录';
COMMENT ON COLUMN auth_role_permission.created_by IS '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code';
