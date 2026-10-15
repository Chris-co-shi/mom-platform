-- MDM 产品入口改名为“主数据中心”，技术模块名、路由和权限前缀保持不变。
UPDATE system_i18n_translation
SET message_text = CASE locale_code
        WHEN 'zh-CN' THEN '主数据中心'
        WHEN 'en-US' THEN 'Master Data Center'
        ELSE message_text
    END,
    version = version + 1,
    updated_by = 'SYSTEM_MIGRATION',
    updated_at = CURRENT_TIMESTAMP
WHERE message_id = (
    SELECT id FROM system_i18n_message_definition WHERE message_key = 'navigation.mdm'
);

INSERT INTO system_i18n_message_definition (
    id, message_key, description, enabled, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9650000000000004001', 'navigation.mdmorganizationresources', 'MDM 组织与制造资源二级目录', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9650000000000004002', 'navigation.mdmmaterialuom', 'MDM 物料与计量二级目录', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9650000000000004003', 'navigation.mdmmanufacturingdefinition', 'MDM 产品与制造定义二级目录', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9650000000000004004', 'navigation.mdmwarehouselogistics', 'MDM 仓储与物流基础二级目录', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9650000000000004005', 'navigation.mdmqualitydefinition', 'MDM 质量基础定义二级目录', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9650000000000004006', 'navigation.mdmgovernance', 'MDM 主数据治理二级目录', true, 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);

INSERT INTO system_i18n_translation (
    id, message_id, locale_code, message_text, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('9660000000000004001', '9650000000000004001', 'zh-CN', '组织与制造资源', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004002', '9650000000000004001', 'en-US', 'Organization & Manufacturing Resources', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004003', '9650000000000004002', 'zh-CN', '物料与计量', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004004', '9650000000000004002', 'en-US', 'Material & UOM', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004005', '9650000000000004003', 'zh-CN', '产品与制造定义', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004006', '9650000000000004003', 'en-US', 'Product & Manufacturing Definition', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004007', '9650000000000004004', 'zh-CN', '仓储与物流基础', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004008', '9650000000000004004', 'en-US', 'Warehouse & Logistics Master Data', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004009', '9650000000000004005', 'zh-CN', '质量基础定义', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004010', '9650000000000004005', 'en-US', 'Quality Master Data', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004011', '9650000000000004006', 'zh-CN', '主数据治理', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP),
    ('9660000000000004012', '9650000000000004006', 'en-US', 'Master Data Governance', 0, false, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP);
