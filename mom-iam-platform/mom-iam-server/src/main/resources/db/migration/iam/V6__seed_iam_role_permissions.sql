-- P1.5 S02 内置角色与 Permission 映射；稳定 ID 由固定角色编码和 Permission Code 排序生成。
WITH grants(role_code, permission_code) AS (
    SELECT 'PLATFORM_ADMIN', code FROM iam_permission WHERE built_in = true AND deleted = false
    UNION ALL
    SELECT 'IAM_ADMIN', code FROM iam_permission
     WHERE built_in = true AND deleted = false
       AND (resource_code IN ('user', 'role', 'permission', 'session')
            OR code = 'iam:client:read')
    UNION ALL
    SELECT 'SECURITY_AUDITOR', code FROM iam_permission
     WHERE code IN ('iam:user:read', 'iam:role:read', 'iam:permission:read',
                    'iam:session:read', 'iam:audit:read', 'iam:client:read')
), resolved AS (
    SELECT r.id AS role_id, p.id AS permission_id, g.role_code, g.permission_code
      FROM grants g
      JOIN iam_role r ON r.code = g.role_code
      JOIN iam_permission p ON p.code = g.permission_code
), numbered AS (
    SELECT '13' || lpad(row_number() OVER (ORDER BY role_code, permission_code)::text, 17, '0') AS id,
           role_id, permission_id
      FROM resolved
)
INSERT INTO iam_role_permission(id, role_id, permission_id, created_at, created_by)
SELECT id, role_id, permission_id, now(), 'mom-iam-flyway' FROM numbered;
