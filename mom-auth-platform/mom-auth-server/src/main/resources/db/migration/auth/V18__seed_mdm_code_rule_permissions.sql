-- 编码规则由 MDM 持有，Auth 只维护对应 RBAC 权限。
INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000138','MDM','CODE_RULE','编码规则','维护 MDM 编码规则草稿与修订',70,true,
  CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000139','READ','mdm:code-rule:read','编码规则读取','查询 MDM 编码规则及修订'),
  ('1000000000000000140','WRITE','mdm:code-rule:write','编码规则维护','创建、修订和启停 MDM 编码规则')
) seed(id,action_code,code,name,description)
JOIN auth_permission_resource resource
  ON resource.domain_code='MDM' AND resource.resource_code='CODE_RULE'
 AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000141','mdm:code-rule:read'),
  ('1000000000000000142','mdm:code-rule:write')
) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (
  SELECT 1 FROM auth_role_permission grant_row
   WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id
);

DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES ('mdm:code-rule:read'),('mdm:code-rule:write')) expected(code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
  ) THEN RAISE EXCEPTION 'MDM code rule permissions missing'; END IF;
END $$;
