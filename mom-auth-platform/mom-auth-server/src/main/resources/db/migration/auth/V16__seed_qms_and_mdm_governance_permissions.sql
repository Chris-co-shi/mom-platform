INSERT INTO auth_permission_resource (
  id, domain_code, resource_code, name, description, sort_order, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
) VALUES
('1000000000000000120','QMS','QUALITY_DEFINITION','质量基础定义','检验特性、方法及质量标准修订',10,true,CURRENT_TIMESTAMP,'QMS_BOOTSTRAP',CURRENT_TIMESTAMP,'QMS_BOOTSTRAP',0,false),
('1000000000000000121','QMS','I18N','质量文案','QMS 自有运行时与管理文案',90,true,CURRENT_TIMESTAMP,'QMS_BOOTSTRAP',CURRENT_TIMESTAMP,'QMS_BOOTSTRAP',0,false),
('1000000000000000122','MDM','GOVERNANCE','主数据治理','只读主数据一致性检查',70,true,CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',CURRENT_TIMESTAMP,'MDM_BOOTSTRAP',0,false)
ON CONFLICT (domain_code, resource_code) DO NOTHING;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
  CURRENT_TIMESTAMP, seed.actor, CURRENT_TIMESTAMP, seed.actor, 0, false
FROM (VALUES
 ('1000000000000000123','QMS','QUALITY_DEFINITION','READ','qms:quality-definition:read','质量定义读取','查询质量定义','QMS_BOOTSTRAP'),
 ('1000000000000000124','QMS','QUALITY_DEFINITION','WRITE','qms:quality-definition:write','质量定义维护','维护并发布质量定义','QMS_BOOTSTRAP'),
 ('1000000000000000125','QMS','I18N','READ','qms:i18n:read','质量文案读取','查询 QMS 文案','QMS_BOOTSTRAP'),
 ('1000000000000000126','QMS','I18N','WRITE','qms:i18n:write','质量文案维护','维护 QMS 文案','QMS_BOOTSTRAP'),
 ('1000000000000000127','MDM','GOVERNANCE','READ','mdm:governance:read','主数据治理读取','执行只读一致性检查','MDM_BOOTSTRAP')
) AS seed(id,domain_code,resource_code,action_code,code,name,description,actor)
JOIN auth_permission_resource resource ON resource.domain_code=seed.domain_code
 AND resource.resource_code=seed.resource_code AND resource.enabled=true AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM (VALUES
  ('qms:quality-definition:read'),('qms:quality-definition:write'),('qms:i18n:read'),('qms:i18n:write'),('mdm:governance:read')
 ) expected(code) LEFT JOIN auth_permission permission ON permission.code=expected.code
 WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled) THEN
  RAISE EXCEPTION 'QMS or MDM governance authority metadata conflict';
 END IF;
END $$;

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, seed.actor
FROM (VALUES
 ('1000000000000000128','qms:quality-definition:read','QMS_BOOTSTRAP'),
 ('1000000000000000129','qms:quality-definition:write','QMS_BOOTSTRAP'),
 ('1000000000000000130','qms:i18n:read','QMS_BOOTSTRAP'),
 ('1000000000000000131','qms:i18n:write','QMS_BOOTSTRAP'),
 ('1000000000000000132','mdm:governance:read','MDM_BOOTSTRAP')
) seed(id,code,actor) JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (SELECT 1 FROM auth_role_permission grant_row
 WHERE grant_row.role_id='1000000000000000002' AND grant_row.permission_id=permission.id);
