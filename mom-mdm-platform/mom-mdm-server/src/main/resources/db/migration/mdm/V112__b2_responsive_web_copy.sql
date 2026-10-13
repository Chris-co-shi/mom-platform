-- B2 界面文案：仅本服务既有 Runtime 表；新增版本，不改历史迁移或业务模型。
INSERT INTO mdm_i18n_message_definition (id,namespace,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
VALUES ('9730000000000090100','mdm.web','mdm.struct.back','返回上级目录',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO mdm_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9740000000000090100','9730000000000090100','zh-CN','返回上级',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9740000000000090101','9730000000000090100','en-US','Back to parent',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO mdm_i18n_message_definition (id,namespace,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9730000000000009001','mdm.web','mdm.material.allCategories','全部分类',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO mdm_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9740000000000009001','9730000000000009001','zh-CN','全部分类',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9740000000000009002','9730000000000009001','en-US','All categories',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);
