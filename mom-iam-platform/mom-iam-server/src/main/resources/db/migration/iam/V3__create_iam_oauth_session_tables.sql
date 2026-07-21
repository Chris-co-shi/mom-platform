-- Spring Security 7.1 Authorization Server JDBC Schema 来源：
-- spring-projects/spring-security 7.1.0 标签 oauth2/oauth2-authorization-server 模块。
-- 按官方 PostgreSQL 说明将 timestamp 改为 timestamptz，将 blob 改为 text；列名和 JDBC 所需结构保持兼容。
CREATE TABLE oauth2_registered_client (
    id varchar(100) NOT NULL,
    client_id varchar(100) NOT NULL,
    client_id_issued_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret varchar(200),
    client_secret_expires_at timestamptz,
    client_name varchar(200) NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types varchar(1000) NOT NULL,
    redirect_uris varchar(1000),
    post_logout_redirect_uris varchar(1000),
    scopes varchar(1000) NOT NULL,
    client_settings varchar(2000) NOT NULL,
    token_settings varchar(2000) NOT NULL,
    CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id)
);
-- 强化官方 Repository 对 Client ID 唯一性的应用级检查，避免并发重复注册。
CREATE UNIQUE INDEX uk_oauth2_registered_client_client_id ON oauth2_registered_client(client_id);

COMMENT ON TABLE oauth2_registered_client IS 'Spring Security 7.1 Authorization Server 官方 JDBC Registered Client 协议表；S03 才写入环境相关 Redirect URI 和正式 Public Client 注册';
COMMENT ON COLUMN oauth2_registered_client.id IS '官方 RegisteredClient 持久化标识，保持 Spring Security JDBC Repository 兼容';
COMMENT ON COLUMN oauth2_registered_client.client_id IS 'OAuth Client Identifier，协议层唯一；MOM 应用访问策略另存于 iam_oauth_client_policy';
COMMENT ON COLUMN oauth2_registered_client.client_id_issued_at IS 'Client Identifier 签发 UTC 时间，按官方 PostgreSQL 建议使用 timestamptz';
COMMENT ON COLUMN oauth2_registered_client.client_secret IS '可选客户端密钥摘要；P1.5 四个 Client 均为 Public Client，S02 不写入密钥';
COMMENT ON COLUMN oauth2_registered_client.client_secret_expires_at IS '可选客户端密钥过期 UTC 时间';
COMMENT ON COLUMN oauth2_registered_client.client_name IS '授权和同意页面可展示的 Client 名称';
COMMENT ON COLUMN oauth2_registered_client.client_authentication_methods IS '官方 JDBC 序列化的 Client Authentication Method 集合；Public Client 使用 none';
COMMENT ON COLUMN oauth2_registered_client.authorization_grant_types IS '官方 JDBC 序列化的 Authorization Grant Type 集合';
COMMENT ON COLUMN oauth2_registered_client.redirect_uris IS '精确匹配的环境相关 Redirect URI 集合；S03 配置，S02 不初始化';
COMMENT ON COLUMN oauth2_registered_client.post_logout_redirect_uris IS '精确匹配的 OIDC 登出回调 URI 集合；S03 配置';
COMMENT ON COLUMN oauth2_registered_client.scopes IS 'OAuth/OIDC Scope 集合，不保存 MOM 业务 Permission';
COMMENT ON COLUMN oauth2_registered_client.client_settings IS 'Spring Security 官方序列化 ClientSettings，例如 PKCE 和 consent 配置';
COMMENT ON COLUMN oauth2_registered_client.token_settings IS 'Spring Security 官方序列化 TokenSettings，例如 Token TTL 和 Refresh 复用策略';

CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000),
    attributes text,
    state varchar(500),
    authorization_code_value text,
    authorization_code_issued_at timestamptz,
    authorization_code_expires_at timestamptz,
    authorization_code_metadata text,
    access_token_value text,
    access_token_issued_at timestamptz,
    access_token_expires_at timestamptz,
    access_token_metadata text,
    access_token_type varchar(100),
    access_token_scopes varchar(1000),
    oidc_id_token_value text,
    oidc_id_token_issued_at timestamptz,
    oidc_id_token_expires_at timestamptz,
    oidc_id_token_metadata text,
    refresh_token_value text,
    refresh_token_issued_at timestamptz,
    refresh_token_expires_at timestamptz,
    refresh_token_metadata text,
    user_code_value text,
    user_code_issued_at timestamptz,
    user_code_expires_at timestamptz,
    user_code_metadata text,
    device_code_value text,
    device_code_issued_at timestamptz,
    device_code_expires_at timestamptz,
    device_code_metadata text,
    CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id)
);
-- 支持按 Registered Client 查询协议授权记录。
CREATE INDEX idx_oauth2_authorization_registered_client
    ON oauth2_authorization(registered_client_id);
-- 支持按 Principal 查询协议授权记录。
CREATE INDEX idx_oauth2_authorization_principal
    ON oauth2_authorization(principal_name);

COMMENT ON TABLE oauth2_authorization IS 'Spring Security 7.1 Authorization Server 官方 JDBC OAuth2Authorization 协议表；可能包含协议 Token 值，仅 IAM 协议基础设施访问，禁止业务日志输出';
COMMENT ON COLUMN oauth2_authorization.id IS '官方 OAuth2Authorization 持久化标识';
COMMENT ON COLUMN oauth2_authorization.registered_client_id IS '关联 Registered Client 的官方持久化 ID；保持 JDBC Repository 查询兼容';
COMMENT ON COLUMN oauth2_authorization.principal_name IS '资源所有者或客户端 Principal 名称';
COMMENT ON COLUMN oauth2_authorization.authorization_grant_type IS '产生该授权记录的 OAuth2 Grant Type';
COMMENT ON COLUMN oauth2_authorization.authorized_scopes IS '用户或客户端已授权的 OAuth/OIDC Scope 集合';
COMMENT ON COLUMN oauth2_authorization.attributes IS '官方 JDBC 序列化授权属性；PostgreSQL 按官方建议使用 text 替代 blob';
COMMENT ON COLUMN oauth2_authorization.state IS '授权请求 state 协议值';
COMMENT ON COLUMN oauth2_authorization.authorization_code_value IS 'Authorization Code 协议值；禁止进入日志、安全审计或业务 API';
COMMENT ON COLUMN oauth2_authorization.authorization_code_issued_at IS 'Authorization Code 签发 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_expires_at IS 'Authorization Code 过期 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_metadata IS '官方 JDBC 序列化 Authorization Code 元数据';
COMMENT ON COLUMN oauth2_authorization.access_token_value IS 'Access Token 协议值；禁止进入日志、安全审计或业务 API';
COMMENT ON COLUMN oauth2_authorization.access_token_issued_at IS 'Access Token 签发 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.access_token_expires_at IS 'Access Token 过期 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.access_token_metadata IS '官方 JDBC 序列化 Access Token 元数据';
COMMENT ON COLUMN oauth2_authorization.access_token_type IS 'Access Token 类型，例如 Bearer';
COMMENT ON COLUMN oauth2_authorization.access_token_scopes IS 'Access Token 实际包含的 OAuth Scope 集合';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_value IS 'OIDC ID Token 协议值；只供 OIDC Client 身份信息使用，禁止调用业务 API';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_issued_at IS 'OIDC ID Token 签发 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_expires_at IS 'OIDC ID Token 过期 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_metadata IS '官方 JDBC 序列化 OIDC ID Token 元数据与 Claims';
COMMENT ON COLUMN oauth2_authorization.refresh_token_value IS 'Spring Security 协议表中的 Refresh Token 值；MOM 自有轮换安全状态另存 HMAC 摘要，任何值均禁止日志输出';
COMMENT ON COLUMN oauth2_authorization.refresh_token_issued_at IS 'Spring Security 协议 Refresh Token 签发 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_expires_at IS 'Spring Security 协议 Refresh Token 过期 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_metadata IS '官方 JDBC 序列化 Refresh Token 元数据';
COMMENT ON COLUMN oauth2_authorization.user_code_value IS 'Device Authorization Flow 用户码协议值；P1.5 当前不启用该流程';
COMMENT ON COLUMN oauth2_authorization.user_code_issued_at IS '用户码签发 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.user_code_expires_at IS '用户码过期 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.user_code_metadata IS '官方 JDBC 序列化用户码元数据';
COMMENT ON COLUMN oauth2_authorization.device_code_value IS 'Device Authorization Flow 设备码协议值；P1.5 当前不启用该流程';
COMMENT ON COLUMN oauth2_authorization.device_code_issued_at IS '设备码签发 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.device_code_expires_at IS '设备码过期 UTC 时间';
COMMENT ON COLUMN oauth2_authorization.device_code_metadata IS '官方 JDBC 序列化设备码元数据';

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    CONSTRAINT pk_oauth2_authorization_consent
        PRIMARY KEY (registered_client_id, principal_name)
);

COMMENT ON TABLE oauth2_authorization_consent IS 'Spring Security 7.1 Authorization Server 官方 JDBC 授权同意协议表，按 Client 与 Principal 保存已同意 Authority';
COMMENT ON COLUMN oauth2_authorization_consent.registered_client_id IS '关联 Registered Client 的官方持久化 ID';
COMMENT ON COLUMN oauth2_authorization_consent.principal_name IS '作出授权同意的资源所有者 Principal 名称';
COMMENT ON COLUMN oauth2_authorization_consent.authorities IS '官方 JDBC 序列化的已同意 OAuth/OIDC Authority 集合';

CREATE TABLE iam_user_session (
    id varchar(19) PRIMARY KEY,
    user_id varchar(19) NOT NULL,
    client_id varchar(100) NOT NULL,
    channel varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    login_at timestamptz NOT NULL,
    last_refresh_at timestamptz,
    absolute_expires_at timestamptz NOT NULL,
    latest_access_token_expires_at timestamptz,
    ip_address varchar(64),
    user_agent varchar(1000),
    device_name varchar(200),
    revoked_at timestamptz,
    revoked_by varchar(128),
    revoke_reason varchar(1000),
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_user_session_channel CHECK (channel IN ('WEB', 'MOBILE')),
    CONSTRAINT ck_iam_user_session_status CHECK (
        status IN ('ACTIVE', 'REVOKED', 'COMPROMISED', 'EXPIRED')
    ),
    CONSTRAINT ck_iam_user_session_absolute_expiry CHECK (absolute_expires_at > login_at),
    CONSTRAINT fk_iam_user_session_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_iam_user_session_client_policy FOREIGN KEY (client_id)
        REFERENCES iam_oauth_client_policy(client_id) ON DELETE RESTRICT
);
-- 支持用户 Session 列表和批量撤销查询。
CREATE INDEX idx_iam_user_session_user_status ON iam_user_session(user_id, status);
-- 支持 Client 禁用时查找需撤销的 Session。
CREATE INDEX idx_iam_user_session_client_status ON iam_user_session(client_id, status);
-- 支持 S05 扫描达到绝对过期时间的 Session。
CREATE INDEX idx_iam_user_session_absolute_expires ON iam_user_session(absolute_expires_at);

COMMENT ON TABLE iam_user_session IS '用户授权 Session 权威记录；主键未来直接作为 JWT sid，不复制角色、Permission、Factory IDs、Party 详情或 Access Token';
COMMENT ON COLUMN iam_user_session.id IS 'MOM String 技术主键，未来直接作为 JWT sid，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_user_session.user_id IS 'Session 所属 IAM 用户 ID';
COMMENT ON COLUMN iam_user_session.client_id IS 'Session 所属 Client ID，引用 iam_oauth_client_policy 而非复制 Registered Client 协议配置';
COMMENT ON COLUMN iam_user_session.channel IS 'Session 渠道，仅允许 WEB 或 MOBILE，领域层还需与 Client Policy channel 一致';
COMMENT ON COLUMN iam_user_session.status IS 'Session 状态，仅允许 ACTIVE、REVOKED、COMPROMISED、EXPIRED';
COMMENT ON COLUMN iam_user_session.login_at IS 'Session 登录建立 UTC 时间';
COMMENT ON COLUMN iam_user_session.last_refresh_at IS '最近成功 Refresh 的 UTC 时间；S05 维护';
COMMENT ON COLUMN iam_user_session.absolute_expires_at IS 'Session 绝对过期 UTC 时间，必须晚于 login_at；Refresh 不延长该时间';
COMMENT ON COLUMN iam_user_session.latest_access_token_expires_at IS '最近一次签发 Access Token 的过期 UTC 时间，不保存 Access Token 本身';
COMMENT ON COLUMN iam_user_session.ip_address IS '登录来源 IP 文本，支持 IPv4、IPv6 或受控代理解析结果';
COMMENT ON COLUMN iam_user_session.user_agent IS '登录 User-Agent 摘要；不得包含 Authorization Header 或 Token';
COMMENT ON COLUMN iam_user_session.device_name IS '可选用户可识别设备名称';
COMMENT ON COLUMN iam_user_session.revoked_at IS 'Session 撤销 UTC 时间';
COMMENT ON COLUMN iam_user_session.revoked_by IS '撤销操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_session.revoke_reason IS '受控撤销原因，不得包含密码、Token 或 Authorization Header';
COMMENT ON COLUMN iam_user_session.created_at IS 'Session 记录首次持久化 UTC 时间';
COMMENT ON COLUMN iam_user_session.created_by IS '创建操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_session.updated_at IS 'Session 记录最近修改 UTC 时间';
COMMENT ON COLUMN iam_user_session.updated_by IS '最近修改操作人用户 ID 或稳定 SYSTEM Actor Code';
COMMENT ON COLUMN iam_user_session.version IS 'MyBatis-Plus 乐观锁版本号，用于 Session 状态并发控制';

CREATE TABLE iam_refresh_token (
    id varchar(19) PRIMARY KEY,
    session_id varchar(19) NOT NULL,
    token_digest varchar(128) NOT NULL,
    sequence_no bigint NOT NULL,
    status varchar(20) NOT NULL,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    replaced_by_token_id varchar(19),
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_iam_refresh_token_digest UNIQUE (token_digest),
    CONSTRAINT uk_iam_refresh_token_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT ck_iam_refresh_token_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_iam_refresh_token_status CHECK (
        status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT ck_iam_refresh_token_expiry CHECK (expires_at > issued_at),
    CONSTRAINT fk_iam_refresh_token_session FOREIGN KEY (session_id)
        REFERENCES iam_user_session(id) ON DELETE RESTRICT,
    CONSTRAINT fk_iam_refresh_token_replacement FOREIGN KEY (replaced_by_token_id)
        REFERENCES iam_refresh_token(id) ON DELETE RESTRICT
);
-- 该部分唯一索引是 S05 Refresh Rotation 的数据库并发底线：每个 Session 最多一个 ACTIVE Token。
CREATE UNIQUE INDEX uk_iam_refresh_token_one_active_per_session
    ON iam_refresh_token(session_id) WHERE status = 'ACTIVE';
-- 支持 S05 扫描和清理已经过期的 Refresh Token 状态。
CREATE INDEX idx_iam_refresh_token_expires ON iam_refresh_token(expires_at);

COMMENT ON TABLE iam_refresh_token IS '保存 Refresh Token 轮换链安全状态；不保存明文 Token，不继承完整 BaseEntity，S05 才实现生成、HMAC、行锁、Rotation 和重放检测';
COMMENT ON COLUMN iam_refresh_token.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_refresh_token.session_id IS '所属用户授权 Session ID；与 sequence_no 共同标识轮换链位置';
COMMENT ON COLUMN iam_refresh_token.token_digest IS 'Refresh Token 的 HMAC-SHA-256 摘要而非明文；Server Pepper 仅存在安全配置中，绝不存入数据库';
COMMENT ON COLUMN iam_refresh_token.sequence_no IS 'Session 内从 1 开始递增的轮换序号，必须大于零';
COMMENT ON COLUMN iam_refresh_token.status IS 'Token 状态，仅允许 ACTIVE、ROTATED、REVOKED、EXPIRED';
COMMENT ON COLUMN iam_refresh_token.issued_at IS 'Refresh Token 签发 UTC 时间，由 S05 领域逻辑显式设置';
COMMENT ON COLUMN iam_refresh_token.expires_at IS 'Refresh Token 过期 UTC 时间，必须晚于 issued_at 且不得超过 Session 绝对过期时间';
COMMENT ON COLUMN iam_refresh_token.consumed_at IS 'Refresh Token 被成功消费的 UTC 时间，未消费时为空';
COMMENT ON COLUMN iam_refresh_token.replaced_by_token_id IS '成功轮换后产生的后继 Token ID，自引用用于安全调查，不保存 Token 明文';
COMMENT ON COLUMN iam_refresh_token.revoked_at IS 'Refresh Token 撤销 UTC 时间';
COMMENT ON COLUMN iam_refresh_token.created_at IS '安全状态记录首次持久化 UTC 时间；不表示 Token 明文创建或可恢复';
