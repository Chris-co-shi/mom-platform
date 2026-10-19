-- 库存总览按仓库可见编码筛选，不再要求用户输入仓库技术 ID。
INSERT INTO wms_i18n_message_definition
    (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('8000000000000005000','wms.inventory.warehousecodesearch',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES
    ('8100000000000005000','8000000000000005000','zh-CN','输入仓库编码',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000005001','8000000000000005000','en-US','Enter warehouse code',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
