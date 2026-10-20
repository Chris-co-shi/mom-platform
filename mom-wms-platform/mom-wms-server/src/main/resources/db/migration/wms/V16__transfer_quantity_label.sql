-- 库内移动的本次请求数量，区别于余额当前数量。
INSERT INTO wms_i18n_message_definition
 (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('8200000000000000001','wms.inventory.transfer.moveQuantity',true,0,false,'WMS_MIGRATION',CURRENT_TIMESTAMP,'WMS_MIGRATION',CURRENT_TIMESTAMP);

INSERT INTO wms_i18n_translation
 (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES
('8200000000000001001','8200000000000000001','zh-CN','本次移动数量',0,false,'WMS_MIGRATION',CURRENT_TIMESTAMP,'WMS_MIGRATION',CURRENT_TIMESTAMP),
('8200000000000001002','8200000000000000001','en-US','Quantity to Move',0,false,'WMS_MIGRATION',CURRENT_TIMESTAMP,'WMS_MIGRATION',CURRENT_TIMESTAMP);
