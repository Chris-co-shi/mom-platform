-- B2 界面文案：仅本服务既有 Runtime 表；新增版本，不改历史迁移或业务模型。
INSERT INTO auth_i18n_message_definition (id,namespace,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9430000000000009001','auth.iam','iam.grants.queryResources','查询资源',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9430000000000009002','auth.iam','iam.grants.changed','有变更',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9430000000000009003','auth.iam','iam.grants.backDirectory','返回资源目录',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9430000000000009004','auth.iam','iam.grants.allResources','跨资源搜索',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9430000000000009005','auth.iam','iam.grants.discardTitle','放弃未保存的权限变更？',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9430000000000009006','auth.iam','iam.grants.discardHint','关闭后本次新增和移除将不会保存。',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9430000000000009007','auth.iam','iam.grants.discard','放弃变更',true,0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO auth_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9440000000000009001','9430000000000009001','zh-CN','查询资源',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009002','9430000000000009001','en-US','Find resources',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009003','9430000000000009002','zh-CN','有变更',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009004','9430000000000009002','en-US','Changed',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009005','9430000000000009003','zh-CN','返回资源目录',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009006','9430000000000009003','en-US','Back to resources',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009007','9430000000000009004','zh-CN','跨资源搜索',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009008','9430000000000009004','en-US','Search across resources',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009009','9430000000000009005','zh-CN','放弃未保存的权限变更？',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009010','9430000000000009005','en-US','Discard unsaved permission changes?',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009011','9430000000000009006','zh-CN','关闭后本次新增和移除将不会保存。',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009012','9430000000000009006','en-US','Closing will discard additions and removals in this draft.',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009013','9430000000000009007','zh-CN','放弃变更',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP),
('9440000000000009014','9430000000000009007','en-US','Discard changes',0,false,'B2_UI_MIGRATION',CURRENT_TIMESTAMP,'B2_UI_MIGRATION',CURRENT_TIMESTAMP);

-- 精确修改登录文案；保留定义身份和启用状态，递增译文版本及审计时间。
UPDATE auth_i18n_translation AS tr
SET message_text = v.message_text, version = tr.version + 1, updated_by = 'B2_UI_MIGRATION', updated_at = CURRENT_TIMESTAMP
FROM auth_i18n_message_definition AS d,
(VALUES
('auth.layout.eyebrow','zh-CN','MOM 制造运营管理平台'),
('auth.layout.eyebrow','en-US','MOM Manufacturing Operations Management'),
('auth.layout.heroTitle','zh-CN','制造运营，协同有序'),
('auth.layout.heroTitle','en-US','Manufacturing operations, working together'),
('auth.layout.heroDescription','zh-CN','统一管理主数据、平台配置与访问权限，为制造运营提供清晰、一致的工作入口。'),
('auth.layout.heroDescription','en-US','Manage master data, platform configuration and access permissions in one consistent workspace.'),
('auth.login.welcome','zh-CN','登录工作台'),
('auth.login.welcome','en-US','Sign in to your workspace'),
('auth.login.description','zh-CN','使用平台账号登录 MOM 制造运营管理平台。'),
('auth.login.description','en-US','Use your platform account to sign in to MOM.'),
('auth.login.submit','zh-CN','登录工作台'),
('auth.login.submit','en-US','Sign in'),
('auth.login.sessionTitle','zh-CN','账号使用提示'),
('auth.login.sessionTitle','en-US','Account guidance'),
('auth.login.sessionDescription','zh-CN','请妥善保管账号与密码，使用共享设备时请在离开前退出登录。'),
('auth.login.sessionDescription','en-US','Keep your credentials private. Sign out before leaving a shared device.')
) AS v(message_key,locale_code,message_text)
WHERE tr.message_id = d.id AND d.namespace = 'auth.login' AND d.message_key = v.message_key
AND tr.locale_code = v.locale_code AND NOT tr.deleted AND NOT d.deleted;
