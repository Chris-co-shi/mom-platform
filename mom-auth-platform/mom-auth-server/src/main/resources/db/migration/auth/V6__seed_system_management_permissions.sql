-- System V1 管理权限归 Auth 所有；System 只消费稳定 authority code，不直接读写 Auth Schema。
-- V5 已建立权限资源目录。若运维已手工建立同编码资源/权限，本迁移保留既有 ID 和启停状态，
-- 但要求其归属、动作与编码完全一致；不静默挪动或重新启用已有授权数据。

INSERT INTO auth_permission_resource (
    id, domain_code, resource_code, name, description, sort_order, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
) VALUES
    ('1000000000000000040', 'SYSTEM', 'DICTIONARY', '平台字典', 'System 字典类型与条目的授权资源', 10, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false),
    ('1000000000000000041', 'SYSTEM', 'I18N', '支持语言与 System 文案', 'System Locale、Message 与 Translation 的授权资源', 20, true, CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false)
ON CONFLICT (domain_code, resource_code) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM auth_permission_resource
        WHERE domain_code = 'SYSTEM' AND resource_code IN ('DICTIONARY', 'I18N')
          AND (deleted = true OR enabled = false)
    ) THEN
        RAISE EXCEPTION 'V6 System permission resource is deleted or disabled; reconcile it before migration';
    END IF;
END $$;

INSERT INTO auth_permission (
    id, resource_id, action_code, code, name, description, enabled,
    created_at, created_by, updated_at, updated_by, version, deleted
)
SELECT seed.id, resource.id, seed.action_code, seed.code, seed.name, seed.description, true,
       CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', CURRENT_TIMESTAMP, 'SYSTEM_BOOTSTRAP', 0, false
FROM (VALUES
    ('1000000000000000042', 'DICTIONARY', 'READ', 'system:dictionary:read', '平台字典读取', '读取字典类型与条目管理列表'),
    ('1000000000000000043', 'DICTIONARY', 'WRITE', 'system:dictionary:write', '平台字典维护', '创建、编辑及启停字典类型与条目'),
    ('1000000000000000044', 'I18N', 'READ', 'system:i18n:read', 'System 国际化读取', '读取支持语言、System 文案及译文'),
    ('1000000000000000045', 'I18N', 'WRITE', 'system:i18n:write', 'System 国际化维护', '维护支持语言、默认项、System 文案及译文')
) AS seed(id, resource_code, action_code, code, name, description)
JOIN auth_permission_resource resource
  ON resource.domain_code = 'SYSTEM' AND resource.resource_code = seed.resource_code AND resource.deleted = false
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.code = seed.code);

-- 不允许相同 authority code 指向其他资源或动作，否则 Web 菜单与 System @PreAuthorize 会产生误授权。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('system:dictionary:read', 'DICTIONARY', 'READ'),
            ('system:dictionary:write', 'DICTIONARY', 'WRITE'),
            ('system:i18n:read', 'I18N', 'READ'),
            ('system:i18n:write', 'I18N', 'WRITE')
        ) AS expected(code, resource_code, action_code)
        LEFT JOIN auth_permission permission ON permission.code = expected.code
        LEFT JOIN auth_permission_resource resource ON resource.id = permission.resource_id
        WHERE permission.id IS NULL OR permission.deleted = true
           OR permission.action_code <> expected.action_code
           OR resource.domain_code <> 'SYSTEM' OR resource.resource_code <> expected.resource_code
           OR resource.deleted = true
    ) THEN
        RAISE EXCEPTION 'V6 System permission catalog conflicts with existing authority metadata';
    END IF;
END $$;
