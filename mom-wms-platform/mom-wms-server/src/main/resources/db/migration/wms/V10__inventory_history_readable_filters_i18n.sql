-- 库存历史查询的可识别筛选条件与折叠区文案，归 WMS Owner。
INSERT INTO wms_i18n_message_definition
    (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES
    ('8000000000000006000','wms.inventory.locationcodesearch',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8000000000000006001','wms.inventory.history.morefilters',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8000000000000006002','wms.inventory.history.inherited',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());

INSERT INTO wms_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES
    ('8100000000000006000','8000000000000006000','zh-CN','输入库位编码',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000006001','8000000000000006000','en-US','Enter location code',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000006002','8000000000000006001','zh-CN','更多筛选条件',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000006003','8000000000000006001','en-US','More filters',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000006004','8000000000000006002','zh-CN','已继承上一页的范围；重置条件可清除',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000006005','8000000000000006002','en-US','Scope inherited from the previous page; reset filters to clear it',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
