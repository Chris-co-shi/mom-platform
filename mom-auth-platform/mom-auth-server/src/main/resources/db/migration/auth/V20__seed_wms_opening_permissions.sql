INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000146','WMS','OPENING_INVENTORY','期初库存建账','管理期初库存建账及仓库库存启用',20,true,
  CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000147','READ','wms:opening:read','期初建账读取','查询期初建账单及过账结果'),
  ('1000000000000000148','WRITE','wms:opening:write','期初建账维护','创建草稿并维护未过账明细'),
  ('1000000000000000149','POST','wms:opening:post','期初库存过账','确认期初库存并写入库存交易'),
  ('1000000000000000150','ACTIVATE','wms:opening:activate','仓库库存启用','启用已完成期初核对的仓库库存')
) seed(id,action_code,code,name,description)
JOIN auth_permission_resource resource
  ON resource.domain_code='WMS' AND resource.resource_code='OPENING_INVENTORY'
 AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'WMS_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000151','wms:opening:read'),
  ('1000000000000000152','wms:opening:write'),
  ('1000000000000000153','wms:opening:post'),
  ('1000000000000000154','wms:opening:activate')
) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (
  SELECT 1 FROM auth_role_permission grant_row
   WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id
);

DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES ('wms:opening:read'),('wms:opening:write'),
                          ('wms:opening:post'),('wms:opening:activate')) expected(code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
  ) THEN RAISE EXCEPTION 'WMS opening inventory permissions missing'; END IF;
END $$;
