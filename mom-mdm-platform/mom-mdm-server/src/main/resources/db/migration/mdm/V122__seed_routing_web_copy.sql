-- Routing 页面文案由 MDM Owner 持有。
INSERT INTO mdm_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97300000000000053' || lpad(seed.ord::text, 2, '0'), seed.key, NULL,
       true, 0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'mdm.routing.title'), (2,'mdm.routing.description'), (3,'mdm.routing.listhint'),
    (4,'mdm.routing.nodata'), (5,'mdm.routing.plant'), (6,'mdm.routing.revision'),
    (7,'mdm.routing.sequence'), (8,'mdm.routing.operationcode'),
    (9,'mdm.routing.resourcetype'), (10,'mdm.routing.resource'),
    (11,'mdm.routing.line'), (12,'mdm.routing.station'), (13,'mdm.routing.removestep'),
    (14,'mdm.routing.addstep'), (15,'mdm.routing.release'), (16,'mdm.routing.obsolete'),
    (17,'mdm.routing.nextrevision'), (18,'mdm.routing.invalid'), (19,'mdm.routing.saved')
) AS seed(ord, key);

INSERT INTO mdm_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97400000000000053' || lpad((seed.ord * 2 - 1)::text, 2, '0'),
       '97300000000000053' || lpad(seed.ord::text, 2, '0'), 'zh-CN', seed.zh,
       0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'工艺路线'), (2,'维护工序顺序及产线/工位引用；不管理排程和执行。'),
    (3,'路线按编码排序，打开详情查看最新业务修订。'), (4,'暂无工艺路线'),
    (5,'工厂'), (6,'业务修订'), (7,'顺序'), (8,'工序编码'), (9,'资源类型'),
    (10,'制造资源'), (11,'产线'), (12,'工位'), (13,'移除工序'), (14,'增加工序'),
    (15,'发布修订'), (16,'废止修订'), (17,'创建下一修订'),
    (18,'请填写工厂和至少一道工序；工序顺序必须是互不重复的正整数。'),
    (19,'工艺路线已保存')) AS seed(ord, zh);

INSERT INTO mdm_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
SELECT '97400000000000053' || lpad((seed.ord * 2)::text, 2, '0'),
       '97300000000000053' || lpad(seed.ord::text, 2, '0'), 'en-US', seed.en,
       0, false, 'MDM_MIGRATION', CURRENT_TIMESTAMP, 'MDM_MIGRATION', CURRENT_TIMESTAMP
FROM (VALUES
    (1,'Routings'), (2,'Manage operation order and line/workstation references; no scheduling or execution.'),
    (3,'Routings are sorted by code. Open details to inspect the latest revision.'), (4,'No routings yet'),
    (5,'Plant'), (6,'Business revision'), (7,'Sequence'), (8,'Operation code'), (9,'Resource type'),
    (10,'Manufacturing resource'), (11,'Production line'), (12,'Workstation'), (13,'Remove step'), (14,'Add step'),
    (15,'Release revision'), (16,'Obsolete revision'), (17,'Create next revision'),
    (18,'Enter a plant and at least one step; step sequences must be unique positive integers.'),
    (19,'Routing saved')) AS seed(ord, en);
