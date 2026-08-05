-- P1.6 S05C：注册 System Dynamic I18n 治理 Permission，并赋予内置 PLATFORM_ADMIN。
-- System 只引用 Permission Code；本 Migration 不改变 Controller、安全规则、Token Claim 或既有 Session。
DO $$
DECLARE
    v_platform_admin_role_id varchar(19);
BEGIN
    SELECT id
      INTO STRICT v_platform_admin_role_id
      FROM iam_role
     WHERE code = 'PLATFORM_ADMIN'
       AND status = 'ENABLED'
       AND built_in = true
       AND deleted = false;

    INSERT INTO iam_permission (
        id, code, name, domain_code, resource_code, action_code,
        risk_level, status, description, built_in,
        created_at, created_by, updated_at, updated_by, version, deleted
    ) VALUES
        ('1900000000000000921', 'system:i18n:read', '读取动态国际化资源',
         'system', 'i18n', 'read', 'LOW', 'ENABLED',
         '读取 System Dynamic I18n 资源、消息草稿与发布历史', true,
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11', CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11', 0, false),
        ('1900000000000000922', 'system:i18n:write', '维护动态国际化草稿',
         'system', 'i18n', 'write', 'MEDIUM', 'ENABLED',
         '创建和更新 Dynamic I18n 资源与消息草稿', true,
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11', CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11', 0, false),
        ('1900000000000000923', 'system:i18n:publish', '发布动态国际化资源',
         'system', 'i18n', 'publish', 'HIGH', 'ENABLED',
         '发布或回滚 System Dynamic I18n 不可变快照', true,
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11', CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11', 0, false);

    INSERT INTO iam_role_permission (id, role_id, permission_id, created_at, created_by)
    VALUES
        ('1900000000000000931', v_platform_admin_role_id, '1900000000000000921',
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11'),
        ('1900000000000000932', v_platform_admin_role_id, '1900000000000000922',
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11'),
        ('1900000000000000933', v_platform_admin_role_id, '1900000000000000923',
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V11');
END $$;
