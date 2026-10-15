-- 质量基础定义的唯一写入权威已转移到 MDM；保留 QMS 历史记录但禁止继续授权。
UPDATE auth_permission
   SET enabled = false, updated_at = CURRENT_TIMESTAMP, updated_by = 'MDM_BOOTSTRAP', version = version + 1
 WHERE code IN ('qms:quality-definition:read', 'qms:quality-definition:write')
   AND enabled = true AND deleted = false;

UPDATE auth_permission_resource
   SET enabled = false, updated_at = CURRENT_TIMESTAMP, updated_by = 'MDM_BOOTSTRAP', version = version + 1
 WHERE domain_code = 'QMS' AND resource_code = 'QUALITY_DEFINITION'
   AND enabled = true AND deleted = false;

INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000133','MDM','QUALITY_DEFINITION','质量基础定义',
  '检验特性、方法及质量标准修订',60,true,
  CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000134','READ','mdm:quality-definition:read','质量定义读取','查询 MDM 质量基础定义'),
  ('1000000000000000135','WRITE','mdm:quality-definition:write','质量定义维护','维护并发布 MDM 质量基础定义')
) seed(id,action_code,code,name,description)
JOIN auth_permission_resource resource
  ON resource.domain_code='MDM' AND resource.resource_code='QUALITY_DEFINITION'
 AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000136','mdm:quality-definition:read'),
  ('1000000000000000137','mdm:quality-definition:write')
) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (
  SELECT 1 FROM auth_role_permission grant_row
   WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id
);

DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM auth_permission
     WHERE code IN ('qms:quality-definition:read','qms:quality-definition:write')
       AND enabled=true AND deleted=false
  ) OR EXISTS (
    SELECT 1 FROM (VALUES ('mdm:quality-definition:read'),('mdm:quality-definition:write')) expected(code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
  ) THEN
    RAISE EXCEPTION 'quality definition authority ownership migration failed';
  END IF;
END $$;
