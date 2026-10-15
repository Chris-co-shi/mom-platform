-- Plant 页面文案由 MDM 自有 i18n 数据维护，不修改 System 导航所有权。
INSERT INTO mdm_i18n_message_definition
    (id, message_key, description, enabled, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES
    ('9730000000000005101','mdm.plants.timezone',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9730000000000005102','mdm.plants.timezonehint',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9730000000000005103','mdm.plants.timezonemissing',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP);

INSERT INTO mdm_i18n_translation
    (id, message_id, locale_code, message_text, version, deleted,
     created_by, created_at, updated_by, updated_at)
VALUES
    ('9740000000000005101','9730000000000005101','zh-CN','工厂业务时区',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9740000000000005102','9730000000000005101','en-US','Plant Business Time Zone',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9740000000000005103','9730000000000005102','zh-CN','请输入 IANA 时区，如 Asia/Shanghai；存量工厂可在编辑时补齐。',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9740000000000005104','9730000000000005102','en-US','Enter an IANA zone such as Asia/Shanghai. Legacy plants can be completed here.',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9740000000000005105','9730000000000005103','zh-CN','待补齐',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9740000000000005106','9730000000000005103','en-US','Not set',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP);
