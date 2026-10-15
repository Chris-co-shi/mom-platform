CREATE TABLE qms_i18n_message_definition (
    id varchar(19) NOT NULL, message_key varchar(160) NOT NULL, description varchar(1000),
    enabled boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0, deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL, created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL, updated_at timestamptz NOT NULL,
    CONSTRAINT pk_qms_i18n_message PRIMARY KEY (id),
    CONSTRAINT uk_qms_i18n_message_key UNIQUE (message_key),
    CONSTRAINT ck_qms_i18n_message_key CHECK (message_key ~ '^qms(?:[._-][a-zA-Z0-9]+)+$'),
    CONSTRAINT ck_qms_i18n_message_version CHECK (version >= 0)
);
CREATE INDEX ix_qms_i18n_message_runtime ON qms_i18n_message_definition (enabled, message_key, id) WHERE NOT deleted;
CREATE TABLE qms_i18n_translation (
    id varchar(19) NOT NULL, message_id varchar(19) NOT NULL, locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL, version bigint NOT NULL DEFAULT 0, deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL, created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL, updated_at timestamptz NOT NULL,
    CONSTRAINT pk_qms_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_qms_i18n_translation_locale UNIQUE (message_id, locale_code),
    CONSTRAINT ck_qms_i18n_translation_locale CHECK (locale_code ~ '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$'),
    CONSTRAINT ck_qms_i18n_translation_text CHECK (length(message_text) BETWEEN 1 AND 4096 AND message_text !~ '<[^>]*>'),
    CONSTRAINT ck_qms_i18n_translation_version CHECK (version >= 0)
);
CREATE INDEX ix_qms_i18n_translation_runtime ON qms_i18n_translation (locale_code, message_id, id) WHERE NOT deleted;

INSERT INTO qms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9810000000000000001','qms.invalid_reference',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000002','qms.invalid_range',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000003','qms.resource_not_found',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000004','qms.code_or_scope_conflict',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000005','qms.version_conflict',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000006','qms.immutable_definition',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000007','qms.disabled_reference',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9810000000000000008','qms.mdm_reference_unavailable',true,0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO qms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
('9820000000000000001','9810000000000000001','zh-CN','质量定义引用无效或引用链已变化',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000002','9810000000000000001','en-US','The quality definition reference is invalid or has changed',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000003','9810000000000000002','zh-CN','检验范围必须使用 Decimal String 且满足下限≤目标值≤上限',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000004','9810000000000000002','en-US','Inspection limits must be Decimal Strings ordered as lower ≤ target ≤ upper',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000005','9810000000000000003','zh-CN','质量定义不存在',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000006','9810000000000000003','en-US','The quality definition does not exist',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000007','9810000000000000004','zh-CN','编码或工厂、物料、用途组合已存在',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000008','9810000000000000004','en-US','The code or plant/material/usage scope already exists',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000009','9810000000000000005','zh-CN','质量定义已被其他请求修改',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000010','9810000000000000005','en-US','The quality definition was changed by another request',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000011','9810000000000000006','zh-CN','已发布或废止的质量定义不可通过当前操作修改',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000012','9810000000000000006','en-US','A released or obsolete quality definition cannot be changed by this operation',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000013','9810000000000000007','zh-CN','引用的主数据、检验特性或检验方法已停用',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000014','9810000000000000007','en-US','A referenced master record, characteristic, or method is disabled',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000015','9810000000000000008','zh-CN','MDM 引用校验暂时不可用，质量定义未保存',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP),
('9820000000000000016','9810000000000000008','en-US','MDM reference validation is unavailable; the quality definition was not saved',0,false,'QMS_MIGRATION',CURRENT_TIMESTAMP,'QMS_MIGRATION',CURRENT_TIMESTAMP);
