INSERT INTO wms_i18n_message_definition
    (id, message_key, enabled, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('8000000000000000548', 'wms.error.opening_plant_timezone_missing', true, 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8000000000000000549', 'wms.error.opening_warehouse_unavailable', true, 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now());

INSERT INTO wms_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('8100000000000002095', '8000000000000000548', 'zh-CN',
     '工厂未配置时区。请在主数据中心补齐所属工厂时区后，再创建期初草稿。', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8100000000000002096', '8000000000000000548', 'en-US',
     'The plant has no time zone. Set the plant time zone in Master Data before creating the opening draft.', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8100000000000002097', '8000000000000000549', 'zh-CN',
     '仓库或所属工厂当前不可用于新业务，请在主数据中心检查工厂和仓库状态。', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8100000000000002098', '8000000000000000549', 'en-US',
     'The warehouse or its plant is unavailable for new operations. Check their status in Master Data.', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now());
