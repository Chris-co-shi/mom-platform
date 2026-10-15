INSERT INTO mdm_i18n_message_definition (id,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9731000000000000'||lpad(row_number() OVER (ORDER BY key)::text,3,'0'),key,NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES ('mdm.governance.title'),('mdm.governance.description'),('mdm.governance.run'),('mdm.governance.scope'),
('mdm.governance.all'),('mdm.governance.organization'),('mdm.governance.warehouse'),('mdm.governance.materialuom'),
('mdm.governance.manufacturing'),('mdm.governance.noissues'),('mdm.governance.issuetype'),('mdm.governance.resource'),
('mdm.governance.businesscode'),('mdm.governance.severity'),('mdm.governance.reason'),('mdm.governance.checkedat'),
('mdm.governance.truncated')) source(key);
INSERT INTO mdm_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '9741000000000000'||lpad(row_number() OVER (ORDER BY source.key,source.locale)::text,3,'0'),definition.id,source.locale,source.text,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP
FROM (VALUES
('mdm.governance.title','zh-CN','一致性检查'),('mdm.governance.title','en-US','Consistency Checks'),
('mdm.governance.description','zh-CN','按需扫描当前主数据引用与父链，只报告问题，不自动修改数据。'),('mdm.governance.description','en-US','Scan current master-data references and hierarchies on demand without automatic repair.'),
('mdm.governance.run','zh-CN','运行检查'),('mdm.governance.run','en-US','Run check'),('mdm.governance.scope','zh-CN','检查范围'),('mdm.governance.scope','en-US','Scope'),
('mdm.governance.all','zh-CN','全部板块'),('mdm.governance.all','en-US','All areas'),('mdm.governance.organization','zh-CN','组织与制造资源'),('mdm.governance.organization','en-US','Organization and resources'),
('mdm.governance.warehouse','zh-CN','仓储与物流基础'),('mdm.governance.warehouse','en-US','Warehouse and logistics'),('mdm.governance.materialuom','zh-CN','物料与计量'),('mdm.governance.materialuom','en-US','Material and UOM'),
('mdm.governance.manufacturing','zh-CN','产品与制造定义'),('mdm.governance.manufacturing','en-US','Manufacturing definition'),('mdm.governance.noissues','zh-CN','未发现一致性问题'),('mdm.governance.noissues','en-US','No consistency issues found'),
('mdm.governance.issuetype','zh-CN','问题类型'),('mdm.governance.issuetype','en-US','Issue type'),('mdm.governance.resource','zh-CN','资源'),('mdm.governance.resource','en-US','Resource'),
('mdm.governance.businesscode','zh-CN','业务编码'),('mdm.governance.businesscode','en-US','Business code'),('mdm.governance.severity','zh-CN','严重度'),('mdm.governance.severity','en-US','Severity'),
('mdm.governance.reason','zh-CN','稳定原因'),('mdm.governance.reason','en-US','Stable reason'),('mdm.governance.checkedat','zh-CN','检查时间'),('mdm.governance.checkedat','en-US','Checked at'),
('mdm.governance.truncated','zh-CN','问题过多，结果已截断'),('mdm.governance.truncated','en-US','Too many issues; results were truncated')) source(key,locale,text)
JOIN mdm_i18n_message_definition definition ON definition.message_key=source.key;
