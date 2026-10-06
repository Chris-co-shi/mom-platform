-- Auth 持有权限目录；各 Owner 仅消费 authority，不跨 Schema 写入 Auth。
INSERT INTO auth_permission_resource (
    id, domain_code, resource_code, name, description, sort_order, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
) VALUES
    ('1000000000000000050', 'AUTH', 'I18N', 'Auth 国际化', 'Auth 登录、账户与 IAM 文案管理', 40, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false),
    ('1000000000000000051', 'MDM', 'I18N', 'MDM 国际化', 'MDM 主数据错误与业务文案管理', 50, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false)
ON CONFLICT (domain_code, resource_code) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM auth_permission_resource
               WHERE (domain_code, resource_code) IN (('AUTH','I18N'),('MDM','I18N'))
                 AND (deleted OR NOT enabled)) THEN
        RAISE EXCEPTION 'I18n owner permission resource disabled or deleted; reconcile before migration';
    END IF;
END $$;

INSERT INTO auth_permission (
    id, resource_id, action_code, code, name, description, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false
FROM (VALUES
    ('1000000000000000052','AUTH','READ','auth:i18n:read','Auth 文案读取','查询 Auth Message 与 Translation'),
    ('1000000000000000053','AUTH','WRITE','auth:i18n:write','Auth 文案维护','维护 Auth Message 与 Translation'),
    ('1000000000000000054','MDM','READ','mdm:i18n:read','MDM 文案读取','查询 MDM Message 与 Translation'),
    ('1000000000000000055','MDM','WRITE','mdm:i18n:write','MDM 文案维护','维护 MDM Message 与 Translation')
) AS seed(id, domain_code, action_code, code, name, description)
JOIN auth_permission_resource resource ON resource.domain_code = seed.domain_code
    AND resource.resource_code = 'I18N' AND NOT resource.deleted
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code = seed.code);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('auth:i18n:read','AUTH','READ'), ('auth:i18n:write','AUTH','WRITE'),
            ('mdm:i18n:read','MDM','READ'), ('mdm:i18n:write','MDM','WRITE')
        ) AS expected(code,domain_code,action_code)
        LEFT JOIN auth_permission permission ON permission.code = expected.code
        LEFT JOIN auth_permission_resource resource ON resource.id = permission.resource_id
        WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
           OR permission.action_code <> expected.action_code
           OR resource.domain_code <> expected.domain_code OR resource.resource_code <> 'I18N'
           OR resource.deleted
    ) THEN
        RAISE EXCEPTION 'I18n owner authority metadata conflicts with existing catalog';
    END IF;
END $$;

-- 平台管理员仍通过显式 RolePermission 获取权限，不依赖角色名的隐式超权。
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000060', '1000000000000000002', p.id, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP'
FROM auth_permission p WHERE p.code = 'auth:i18n:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission r WHERE r.role_id = '1000000000000000002' AND r.permission_id = p.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000061', '1000000000000000002', p.id, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP'
FROM auth_permission p WHERE p.code = 'auth:i18n:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission r WHERE r.role_id = '1000000000000000002' AND r.permission_id = p.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000062', '1000000000000000002', p.id, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP'
FROM auth_permission p WHERE p.code = 'mdm:i18n:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission r WHERE r.role_id = '1000000000000000002' AND r.permission_id = p.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000063', '1000000000000000002', p.id, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP'
FROM auth_permission p WHERE p.code = 'mdm:i18n:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission r WHERE r.role_id = '1000000000000000002' AND r.permission_id = p.id);
