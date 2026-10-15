-- Formula 页面文案由 MDM Owner 持有；导航标题仍属于 System。
INSERT INTO mdm_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97300000000000052' || lpad(seed.ord::text, 2, '0'), seed.key, NULL,
       true, 0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'mdm.formula.title'), (2,'mdm.formula.description'), (3,'mdm.formula.listhint'),
    (4,'mdm.formula.nodata'), (5,'mdm.formula.plant'), (6,'mdm.formula.output'),
    (7,'mdm.formula.batchquantity'), (8,'mdm.formula.baseuom'), (9,'mdm.formula.revision'),
    (10,'mdm.formula.sequence'), (11,'mdm.formula.material'), (12,'mdm.formula.quantity'),
    (13,'mdm.formula.uom'), (14,'mdm.formula.removeitem'), (15,'mdm.formula.additem'),
    (16,'mdm.formula.release'), (17,'mdm.formula.obsolete'), (18,'mdm.formula.nextrevision'),
    (19,'mdm.formula.invalid'), (20,'mdm.formula.saved')
) AS seed(ord, key);

INSERT INTO mdm_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97400000000000052' || lpad((seed.ord * 2 - 1)::text, 2, '0'),
       '97300000000000052' || lpad(seed.ord::text, 2, '0'), 'zh-CN', seed.zh,
       0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'配方'), (2,'维护流程制造配方及不可变发布修订；不生成生产执行指令。'),
    (3,'配方身份按编码稳定排序，打开详情查看最新修订。'), (4,'暂无配方'),
    (5,'工厂'), (6,'产出物料'), (7,'基准批量'), (8,'基准单位'), (9,'业务修订'),
    (10,'顺序'), (11,'物料'), (12,'用量'), (13,'单位'), (14,'移除明细'),
    (15,'增加明细'), (16,'发布修订'), (17,'废止修订'), (18,'创建下一修订'),
    (19,'请填写有效字段：数量必须是最多 9 位小数的正数字符串，明细顺序不能重复。'),
    (20,'配方已保存')) AS seed(ord, zh);

INSERT INTO mdm_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97400000000000052' || lpad((seed.ord * 2)::text, 2, '0'),
       '97300000000000052' || lpad(seed.ord::text, 2, '0'), 'en-US', seed.en,
       0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'Formulas'), (2,'Manage process formulas and immutable released revisions; no execution instructions are created.'),
    (3,'Formulas are sorted by code. Open details to inspect the latest revision.'), (4,'No formulas yet'),
    (5,'Plant'), (6,'Output material'), (7,'Base batch quantity'), (8,'Base unit'), (9,'Business revision'),
    (10,'Sequence'), (11,'Material'), (12,'Quantity'), (13,'Unit'), (14,'Remove item'),
    (15,'Add item'), (16,'Release revision'), (17,'Obsolete revision'), (18,'Create next revision'),
    (19,'Enter valid fields: quantities must be positive decimal strings with at most 9 fractional digits and item sequences must be unique.'),
    (20,'Formula saved')) AS seed(ord, en);
