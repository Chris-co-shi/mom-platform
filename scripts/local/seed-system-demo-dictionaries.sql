-- 仅用于本地 System 字典页面验收；不要放入 Flyway 或在生产环境执行。
-- 执行：docker exec -i postgres17 psql -X -v ON_ERROR_STOP=1 -U postgres -d mom_platform \
--         < scripts/local/seed-system-demo-dictionaries.sql
-- 重复执行只补充缺失的 demo.* 类型和条目，不覆盖页面中已修改的数据。
BEGIN;

-- 避免把演示条目挂到其他人已创建的同编码字典下。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM mom_system.system_dictionary
        WHERE dictionary_code IN ('demo.contact-channel', 'demo.document-format', 'demo.weekday')
          AND created_by <> 'local-demo-seed'
    ) THEN
        RAISE EXCEPTION 'demo dictionary code already belongs to another creator';
    END IF;
END;
$$;

INSERT INTO mom_system.system_dictionary
    (id, dictionary_code, dictionary_name, enabled, version, description,
     created_by, created_at, updated_by, updated_at, deleted)
VALUES
    ('8800000000000000001', 'demo.contact-channel', '联系渠道（演示）', true, 0,
     '本地验收样例；不代表 MDM 联系方式的权威模型。',
     'local-demo-seed', transaction_timestamp(), 'local-demo-seed', transaction_timestamp(), false),
    ('8800000000000000002', 'demo.document-format', '文档格式（演示）', true, 0,
     '本地验收样例；不用于生产文件格式校验。',
     'local-demo-seed', transaction_timestamp(), 'local-demo-seed', transaction_timestamp(), false),
    ('8800000000000000003', 'demo.weekday', '星期（演示）', true, 0,
     '本地验收样例；不用于工厂日历或排班决策。',
     'local-demo-seed', transaction_timestamp(), 'local-demo-seed', transaction_timestamp(), false)
ON CONFLICT (dictionary_code) DO NOTHING;

INSERT INTO mom_system.system_dictionary_item
    (id, dictionary_id, item_code, item_label, sort_order, enabled, version,
     description, created_by, created_at, updated_by, updated_at, deleted)
SELECT
    sample.id, dictionary.id, sample.item_code, sample.item_label,
    sample.sort_order, sample.enabled, 0,
    '仅供本地页面验收，不作为生产业务语义。',
    'local-demo-seed', transaction_timestamp(), 'local-demo-seed', transaction_timestamp(), false
FROM (VALUES
    ('8810000000000000001', 'demo.contact-channel', 'phone', '电话', 10, true),
    ('8810000000000000002', 'demo.contact-channel', 'email', '电子邮件', 20, true),
    ('8810000000000000003', 'demo.contact-channel', 'sms', '短信', 30, true),
    ('8810000000000000004', 'demo.contact-channel', 'fax', '传真', 40, false),
    ('8810000000000000005', 'demo.document-format', 'pdf', 'PDF 文档', 10, true),
    ('8810000000000000006', 'demo.document-format', 'csv', 'CSV 表格', 20, true),
    ('8810000000000000007', 'demo.document-format', 'xlsx', 'Excel 工作簿', 30, true),
    ('8810000000000000008', 'demo.document-format', 'json', 'JSON 文件', 40, true),
    ('8810000000000000009', 'demo.weekday', 'monday', '星期一', 10, true),
    ('8810000000000000010', 'demo.weekday', 'tuesday', '星期二', 20, true),
    ('8810000000000000011', 'demo.weekday', 'wednesday', '星期三', 30, true),
    ('8810000000000000012', 'demo.weekday', 'thursday', '星期四', 40, true),
    ('8810000000000000013', 'demo.weekday', 'friday', '星期五', 50, true),
    ('8810000000000000014', 'demo.weekday', 'saturday', '星期六', 60, true),
    ('8810000000000000015', 'demo.weekday', 'sunday', '星期日', 70, true)
) AS sample(id, dictionary_code, item_code, item_label, sort_order, enabled)
JOIN mom_system.system_dictionary AS dictionary
  ON dictionary.dictionary_code = sample.dictionary_code
 AND dictionary.created_by = 'local-demo-seed'
 AND dictionary.deleted = false
ON CONFLICT (dictionary_id, item_code) DO NOTHING;

COMMIT;
