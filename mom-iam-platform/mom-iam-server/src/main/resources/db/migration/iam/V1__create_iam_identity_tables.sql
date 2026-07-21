-- P1.5 S02 IAM 身份与主体模型。Flyway 连接默认 Schema 为 mom_iam。
CREATE TABLE iam_user (
    id varchar(19) PRIMARY KEY,
    username varchar(120) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(200) NOT NULL,
    user_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    failed_login_count integer NOT NULL DEFAULT 0,
    locked_until timestamptz,
    password_change_required boolean NOT NULL DEFAULT true,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT uk_iam_user_username UNIQUE (username),
    CONSTRAINT ck_iam_user_type CHECK (user_type IN ('INTERNAL', 'SUPPLIER', 'CUSTOMER')),
    CONSTRAINT ck_iam_user_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_iam_user_failed_login_count CHECK (failed_login_count >= 0)
);
-- 支持 S07 按账号状态分页查询有效用户。
CREATE INDEX idx_iam_user_status ON iam_user(status) WHERE deleted = false;
-- 支持按 user_type 与状态执行 Client 入口用户筛选。
CREATE INDEX idx_iam_user_type_status ON iam_user(user_type, status) WHERE deleted = false;
-- 支持 S03 查找到期锁定账号并恢复认证尝试。
CREATE INDEX idx_iam_user_locked_until ON iam_user(locked_until) WHERE locked_until IS NOT NULL AND deleted = false;

COMMENT ON TABLE iam_user IS '统一保存内部员工、供应商和客户登录账号；不保存明文密码，登录、锁定和密码行为由后续 Slice 实现';
COMMENT ON COLUMN iam_user.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)，不得作为 JavaScript Number 暴露';
COMMENT ON COLUMN iam_user.username IS '全局唯一登录名；唯一约束包含逻辑删除记录，因此已删除用户名默认不得重新使用';
COMMENT ON COLUMN iam_user.password_hash IS '密码摘要，仅限 IAM 认证基础设施读取；禁止返回 API、写入日志或安全审计';
COMMENT ON COLUMN iam_user.display_name IS '用户展示名称，不作为登录标识或授权依据';
COMMENT ON COLUMN iam_user.user_type IS '账号用户类型，当前仅允许 INTERNAL、SUPPLIER、CUSTOMER；PDA 是客户端而不是用户类型';
COMMENT ON COLUMN iam_user.status IS '账号状态，当前仅允许 ENABLED、DISABLED；临时锁定由 locked_until 表达，不增加 LOCKED 状态';
COMMENT ON COLUMN iam_user.failed_login_count IS '连续登录失败次数，必须大于等于零；S03 才实现递增和清零行为';
COMMENT ON COLUMN iam_user.locked_until IS '账号临时锁定截止 UTC 时间；空值表示当前没有时间锁定';
COMMENT ON COLUMN iam_user.password_change_required IS '是否要求用户在后续认证流程中首次或重置后修改密码';
COMMENT ON COLUMN iam_user.last_login_at IS '最近一次成功登录 UTC 时间；S03 认证成功后维护';
COMMENT ON COLUMN iam_user.created_at IS '记录首次持久化 UTC 时间，由 Slice 01 审计处理器或 Flyway 显式写入';
COMMENT ON COLUMN iam_user.created_by IS '创建操作人，可保存用户 ID 或稳定 SYSTEM Actor Code，长度不限制为 19 位';
COMMENT ON COLUMN iam_user.updated_at IS '记录最近一次修改 UTC 时间，由 Slice 01 审计处理器维护';
COMMENT ON COLUMN iam_user.updated_by IS '最近修改操作人，可保存用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user.version IS 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始，每次受控更新递增';
COMMENT ON COLUMN iam_user.deleted IS '逻辑删除标识，false 表示有效、true 表示已删除；用户名唯一性跨删除记录保留';

-- 该唯一约束保证一个用户最多一条内部资料；employee_no 只对非空值唯一。
CREATE TABLE iam_internal_user_profile (
    id varchar(19) PRIMARY KEY,
    user_id varchar(19) NOT NULL,
    employee_no varchar(100),
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_internal_profile_user UNIQUE (user_id),
    CONSTRAINT fk_iam_internal_profile_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE RESTRICT
);
-- 防止两个内部用户资料重复占用同一非空员工编号。
CREATE UNIQUE INDEX uk_iam_internal_profile_employee_no
    ON iam_internal_user_profile(employee_no) WHERE employee_no IS NOT NULL;

COMMENT ON TABLE iam_internal_user_profile IS '保存 INTERNAL 用户的最小内部身份扩展资料；不复制组织、部门、岗位或工厂主数据';
COMMENT ON COLUMN iam_internal_user_profile.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_internal_user_profile.user_id IS '内部用户 ID；唯一约束保证一个用户最多一条内部资料，删除用户前必须先处理历史引用';
COMMENT ON COLUMN iam_internal_user_profile.employee_no IS '可选员工编号；非空值通过部分唯一索引防止重复';
COMMENT ON COLUMN iam_internal_user_profile.created_at IS '资料首次持久化 UTC 时间';
COMMENT ON COLUMN iam_internal_user_profile.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_internal_user_profile.updated_at IS '资料最近修改 UTC 时间';
COMMENT ON COLUMN iam_internal_user_profile.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_internal_user_profile.version IS 'MyBatis-Plus 乐观锁版本号';

CREATE TABLE iam_external_user_binding (
    id varchar(19) PRIMARY KEY,
    user_id varchar(19) NOT NULL,
    party_type varchar(20) NOT NULL,
    party_id varchar(19) NOT NULL,
    status varchar(20) NOT NULL,
    valid_from timestamptz,
    valid_until timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_external_binding_user UNIQUE (user_id),
    CONSTRAINT ck_iam_external_binding_party_type CHECK (party_type IN ('SUPPLIER', 'CUSTOMER')),
    CONSTRAINT ck_iam_external_binding_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_iam_external_binding_period CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from
    ),
    CONSTRAINT fk_iam_external_binding_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE RESTRICT
);
-- 支持 S07 按 Party 类型、Party ID 和状态查询外部账号。
CREATE INDEX idx_iam_external_binding_party
    ON iam_external_user_binding(party_type, party_id, status);

COMMENT ON TABLE iam_external_user_binding IS '保存外部账号唯一 Party Binding；用户类型与 Party 类型一致性由领域层校验，不建立到 MDM 或业务 Schema 的外键';
COMMENT ON COLUMN iam_external_user_binding.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_external_user_binding.user_id IS '外部用户 ID；唯一约束保证一个用户不能同时绑定多个供应商或客户主体';
COMMENT ON COLUMN iam_external_user_binding.party_type IS '外部主体类型，当前仅允许 SUPPLIER 或 CUSTOMER';
COMMENT ON COLUMN iam_external_user_binding.party_id IS '供应商或客户主体引用 ID；只保存跨服务引用，不复制主体业务字段且不建立跨 Schema 外键';
COMMENT ON COLUMN iam_external_user_binding.status IS '主体绑定状态，当前仅允许 ENABLED 或 DISABLED';
COMMENT ON COLUMN iam_external_user_binding.valid_from IS '可选绑定生效 UTC 时间';
COMMENT ON COLUMN iam_external_user_binding.valid_until IS '可选绑定失效 UTC 时间；存在开始时间时必须严格晚于 valid_from';
COMMENT ON COLUMN iam_external_user_binding.created_at IS '绑定记录首次持久化 UTC 时间';
COMMENT ON COLUMN iam_external_user_binding.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_external_user_binding.updated_at IS '绑定记录最近修改 UTC 时间';
COMMENT ON COLUMN iam_external_user_binding.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_external_user_binding.version IS 'MyBatis-Plus 乐观锁版本号';
