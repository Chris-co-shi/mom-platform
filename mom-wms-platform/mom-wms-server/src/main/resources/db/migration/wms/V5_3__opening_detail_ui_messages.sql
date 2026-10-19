INSERT INTO wms_i18n_message_definition
    (id, message_key, enabled, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('8000000000000000550', 'wms.opening.emptylines', true, 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8000000000000000551', 'wms.opening.postrequireslines', true, 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now());

INSERT INTO wms_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('8100000000000002099', '8000000000000000550', 'zh-CN',
     '当前单据还没有库存明细。请在上方录入物料、库位和数量，再新增明细。', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8100000000000002100', '8000000000000000550', 'en-US',
     'This document has no inventory lines yet. Enter a material, location, and quantity above, then add the line.', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8100000000000002101', '8000000000000000551', 'zh-CN',
     '至少新增一条库存明细后，才能确认并过账。', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now()),
    ('8100000000000002102', '8000000000000000551', 'en-US',
     'Add at least one inventory line before confirming and posting.', 0, false,
     'WMS_MIGRATION', now(), 'WMS_MIGRATION', now());
