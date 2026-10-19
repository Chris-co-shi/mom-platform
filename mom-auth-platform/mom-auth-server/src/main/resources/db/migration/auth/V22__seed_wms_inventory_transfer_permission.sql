INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000160','WMS','INVENTORY_TRANSFER','库存库位移动','同仓库内正常库存的库位移动',31,true,
  CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT '1000000000000000161', resource.id, 'TRANSFER', 'wms:inventory:transfer',
       '库存库位移动', '从具体正常库存余额移动至同仓库其他库位', true,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', 0, false
FROM auth_permission_resource resource
WHERE resource.domain_code='WMS' AND resource.resource_code='INVENTORY_TRANSFER'
  AND resource.enabled=true AND resource.deleted=false
  AND NOT EXISTS (SELECT 1 FROM auth_permission WHERE code='wms:inventory:transfer');

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000162', '1000000000000000002', permission.id,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='wms:inventory:transfer'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grant_row
    WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM auth_permission WHERE code='wms:inventory:transfer'
      AND action_code='TRANSFER' AND enabled=true AND deleted=false)
  THEN RAISE EXCEPTION 'WMS inventory transfer permission missing'; END IF;
END $$;
