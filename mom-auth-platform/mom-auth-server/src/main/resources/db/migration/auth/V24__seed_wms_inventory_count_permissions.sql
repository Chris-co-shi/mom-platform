INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
  '1000000000000000168','WMS','INVENTORY_COUNT','库存盘点','管理整仓停账盘点、实盘差异及库存调整',40,true,
  CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id,resource_id,action_code,code,name,description,enabled,
  created_at,created_by,updated_at,updated_by,version,deleted
)
SELECT seed.id,resource.id,seed.action_code,seed.code,seed.name,seed.description,true,
       CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',CURRENT_TIMESTAMP,'WMS_BOOTSTRAP',0,false
FROM (VALUES
  ('1000000000000000169','READ','wms:inventory-count:read','盘点读取','查询盘点任务、快照和实盘明细'),
  ('1000000000000000170','CREATE','wms:inventory-count:create','盘点创建','创建盘点草稿'),
  ('1000000000000000171','WRITE','wms:inventory-count:write','盘点维护','修改草稿范围及取消未确认盘点'),
  ('1000000000000000172','START','wms:inventory-count:start','盘点开始','锁定整仓并生成账面快照'),
  ('1000000000000000173','COUNT','wms:inventory-count:count','实盘录入','录入实盘及账外实物'),
  ('1000000000000000174','CONFIRM','wms:inventory-count:confirm','差异确认','确认盘点差异'),
  ('1000000000000000175','ADJUST','wms:inventory-count:adjust','盘点调整','原子执行库存调整')
) seed(id,action_code,code,name,description)
JOIN auth_permission_resource resource ON resource.domain_code='WMS' AND resource.resource_code='INVENTORY_COUNT'
  AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

INSERT INTO auth_role_permission(id,role_id,permission_id,created_at,created_by)
SELECT seed.id,'1000000000000000002',permission.id,CURRENT_TIMESTAMP,'WMS_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000176','wms:inventory-count:read'),
  ('1000000000000000177','wms:inventory-count:create'),
  ('1000000000000000178','wms:inventory-count:write'),
  ('1000000000000000179','wms:inventory-count:start'),
  ('1000000000000000180','wms:inventory-count:count'),
  ('1000000000000000181','wms:inventory-count:confirm'),
  ('1000000000000000182','wms:inventory-count:adjust')
) seed(id,code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (SELECT 1 FROM auth_role_permission grant_row
                  WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id);
