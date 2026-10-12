-- MDM V1 授权目录仅由 Auth Schema 持有，MDM Server 只消费 authority。
-- 不覆盖运维手工创建的同编码资源与权限；归属冲突显式中止 Migration。
INSERT INTO auth_permission_resource (
    id, domain_code, resource_code, name, description, sort_order, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
) VALUES
    ('1000000000000000070','MDM','FACTORY','工厂结构','Plant、Workshop、ProductionLine、Workstation',10,true,CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false),
    ('1000000000000000071','MDM','WAREHOUSE','仓库结构','Warehouse、WarehouseArea',20,true,CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false),
    ('1000000000000000072','MDM','LOCATION','库位与类型','Location、LocationType',30,true,CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false),
    ('1000000000000000073','MDM','UOM','计量单位','Dimension、UomCategory、Uom 与换算规则',40,true,CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false),
    ('1000000000000000074','MDM','MATERIAL','物料主数据','MaterialCategory、Material',50,true,CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false)
ON CONFLICT (domain_code, resource_code) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM auth_permission_resource
     WHERE domain_code = 'MDM'
       AND resource_code IN ('FACTORY','WAREHOUSE','LOCATION','UOM','MATERIAL')
       AND (deleted OR NOT enabled)) THEN
    RAISE EXCEPTION 'MDM permission resource disabled/deleted; reconcile before migration';
  END IF;
END $$;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
  CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false
FROM (VALUES
  ('1000000000000000080','FACTORY','READ','mdm:factory:read','工厂结构读取','查询工厂结构'),
  ('1000000000000000081','FACTORY','WRITE','mdm:factory:write','工厂结构维护','创建、编辑、启停工厂结构'),
  ('1000000000000000082','WAREHOUSE','READ','mdm:warehouse:read','仓库结构读取','查询仓库结构'),
  ('1000000000000000083','WAREHOUSE','WRITE','mdm:warehouse:write','仓库结构维护','创建、编辑、启停仓库结构'),
  ('1000000000000000084','LOCATION','READ','mdm:location:read','库位与类型读取','查询库位与类型'),
  ('1000000000000000085','LOCATION','WRITE','mdm:location:write','库位与类型维护','创建、编辑、启停库位与类型'),
  ('1000000000000000086','UOM','READ','mdm:uom:read','计量单位读取','查询计量单位'),
  ('1000000000000000087','UOM','WRITE','mdm:uom:write','计量单位维护','创建、编辑、启停计量单位'),
  ('1000000000000000088','MATERIAL','READ','mdm:material:read','物料主数据读取','查询物料主数据'),
  ('1000000000000000089','MATERIAL','WRITE','mdm:material:write','物料主数据维护','创建、编辑、启停物料主数据')
) AS seed(id, resource_code, action_code, code, name, description)
JOIN auth_permission_resource resource ON resource.domain_code='MDM'
 AND resource.resource_code=seed.resource_code AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES
      ('mdm:factory:read','FACTORY','READ'),
      ('mdm:factory:write','FACTORY','WRITE'),
      ('mdm:warehouse:read','WAREHOUSE','READ'),
      ('mdm:warehouse:write','WAREHOUSE','WRITE'),
      ('mdm:location:read','LOCATION','READ'),
      ('mdm:location:write','LOCATION','WRITE'),
      ('mdm:uom:read','UOM','READ'),
      ('mdm:uom:write','UOM','WRITE'),
      ('mdm:material:read','MATERIAL','READ'),
      ('mdm:material:write','MATERIAL','WRITE')
    ) AS expected(code, resource_code, action_code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    LEFT JOIN auth_permission_resource resource ON resource.id=permission.resource_id
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
       OR permission.action_code<>expected.action_code
       OR resource.domain_code<>'MDM' OR resource.resource_code<>expected.resource_code
       OR resource.deleted OR NOT resource.enabled
  ) THEN
    RAISE EXCEPTION 'MDM authority metadata conflicts with existing catalog';
  END IF;
END $$;

-- PLATFORM_ADMIN 不具有隐式超权，明确授予 MDM V1 的十个 Authority。
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000090', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:factory:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000091', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:factory:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000092', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:warehouse:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000093', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:warehouse:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000094', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:location:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000095', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:location:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000096', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:uom:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000097', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:uom:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000098', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:material:read'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT '1000000000000000099', '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM auth_permission permission WHERE permission.code='mdm:material:write'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission grants
    WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
