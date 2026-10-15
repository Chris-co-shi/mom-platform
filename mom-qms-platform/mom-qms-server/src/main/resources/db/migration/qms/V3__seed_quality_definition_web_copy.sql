INSERT INTO qms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9811000000000000' || lpad(row_number() OVER (ORDER BY key)::text,3,'0'), key, true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
('qms.common.title'),('qms.common.description'),('qms.common.noData'),('qms.common.defaultUom'),('qms.common.methodDescription'),('qms.common.invalid'),
('qms.spec.title'),('qms.spec.description'),('qms.spec.noData'),('qms.spec.plant'),('qms.spec.material'),('qms.spec.usage'),('qms.spec.revision'),
('qms.spec.sequence'),('qms.spec.characteristic'),('qms.spec.method'),('qms.spec.uom'),('qms.spec.lower'),('qms.spec.target'),('qms.spec.upper'),
('qms.spec.mandatory'),('qms.spec.addItem'),('qms.spec.removeItem'),('qms.spec.release'),('qms.spec.obsolete'),('qms.spec.nextRevision'),
('qms.spec.invalid'),('qms.spec.saved')) source(key);

INSERT INTO qms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9821000000000000' || lpad(row_number() OVER (ORDER BY source.key,source.locale)::text,3,'0'), definition.id,
 source.locale, source.text,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
('qms.common.title','zh-CN','质量基础定义'),('qms.common.title','en-US','Quality Definition'),
('qms.common.description','zh-CN','维护 QMS 权威的检验特性、方法与标准定义，不包含检验执行结果。'),('qms.common.description','en-US','Maintain QMS-owned characteristics, methods, and specifications; inspection results are excluded.'),
('qms.common.noData','zh-CN','暂无质量定义'),('qms.common.noData','en-US','No quality definitions'),
('qms.common.defaultUom','zh-CN','默认单位'),('qms.common.defaultUom','en-US','Default UOM'),
('qms.common.methodDescription','zh-CN','方法说明'),('qms.common.methodDescription','en-US','Method description'),
('qms.common.invalid','zh-CN','请填写完整且有效的质量定义'),('qms.common.invalid','en-US','Complete all required quality definition fields'),
('qms.spec.title','zh-CN','质量标准'),('qms.spec.title','en-US','Quality Specifications'),
('qms.spec.description','zh-CN','按工厂、物料和来料用途维护可追溯的质量标准修订。'),('qms.spec.description','en-US','Maintain traceable incoming quality specification revisions by plant and material.'),
('qms.spec.noData','zh-CN','暂无质量标准'),('qms.spec.noData','en-US','No quality specifications'),
('qms.spec.plant','zh-CN','工厂'),('qms.spec.plant','en-US','Plant'),
('qms.spec.material','zh-CN','物料'),('qms.spec.material','en-US','Material'),
('qms.spec.usage','zh-CN','检验用途'),('qms.spec.usage','en-US','Inspection usage'),
('qms.spec.revision','zh-CN','业务修订'),('qms.spec.revision','en-US','Business revision'),
('qms.spec.sequence','zh-CN','顺序'),('qms.spec.sequence','en-US','Sequence'),
('qms.spec.characteristic','zh-CN','检验特性'),('qms.spec.characteristic','en-US','Characteristic'),
('qms.spec.method','zh-CN','检验方法'),('qms.spec.method','en-US','Method'),
('qms.spec.uom','zh-CN','单位'),('qms.spec.uom','en-US','UOM'),
('qms.spec.lower','zh-CN','下限'),('qms.spec.lower','en-US','Lower limit'),
('qms.spec.target','zh-CN','目标值'),('qms.spec.target','en-US','Target'),
('qms.spec.upper','zh-CN','上限'),('qms.spec.upper','en-US','Upper limit'),
('qms.spec.mandatory','zh-CN','必检'),('qms.spec.mandatory','en-US','Mandatory'),
('qms.spec.addItem','zh-CN','添加检验项'),('qms.spec.addItem','en-US','Add item'),
('qms.spec.removeItem','zh-CN','移除'),('qms.spec.removeItem','en-US','Remove'),
('qms.spec.release','zh-CN','发布'),('qms.spec.release','en-US','Release'),
('qms.spec.obsolete','zh-CN','废止'),('qms.spec.obsolete','en-US','Obsolete'),
('qms.spec.nextRevision','zh-CN','创建下一修订'),('qms.spec.nextRevision','en-US','Create next revision'),
('qms.spec.invalid','zh-CN','检验项必须完整、顺序唯一，范围使用 Decimal String 且有序'),('qms.spec.invalid','en-US','Items must be complete with unique sequence and ordered Decimal String limits'),
('qms.spec.saved','zh-CN','质量标准已保存'),('qms.spec.saved','en-US','Quality specification saved')) source(key,locale,text)
JOIN qms_i18n_message_definition definition ON definition.message_key=source.key;
