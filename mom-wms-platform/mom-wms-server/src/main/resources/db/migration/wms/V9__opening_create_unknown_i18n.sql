-- 期初单创建响应未知时禁止盲目重试，按仓库查询核对。
INSERT INTO wms_i18n_message_definition
    (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('8000000000000005001','wms.opening.createunknown',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES
    ('8100000000000005002','8000000000000005001','zh-CN','创建结果未知，请按仓库刷新核对单据，勿重复创建',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()),
    ('8100000000000005003','8000000000000005001','en-US','Creation result is unknown. Refresh and check the document for this warehouse before creating again.',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
