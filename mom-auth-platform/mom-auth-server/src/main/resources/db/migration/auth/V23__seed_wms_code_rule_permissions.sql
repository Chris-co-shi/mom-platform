-- WMS 仅管理自身编码规则；平台管理员获得读写权限。
INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000163','WMS','CODE_RULE','WMS 编码规则','维护 WMS 编码规则草稿与修订',70,true,
  CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000164','READ','wms:code-rule:read','WMS 编码规则读取','查询 WMS 编码规则及修订'),
  ('1000000000000000165','WRITE','wms:code-rule:write','WMS 编码规则维护','创建、修订和启停 WMS 编码规则')
) seed(id,action_code,code,name,description)
JOIN auth_permission_resource resource
  ON resource.domain_code='WMS' AND resource.resource_code='CODE_RULE'
 AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000166','wms:code-rule:read'),
  ('1000000000000000167','wms:code-rule:write')
) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (
  SELECT 1 FROM auth_role_permission grant_row
   WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id
);

DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES ('wms:code-rule:read'),('wms:code-rule:write')) expected(code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
  ) THEN RAISE EXCEPTION 'WMS code rule permissions missing'; END IF;
END $$;
