INSERT INTO mdm_i18n_message_definition
    (id,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9770000000000000'||lpad(row_number() OVER (ORDER BY key)::text,3,'0'),key,NULL,true,0,false,
       'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
    ('mdm.quality.spec.v101description'),
    ('mdm.quality.spec.usage.incoming'),
    ('mdm.quality.spec.usage.finished_goods')
) source(key);

INSERT INTO mdm_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9780000000000000'||lpad(row_number() OVER (ORDER BY source.key,source.locale)::text,3,'0'),definition.id,
       source.locale,source.text,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
    ('mdm.quality.spec.v101description','zh-CN','按工厂、物料和检验用途维护可追溯的质量标准修订。'),
    ('mdm.quality.spec.v101description','en-US','Maintain traceable quality specification revisions by plant, material and inspection usage.'),
    ('mdm.quality.spec.usage.incoming','zh-CN','来料'),
    ('mdm.quality.spec.usage.incoming','en-US','Incoming'),
    ('mdm.quality.spec.usage.finished_goods','zh-CN','成品'),
    ('mdm.quality.spec.usage.finished_goods','en-US','Finished goods')
) source(key,locale,text)
JOIN mdm_i18n_message_definition definition ON definition.message_key=source.key;
