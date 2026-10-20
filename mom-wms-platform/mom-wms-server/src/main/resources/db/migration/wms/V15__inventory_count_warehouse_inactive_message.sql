-- 盘点创建和开始仅允许 WMS 库存生命周期为 ACTIVE 的仓库。
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('8000000000000007204','wms.error.count_warehouse_inactive',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('8100000000000007208','8000000000000007204','zh-CN','请先完成期初建账并启用仓库库存',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('8100000000000007209','8000000000000007204','en-US','Complete opening inventory and activate warehouse inventory before counting.',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
