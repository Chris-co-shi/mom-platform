INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000155','WMS','INVENTORY_FREEZE','仓储库存限制','管理仓储库存冻结与解冻限制',30,true,
  CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000156','FREEZE','wms:inventory:freeze','仓储库存冻结','对指定实时库存执行全部或部分仓储冻结'),
  ('1000000000000000157','UNFREEZE','wms:inventory:unfreeze','仓储库存解冻','解除明确冻结限制上的全部或部分仓储冻结')
) seed(id,action_code,code,name,description)
JOIN auth_permission_resource resource
  ON resource.domain_code='WMS' AND resource.resource_code='INVENTORY_FREEZE'
 AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP'
FROM (VALUES ('1000000000000000158','wms:inventory:freeze'),
             ('1000000000000000159','wms:inventory:unfreeze')) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (
  SELECT 1 FROM auth_role_permission grant_row
   WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id
);

DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES ('wms:inventory:freeze'),('wms:inventory:unfreeze')) expected(code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
  ) THEN RAISE EXCEPTION 'WMS inventory freeze permissions missing'; END IF;
END $$;
