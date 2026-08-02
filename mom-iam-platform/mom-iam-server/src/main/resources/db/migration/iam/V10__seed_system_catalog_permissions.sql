-- P1.6 S17：注册 System Application Catalog 管理 Permission，并赋予内置 PLATFORM_ADMIN。
-- Permission 仍由 IAM 权威管理；本 Migration 不创建动态 Permission API、不修改 OAuth Client、JWT Claim 或 Session。
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
        ('1900000000000000901', 'system:catalog:read', '读取应用目录',
         'system', 'catalog', 'read', 'LOW', 'ENABLED',
         '读取 System Application Catalog、Navigation Draft 与发布历史', true,
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10', CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10', 0, false),
        ('1900000000000000902', 'system:catalog:write', '维护应用目录草稿',
         'system', 'catalog', 'write', 'MEDIUM', 'ENABLED',
         '创建和更新 Application Catalog 与 Navigation Draft', true,
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10', CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10', 0, false),
        ('1900000000000000903', 'system:catalog:publish', '发布应用目录',
         'system', 'catalog', 'publish', 'HIGH', 'ENABLED',
         '发布或回滚 System Application Catalog 不可变快照', true,
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10', CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10', 0, false);

    INSERT INTO iam_role_permission (id, role_id, permission_id, created_at, created_by)
    VALUES
        ('1900000000000000911', v_platform_admin_role_id, '1900000000000000901',
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10'),
        ('1900000000000000912', v_platform_admin_role_id, '1900000000000000902',
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10'),
        ('1900000000000000913', v_platform_admin_role_id, '1900000000000000903',
         CURRENT_TIMESTAMP, 'IAM_MIGRATION_V10');
END $$;
