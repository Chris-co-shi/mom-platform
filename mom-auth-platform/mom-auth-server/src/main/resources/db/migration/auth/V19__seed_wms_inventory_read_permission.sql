-- WMS Phase 1 只开放实时库存读取；记账服务没有普通用户写权限或 HTTP 写接口。
INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000143','WMS','INVENTORY','库存','实时库存汇总与余额明细',10,true,
  CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT '1000000000000000144', resource.id, 'READ', 'wms:inventory:read',
       '库存读取', '查询实时库存汇总与余额明细', true,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', 0, false
FROM auth_permission_resource resource
WHERE resource.domain_code='WMS' AND resource.resource_code='INVENTORY'
  AND resource.enabled=true AND resource.deleted=false
  AND NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code='wms:inventory:read');

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000145', '1000000000000000002', permission.id,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='wms:inventory:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grant_row
                  WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM auth_permission permission
                 JOIN auth_permission_resource resource ON resource.id=permission.resource_id
                 WHERE permission.code='wms:inventory:read' AND permission.action_code='READ'
                   AND resource.domain_code='WMS' AND resource.resource_code='INVENTORY'
                   AND permission.enabled AND NOT permission.deleted)
  THEN RAISE EXCEPTION 'WMS inventory read permission missing or mismatched'; END IF;
END $$;
