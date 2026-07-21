-- P1.5 S02 追加型 IAM 安全审计事件。
CREATE TABLE iam_security_audit_event (
    id varchar(19) PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    event_category varchar(30) NOT NULL,
    risk_level varchar(20) NOT NULL,
    result varchar(20) NOT NULL,
    actor_type varchar(20) NOT NULL,
    actor_user_id varchar(19),
    actor_client_id varchar(100),
    target_type varchar(100),
    target_id varchar(128),
    session_id varchar(19),
    ip_address varchar(64),
    user_agent varchar(1000),
    reason_code varchar(100),
    reason_detail varchar(2000),
    change_summary jsonb,
    correlation_id varchar(128),
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_iam_security_audit_category CHECK (
        event_category IN ('AUTHENTICATION', 'ACCOUNT', 'AUTHORIZATION', 'SESSION', 'TOKEN', 'CLIENT', 'SECURITY')
    ),
    CONSTRAINT ck_iam_security_audit_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_iam_security_audit_result CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT ck_iam_security_audit_actor_type CHECK (
        actor_type IN ('USER', 'ADMIN', 'SYSTEM', 'ANONYMOUS')
    ),
    CONSTRAINT ck_iam_security_audit_change_summary CHECK (
        change_summary IS NULL OR jsonb_typeof(change_summary) = 'object'
    )
);
-- 支持按事件发生时间倒序分页和 180 天保留期扫描。
CREATE INDEX idx_iam_security_audit_occurred_at
    ON iam_security_audit_event(occurred_at DESC);
-- 支持按安全事件分类和时间范围调查。
CREATE INDEX idx_iam_security_audit_category_time
    ON iam_security_audit_event(event_category, occurred_at DESC);
-- 支持按操作用户和时间追踪安全操作。
CREATE INDEX idx_iam_security_audit_actor_time
    ON iam_security_audit_event(actor_user_id, occurred_at DESC)
    WHERE actor_user_id IS NOT NULL;
-- 支持按目标类型、目标 ID 和时间调查变更。
CREATE INDEX idx_iam_security_audit_target_time
    ON iam_security_audit_event(target_type, target_id, occurred_at DESC)
    WHERE target_type IS NOT NULL AND target_id IS NOT NULL;
-- 支持按 Session ID 聚合认证、Token 与撤销事件。
CREATE INDEX idx_iam_security_audit_session
    ON iam_security_audit_event(session_id)
    WHERE session_id IS NOT NULL;
-- 支持按 Correlation ID 关联安全事件、日志和 Trace。
CREATE INDEX idx_iam_security_audit_correlation
    ON iam_security_audit_event(correlation_id)
    WHERE correlation_id IS NOT NULL;

COMMENT ON TABLE iam_security_audit_event IS '追加保存 IAM 认证、账号、授权、Session、Token、Client 和综合安全事件；普通 Repository 只允许 INSERT，不使用逻辑删除或版本字段，默认保留 180 天的清理策略留给后续任务';
COMMENT ON COLUMN iam_security_audit_event.id IS 'MOM String 技术主键，数据库固定 varchar(19)';
COMMENT ON COLUMN iam_security_audit_event.event_type IS '稳定安全事件类型编码，例如 iam.authentication.login-failed；禁止使用空泛 unknown/default';
COMMENT ON COLUMN iam_security_audit_event.event_category IS '事件分类，仅允许 AUTHENTICATION、ACCOUNT、AUTHORIZATION、SESSION、TOKEN、CLIENT、SECURITY';
COMMENT ON COLUMN iam_security_audit_event.risk_level IS '事件风险等级，仅允许 LOW、MEDIUM、HIGH';
COMMENT ON COLUMN iam_security_audit_event.result IS '事件处理结果，仅允许 SUCCESS、FAILURE、DENIED';
COMMENT ON COLUMN iam_security_audit_event.actor_type IS '事件操作人类型，仅允许 USER、ADMIN、SYSTEM、ANONYMOUS';
COMMENT ON COLUMN iam_security_audit_event.actor_user_id IS '可选操作用户 ID；SYSTEM 或 ANONYMOUS 场景可为空，不建立外键以保留历史';
COMMENT ON COLUMN iam_security_audit_event.actor_client_id IS '可选 OAuth Client ID；不复制 Client Settings 或 Redirect URI';
COMMENT ON COLUMN iam_security_audit_event.target_type IS '可选目标对象类型，例如 USER、ROLE、SESSION、CLIENT';
COMMENT ON COLUMN iam_security_audit_event.target_id IS '可选目标对象标识；可保存 MOM ID 或稳定业务编码';
COMMENT ON COLUMN iam_security_audit_event.session_id IS '可选用户授权 Session ID；不建立外键以保证 Session 清理后仍保留安全事件';
COMMENT ON COLUMN iam_security_audit_event.ip_address IS '可选来源 IP 文本；不得拼接 Authorization Header 或 Token';
COMMENT ON COLUMN iam_security_audit_event.user_agent IS '可选 User-Agent 摘要；必须控制长度并移除敏感 Header';
COMMENT ON COLUMN iam_security_audit_event.reason_code IS '稳定原因编码，用于安全统计、告警和调查';
COMMENT ON COLUMN iam_security_audit_event.reason_detail IS '受控原因摘要；禁止密码、密码摘要、Access Token、Refresh Token、Token 摘要、Authorization Code、code_verifier、Authorization Header 或私钥';
COMMENT ON COLUMN iam_security_audit_event.change_summary IS '受控 JSONB 对象摘要，只允许非敏感变更前后值、字段名和管理说明；禁止任何凭证、Token、Token 摘要或密码材料';
COMMENT ON COLUMN iam_security_audit_event.correlation_id IS '端到端关联标识，用于把安全事件与日志、Trace 和管理操作关联，不作为授权证明';
COMMENT ON COLUMN iam_security_audit_event.occurred_at IS '安全事件实际发生 UTC 时间，用于排序、调查和 180 天保留策略';
COMMENT ON COLUMN iam_security_audit_event.created_at IS '安全事件记录持久化 UTC 时间，可晚于 occurred_at';
