-- ProductionVersion 页面与稳定业务错误文案由 MDM Owner 持有。
INSERT INTO mdm_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97300000000000054' || lpad(seed.ord::text, 2, '0'), seed.key, NULL,
       true, 0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'mdm.productionversion.title'), (2,'mdm.productionversion.description'),
    (3,'mdm.productionversion.listhint'), (4,'mdm.productionversion.nodata'),
    (5,'mdm.productionversion.businessversion'), (6,'mdm.productionversion.plant'),
    (7,'mdm.productionversion.output'), (8,'mdm.productionversion.formularevision'),
    (9,'mdm.productionversion.routingrevision'), (10,'mdm.productionversion.validity'),
    (11,'mdm.productionversion.validfrom'), (12,'mdm.productionversion.validto'),
    (13,'mdm.productionversion.release'), (14,'mdm.productionversion.obsolete'),
    (15,'mdm.productionversion.invalid'), (16,'mdm.productionversion.overlap'),
    (17,'mdm.productionversion.saved'), (18,'mdm.error.production_version_overlap')
) AS seed(ord,key);

INSERT INTO mdm_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '97400000000000054'||lpad((seed.ord*2-1)::text,2,'0'),
       '97300000000000054'||lpad(seed.ord::text,2,'0'),'zh-CN',seed.zh,0,false,
       'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
 (1,'生产版本'),(2,'组合已发布配方与工艺路线，并维护工厂业务日期有效期。'),
 (3,'已发布版本可供未来 MES 建单时读取并固定快照。'),(4,'暂无生产版本'),
 (5,'业务版本'),(6,'工厂'),(7,'产出物料'),(8,'配方修订'),(9,'路线修订'),
 (10,'有效期'),(11,'生效日期'),(12,'失效日期（不含）'),(13,'发布版本'),(14,'废止版本'),
 (15,'请完整填写字段；失效日期必须晚于生效日期。'),
 (16,'同一工厂和产出物料已有有效期重叠的已发布版本。'),(17,'生产版本已保存'),
 (18,'同一工厂和产出物料的已发布生产版本有效期不能重叠')) AS seed(ord,zh);

INSERT INTO mdm_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '97400000000000054'||lpad((seed.ord*2)::text,2,'0'),
       '97300000000000054'||lpad(seed.ord::text,2,'0'),'en-US',seed.en,0,false,
       'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
 (1,'Production Versions'),(2,'Combine released formula and routing revisions within plant business-date validity.'),
 (3,'Released versions can be read and snapshotted by future MES orders.'),(4,'No production versions yet'),
 (5,'Business version'),(6,'Plant'),(7,'Output material'),(8,'Formula revision'),(9,'Routing revision'),
 (10,'Validity'),(11,'Valid from'),(12,'Valid to (exclusive)'),(13,'Release version'),(14,'Obsolete version'),
 (15,'Complete all fields; valid-to must be later than valid-from.'),
 (16,'Another released version overlaps this plant, output material and validity range.'),(17,'Production version saved'),
 (18,'Released production-version validity cannot overlap for the same plant and output material')) AS seed(ord,en);
