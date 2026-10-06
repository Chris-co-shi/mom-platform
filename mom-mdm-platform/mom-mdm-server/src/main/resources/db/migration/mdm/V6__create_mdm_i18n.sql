-- MDM 自有翻译表；无跨 Schema 外键，管理用例在本地事务中验证 Message 引用。
CREATE TABLE mdm_i18n_message_definition (
    id varchar(19) NOT NULL,
    namespace varchar(128) NOT NULL,
    message_key varchar(160) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_mdm_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_mdm_i18n_message_definition_key UNIQUE (namespace, message_key),
    CONSTRAINT ck_mdm_i18n_message_definition_namespace CHECK (
        namespace = 'mdm' OR namespace ~ '^mdm(?:\.[a-z][a-z0-9-]*)+$'),
    CONSTRAINT ck_mdm_i18n_message_definition_key CHECK (
        message_key ~ '^[a-zA-Z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)*$'),
    CONSTRAINT ck_mdm_i18n_message_definition_version CHECK (version >= 0)
);

CREATE INDEX ix_mdm_i18n_message_definition_runtime
    ON mdm_i18n_message_definition (namespace, enabled, message_key, id) WHERE NOT deleted;

CREATE TABLE mdm_i18n_translation (
    id varchar(19) NOT NULL,
    message_id varchar(19) NOT NULL,
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_mdm_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_mdm_i18n_translation_message_locale UNIQUE (message_id, locale_code),
    CONSTRAINT ck_mdm_i18n_translation_locale CHECK (
        locale_code ~ '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$'),
    CONSTRAINT ck_mdm_i18n_translation_text CHECK (length(message_text) BETWEEN 1 AND 4096 AND message_text !~ E'[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]' AND message_text !~ '<[^>]*>'),
    CONSTRAINT ck_mdm_i18n_translation_version CHECK (version >= 0)
);

CREATE INDEX ix_mdm_i18n_translation_runtime
    ON mdm_i18n_translation (locale_code, message_id, id) WHERE NOT deleted;

-- MDM V1 稳定业务错误；展示语言由请求 Locale 决定，code 不变。
INSERT INTO mdm_i18n_message_definition (id,namespace,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
    ('9710000000000000001','mdm','error.resource_not_found',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000002','mdm','error.code_conflict',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000003','mdm','error.dimension_conflict',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000004','mdm','error.version_conflict',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000005','mdm','error.parent_disabled',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000006','mdm','error.invalid_reference',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000007','mdm','error.immutable_master_data',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000008','mdm','error.resource_referenced',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000009','mdm','error.material_category_not_leaf',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000010','mdm','error.material_category_cascade_too_large',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000011','mdm','error.incompatible_uom',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000012','mdm','error.conversion_inexact',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000013','mdm','error.validation_failed',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9710000000000000014','mdm','error.invalid_conversion_rule',NULL,true,0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP);
INSERT INTO mdm_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES
    ('9720000000000000001','9710000000000000001','zh-CN','{0}不存在',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000002','9710000000000000001','en-US','The {0} does not exist',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000003','9710000000000000002','zh-CN','{0}编码已存在',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000004','9710000000000000002','en-US','The {0} code already exists',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000005','9710000000000000003','zh-CN','量纲编码或七维向量已存在',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000006','9710000000000000003','en-US','The dimension code or seven-dimensional vector already exists',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000007','9710000000000000004','zh-CN','主数据已被其他请求修改',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000008','9710000000000000004','en-US','The master data was changed by another request',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000009','9710000000000000005','zh-CN','{0}已停用',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000010','9710000000000000005','en-US','The parent {0} is disabled',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000011','9710000000000000006','zh-CN','主数据引用无效或引用链已变化',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000012','9710000000000000006','en-US','The master data reference is invalid or its hierarchy has changed',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000013','9710000000000000007','zh-CN','该主数据身份或状态不能通过当前操作修改',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000014','9710000000000000007','en-US','This master data identity or state cannot be changed by this operation',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000015','9710000000000000008','zh-CN','{0}仍被物料引用，不能执行当前操作',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000016','9710000000000000008','en-US','{0} is still referenced by a material and cannot be changed',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000017','9710000000000000009','zh-CN','Material 只能引用没有子分类的 MaterialCategory',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000018','9710000000000000009','en-US','A material may reference only a leaf material category',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000019','9710000000000000010','zh-CN','MaterialCategory 子树超过单次级联上限 {0}',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000020','9710000000000000010','en-US','The material category subtree exceeds the single-operation limit of {0}',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000021','9710000000000000011','zh-CN','两个计量单位不属于同一计量单位类别',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000022','9710000000000000011','en-US','The two units belong to different unit categories',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000023','9710000000000000012','zh-CN','换算结果无法按指定规则精确表示',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000024','9710000000000000012','en-US','The conversion result cannot be represented exactly under the specified rule',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000025','9710000000000000013','zh-CN','主数据请求参数不符合规则',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000026','9710000000000000013','en-US','The master data request does not satisfy the validation rules',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000027','9710000000000000014','zh-CN','换算规则、精度或舍入模式无效',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP),
    ('9720000000000000028','9710000000000000014','en-US','The conversion rule, precision, or rounding mode is invalid',0,false,'MDM_MIGRATION',CURRENT_TIMESTAMP,'MDM_MIGRATION',CURRENT_TIMESTAMP);
