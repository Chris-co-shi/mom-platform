INSERT INTO mdm_i18n_message_definition
    (id,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9750000000000000'||lpad(row_number() OVER (ORDER BY key)::text,3,'0'),key,NULL,true,0,false,
       'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
    ('mdm.error.quality_invalid_range'),
    ('mdm.quality.common.title'),('mdm.quality.common.description'),('mdm.quality.common.nodata'),
    ('mdm.quality.common.defaultuom'),('mdm.quality.common.methoddescription'),('mdm.quality.common.invalid'),
    ('mdm.quality.spec.title'),('mdm.quality.spec.description'),('mdm.quality.spec.nodata'),
    ('mdm.quality.spec.plant'),('mdm.quality.spec.material'),('mdm.quality.spec.usage'),
    ('mdm.quality.spec.revision'),('mdm.quality.spec.sequence'),('mdm.quality.spec.characteristic'),
    ('mdm.quality.spec.method'),('mdm.quality.spec.uom'),('mdm.quality.spec.lower'),
    ('mdm.quality.spec.target'),('mdm.quality.spec.upper'),('mdm.quality.spec.mandatory'),
    ('mdm.quality.spec.additem'),('mdm.quality.spec.removeitem'),('mdm.quality.spec.release'),
    ('mdm.quality.spec.obsolete'),('mdm.quality.spec.nextrevision'),('mdm.quality.spec.invalid'),
    ('mdm.quality.spec.saved'),('mdm.governance.quality')
) source(key);

INSERT INTO mdm_i18n_translation
    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9760000000000000'||lpad(row_number() OVER (ORDER BY source.key,source.locale)::text,3,'0'),definition.id,
       source.locale,source.text,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
    ('mdm.error.quality_invalid_range','zh-CN','检验范围必须使用 Decimal String 且满足下限≤目标值≤上限'),
    ('mdm.error.quality_invalid_range','en-US','Inspection limits must be Decimal Strings ordered as lower ≤ target ≤ upper'),
    ('mdm.quality.common.title','zh-CN','质量基础定义'),('mdm.quality.common.title','en-US','Quality Definition'),
    ('mdm.quality.common.description','zh-CN','维护 MDM 权威的检验特性、方法与质量标准，不包含检验执行结果。'),
    ('mdm.quality.common.description','en-US','Maintain MDM-owned characteristics, methods, and specifications; inspection execution and results are excluded.'),
    ('mdm.quality.common.nodata','zh-CN','暂无质量定义'),('mdm.quality.common.nodata','en-US','No quality definitions'),
    ('mdm.quality.common.defaultuom','zh-CN','默认单位'),('mdm.quality.common.defaultuom','en-US','Default UOM'),
    ('mdm.quality.common.methoddescription','zh-CN','方法说明'),('mdm.quality.common.methoddescription','en-US','Method description'),
    ('mdm.quality.common.invalid','zh-CN','请填写完整且有效的质量定义'),('mdm.quality.common.invalid','en-US','Complete all required quality definition fields'),
    ('mdm.quality.spec.title','zh-CN','质量标准'),('mdm.quality.spec.title','en-US','Quality Specifications'),
    ('mdm.quality.spec.description','zh-CN','按工厂、物料和来料用途维护可追溯的质量标准修订。'),
    ('mdm.quality.spec.description','en-US','Maintain traceable incoming quality specification revisions by plant and material.'),
    ('mdm.quality.spec.nodata','zh-CN','暂无质量标准'),('mdm.quality.spec.nodata','en-US','No quality specifications'),
    ('mdm.quality.spec.plant','zh-CN','工厂'),('mdm.quality.spec.plant','en-US','Plant'),
    ('mdm.quality.spec.material','zh-CN','物料'),('mdm.quality.spec.material','en-US','Material'),
    ('mdm.quality.spec.usage','zh-CN','检验用途'),('mdm.quality.spec.usage','en-US','Inspection usage'),
    ('mdm.quality.spec.revision','zh-CN','业务修订'),('mdm.quality.spec.revision','en-US','Business revision'),
    ('mdm.quality.spec.sequence','zh-CN','顺序'),('mdm.quality.spec.sequence','en-US','Sequence'),
    ('mdm.quality.spec.characteristic','zh-CN','检验特性'),('mdm.quality.spec.characteristic','en-US','Characteristic'),
    ('mdm.quality.spec.method','zh-CN','检验方法'),('mdm.quality.spec.method','en-US','Method'),
    ('mdm.quality.spec.uom','zh-CN','单位'),('mdm.quality.spec.uom','en-US','UOM'),
    ('mdm.quality.spec.lower','zh-CN','下限'),('mdm.quality.spec.lower','en-US','Lower limit'),
    ('mdm.quality.spec.target','zh-CN','目标值'),('mdm.quality.spec.target','en-US','Target'),
    ('mdm.quality.spec.upper','zh-CN','上限'),('mdm.quality.spec.upper','en-US','Upper limit'),
    ('mdm.quality.spec.mandatory','zh-CN','必检'),('mdm.quality.spec.mandatory','en-US','Mandatory'),
    ('mdm.quality.spec.additem','zh-CN','添加检验项'),('mdm.quality.spec.additem','en-US','Add item'),
    ('mdm.quality.spec.removeitem','zh-CN','移除'),('mdm.quality.spec.removeitem','en-US','Remove'),
    ('mdm.quality.spec.release','zh-CN','发布'),('mdm.quality.spec.release','en-US','Release'),
    ('mdm.quality.spec.obsolete','zh-CN','废止'),('mdm.quality.spec.obsolete','en-US','Obsolete'),
    ('mdm.quality.spec.nextrevision','zh-CN','创建下一修订'),('mdm.quality.spec.nextrevision','en-US','Create next revision'),
    ('mdm.quality.spec.invalid','zh-CN','检验项必须完整、顺序唯一，范围使用 Decimal String 且有序'),
    ('mdm.quality.spec.invalid','en-US','Items must be complete with unique sequence and ordered Decimal String limits'),
    ('mdm.quality.spec.saved','zh-CN','质量标准已保存'),('mdm.quality.spec.saved','en-US','Quality specification saved'),
    ('mdm.governance.quality','zh-CN','质量基础定义'),('mdm.governance.quality','en-US','Quality definition')
) source(key,locale,text)
JOIN mdm_i18n_message_definition definition ON definition.message_key=source.key;
