-- 制造定义读写权限的目录由 Auth 独占；不覆盖已有同编码目录，冲突时中止迁移。
INSERT INTO auth_permission_resource (
    id, domain_code, resource_code, name, description, sort_order, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
) VALUES (
    '1000000000000000110', 'MDM', 'MANUFACTURING_DEFINITION', '产品与制造定义',
    'Formula、Routing、ProductionVersion 的业务修订与发布', 60, true,
    CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', 0, false
) ON CONFLICT (domain_code, resource_code) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM auth_permission_resource
    WHERE domain_code='MDM' AND resource_code='MANUFACTURING_DEFINITION'
      AND (deleted OR NOT enabled)) THEN
    RAISE EXCEPTION 'MDM manufacturing definition resource disabled/deleted';
  END IF;
END $$;

INSERT INTO auth_permission (
  id, resource_id, action_code, code, name, description, enabled,
  created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
  CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP', 0, false
FROM (VALUES
  ('1000000000000000111', 'READ', 'mdm:manufacturing-definition:read', '制造定义读取', '查询配方、工艺路线与生产版本'),
  ('1000000000000000112', 'WRITE', 'mdm:manufacturing-definition:write', '制造定义维护', '创建、修订、发布和废止制造定义')
) AS seed(id, action_code, code, name, description)
JOIN auth_permission_resource resource ON resource.domain_code='MDM'
  AND resource.resource_code='MANUFACTURING_DEFINITION' AND resource.deleted=false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code=seed.code);

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES
      ('mdm:manufacturing-definition:read', 'READ'),
      ('mdm:manufacturing-definition:write', 'WRITE')
    ) AS expected(code, action_code)
    LEFT JOIN auth_permission permission ON permission.code=expected.code
    LEFT JOIN auth_permission_resource resource ON resource.id=permission.resource_id
    WHERE permission.id IS NULL OR permission.deleted OR NOT permission.enabled
      OR permission.action_code<>expected.action_code
      OR resource.domain_code<>'MDM' OR resource.resource_code<>'MANUFACTURING_DEFINITION'
      OR resource.deleted OR NOT resource.enabled
  ) THEN
    RAISE EXCEPTION 'MDM manufacturing definition authority metadata conflict';
  END IF;
END $$;

INSERT INTO auth_role_permission (id, role_id, permission_id, created_at, created_by)
SELECT seed.grant_id, '1000000000000000002', permission.id, CURRENT_TIMESTAMP, 'MDM_BOOTSTRAP'
FROM (VALUES
  ('1000000000000000113', 'mdm:manufacturing-definition:read'),
  ('1000000000000000114', 'mdm:manufacturing-definition:write')
) AS seed(grant_id, code)
JOIN auth_permission permission ON permission.code=seed.code
WHERE NOT EXISTS (SELECT 1 FROM auth_role_permission grants
  WHERE grants.role_id='1000000000000000002' AND grants.permission_id=permission.id);
