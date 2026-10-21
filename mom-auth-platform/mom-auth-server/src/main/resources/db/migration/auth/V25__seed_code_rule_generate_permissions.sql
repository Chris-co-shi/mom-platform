-- 使用权限独立于规则管理权限，由 IAM 管理员按业务角色授权。
INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, 'GENERATE', seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'CODE_RULE_BOOTSTRAP', CURRENT_TIMESTAMP, 'CODE_RULE_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000183','MDM','mdm:code-rule:generate','MDM 正式发号','使用已发布的 MDM 规则申请正式编码'),
  ('1000000000000000184','WMS','wms:code-rule:generate','WMS 正式发号','使用已发布的 WMS 规则申请正式编码')
) seed(id,domain_code,code,name,description)
JOIN auth_permission_resource resource ON resource.domain_code=seed.domain_code
 AND resource.resource_code='CODE_RULE' AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'CODE_RULE_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000185','mdm:code-rule:generate'),
  ('1000000000000000186','wms:code-rule:generate')
) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (SELECT 1 FROM auth_role_permission grant_row
 WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id);
