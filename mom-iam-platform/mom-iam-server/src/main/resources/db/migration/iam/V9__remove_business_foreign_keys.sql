DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM iam_internal_user_profile child LEFT JOIN iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_internal_user_profile contains orphan user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_external_user_binding child LEFT JOIN iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_external_user_binding contains orphan user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_user_role child LEFT JOIN iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_user_role contains orphan user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_user_role child LEFT JOIN iam_role parent ON parent.id=child.role_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_user_role contains orphan role_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_role_permission child LEFT JOIN iam_role parent ON parent.id=child.role_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_role_permission contains orphan role_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_role_permission child LEFT JOIN iam_permission parent ON parent.id=child.permission_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_role_permission contains orphan permission_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_user_application child LEFT JOIN iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_user_application contains orphan user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_user_factory_scope child LEFT JOIN iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_user_factory_scope contains orphan user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_user_session child LEFT JOIN iam_user parent ON parent.id=child.user_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_user_session contains orphan user_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_user_session child LEFT JOIN iam_oauth_client_policy parent ON parent.client_id=child.client_id WHERE parent.client_id IS NULL) THEN
        RAISE EXCEPTION 'iam_user_session contains orphan client_id';
    END IF;
    IF EXISTS (SELECT 1 FROM iam_refresh_token child LEFT JOIN iam_user_session parent ON parent.id=child.session_id WHERE parent.id IS NULL) THEN
        RAISE EXCEPTION 'iam_refresh_token contains orphan session_id';
    END IF;
    IF EXISTS (
        SELECT 1 FROM iam_refresh_token child
        LEFT JOIN iam_refresh_token parent ON parent.id=child.replaced_by_token_id
        WHERE child.replaced_by_token_id IS NOT NULL AND parent.id IS NULL
    ) THEN
        RAISE EXCEPTION 'iam_refresh_token contains orphan replaced_by_token_id';
    END IF;
END;
$$;

ALTER TABLE iam_internal_user_profile DROP CONSTRAINT fk_iam_internal_profile_user;
ALTER TABLE iam_external_user_binding DROP CONSTRAINT fk_iam_external_binding_user;
ALTER TABLE iam_user_role DROP CONSTRAINT fk_iam_user_role_user;
ALTER TABLE iam_user_role DROP CONSTRAINT fk_iam_user_role_role;
ALTER TABLE iam_role_permission DROP CONSTRAINT fk_iam_role_permission_role;
ALTER TABLE iam_role_permission DROP CONSTRAINT fk_iam_role_permission_permission;
ALTER TABLE iam_user_application DROP CONSTRAINT fk_iam_user_application_user;
ALTER TABLE iam_user_factory_scope DROP CONSTRAINT fk_iam_user_factory_scope_user;
ALTER TABLE iam_user_session DROP CONSTRAINT fk_iam_user_session_user;
ALTER TABLE iam_user_session DROP CONSTRAINT fk_iam_user_session_client_policy;
ALTER TABLE iam_refresh_token DROP CONSTRAINT fk_iam_refresh_token_session;
ALTER TABLE iam_refresh_token DROP CONSTRAINT fk_iam_refresh_token_replacement;

COMMENT ON COLUMN iam_internal_user_profile.user_id IS 'IAM 用户引用；创建前由 Application 校验，唯一约束保证一人一份资料，禁止物理外键';
COMMENT ON COLUMN iam_external_user_binding.user_id IS 'IAM 外部用户引用；Application 校验用户类型与 Party Binding，禁止物理外键';
COMMENT ON COLUMN iam_user_role.user_id IS 'IAM 用户引用；受控授权用例锁定用户并验证存在性，禁止物理外键';
COMMENT ON COLUMN iam_user_role.role_id IS 'IAM 角色引用；批量替换前校验全部角色有效，禁止物理外键';
COMMENT ON COLUMN iam_role_permission.role_id IS 'IAM 角色引用；受控授权用例锁定角色并验证存在性，禁止物理外键';
COMMENT ON COLUMN iam_role_permission.permission_id IS 'IAM Permission 引用；批量替换前校验全部 Permission 有效，禁止物理外键';
COMMENT ON COLUMN iam_user_application.user_id IS 'IAM 用户引用；Mobile Access 用例先锁定并校验用户，禁止物理外键';
COMMENT ON COLUMN iam_user_factory_scope.user_id IS 'IAM 用户引用；Factory Scope 用例先锁定并校验用户，禁止物理外键';
COMMENT ON COLUMN iam_user_session.user_id IS 'Session 所属 IAM 用户引用；认证创建前加载权威用户，禁止物理外键';
COMMENT ON COLUMN iam_user_session.client_id IS 'Session 所属 Client Policy 引用；认证创建前加载权威 Policy，禁止物理外键';
COMMENT ON COLUMN iam_refresh_token.session_id IS 'Refresh 轮换链所属 Session；只允许受控 Session Token 用例写入，禁止物理外键';
COMMENT ON COLUMN iam_refresh_token.replaced_by_token_id IS '成功轮换后的后继 Token 引用；同事务写入并校验 affected rows，禁止物理外键';
