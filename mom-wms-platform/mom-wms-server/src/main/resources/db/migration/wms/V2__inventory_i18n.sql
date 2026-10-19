-- WMS Owner 自有动态文案；翻译不进入 System 或 MDM Schema。
CREATE TABLE wms_i18n_message_definition (
    id varchar(19) NOT NULL,
    message_key varchar(160) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_wms_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_wms_i18n_message_definition_key UNIQUE (message_key),
    CONSTRAINT ck_wms_i18n_message_definition_version CHECK (version >= 0),
    CONSTRAINT ck_wms_i18n_message_definition_key CHECK (message_key ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$')
);
CREATE INDEX ix_wms_i18n_message_runtime ON wms_i18n_message_definition (enabled, message_key, id)
    WHERE NOT deleted;
CREATE TABLE wms_i18n_translation (
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
    CONSTRAINT pk_wms_i18n_translation PRIMARY KEY (id),
    CONSTRAINT ck_wms_i18n_translation_version CHECK (version >= 0),
    CONSTRAINT uk_wms_i18n_translation_message_locale UNIQUE (message_id, locale_code),
    CONSTRAINT ck_wms_i18n_translation_text CHECK (length(message_text) BETWEEN 1 AND 4096)
);
CREATE INDEX ix_wms_i18n_translation_runtime ON wms_i18n_translation (locale_code, message_id, id)
    WHERE NOT deleted;
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000001','wms.inventory.overview',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000002','8000000000000000001','zh-CN','实时库存总览',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000003','8000000000000000001','en-US','Live Inventory Overview',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000002','wms.inventory.overviewhint',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000004','8000000000000000002','zh-CN','按物料汇总当前非零库存；冻结量已包含在不可用量中',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000005','8000000000000000002','en-US','Current nonzero stock grouped by material. Frozen stock is included in unavailable stock.',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000003','wms.inventory.details',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000006','8000000000000000003','zh-CN','实时库存明细',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000007','8000000000000000003','en-US','Live Inventory Details',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000004','wms.inventory.detailshint',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000008','8000000000000000004','zh-CN','按批次、SN、库位及状态查看非零余额',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000009','8000000000000000004','en-US','Filter nonzero balances by batch, SN, location and status.',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000005','wms.inventory.material',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000010','8000000000000000005','zh-CN','物料',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000011','8000000000000000005','en-US','Material',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000006','wms.inventory.materialid',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000012','8000000000000000006','zh-CN','物料 ID',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000013','8000000000000000006','en-US','Material ID',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000007','wms.inventory.materialsearch',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000014','8000000000000000007','zh-CN','输入物料编码或名称',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000015','8000000000000000007','en-US','Search material code or name',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000008','wms.inventory.materialcode',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000016','8000000000000000008','zh-CN','物料编码',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000017','8000000000000000008','en-US','Material Code',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000009','wms.inventory.materialname',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000018','8000000000000000009','zh-CN','物料名称',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000019','8000000000000000009','en-US','Material Name',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000010','wms.inventory.warehouse',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000020','8000000000000000010','zh-CN','仓库',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000021','8000000000000000010','en-US','Warehouse',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000011','wms.inventory.warehouseid',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000022','8000000000000000011','zh-CN','仓库 ID',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000023','8000000000000000011','en-US','Warehouse ID',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000012','wms.inventory.location',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000024','8000000000000000012','zh-CN','库位',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000025','8000000000000000012','en-US','Location',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000013','wms.inventory.locationid',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000026','8000000000000000013','zh-CN','库位 ID',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000027','8000000000000000013','en-US','Location ID',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000014','wms.inventory.batch',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000028','8000000000000000014','zh-CN','批次',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000029','8000000000000000014','en-US','Batch',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000015','wms.inventory.sn',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000030','8000000000000000015','zh-CN','SN 标签码',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000031','8000000000000000015','en-US','SN Label',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000016','wms.inventory.qualitystatus',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000032','8000000000000000016','zh-CN','质量状态',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000033','8000000000000000016','en-US','Quality Status',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000017','wms.inventory.warehousestatus',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000034','8000000000000000017','zh-CN','仓储状态',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000035','8000000000000000017','en-US','Warehouse Status',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000018','wms.inventory.quantity',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000036','8000000000000000018','zh-CN','当前数量',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000037','8000000000000000018','en-US','Current Quantity',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000019','wms.inventory.total',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000038','8000000000000000019','zh-CN','实物总量',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000039','8000000000000000019','en-US','Total On Hand',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000020','wms.inventory.available',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000040','8000000000000000020','zh-CN','可用量',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000041','8000000000000000020','en-US','Available',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000021','wms.inventory.unavailable',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000042','8000000000000000021','zh-CN','不可用量',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000043','8000000000000000021','en-US','Unavailable',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000022','wms.inventory.frozen',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000044','8000000000000000022','zh-CN','其中冻结量',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000045','8000000000000000022','en-US','Of Which Frozen',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000023','wms.inventory.uom',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000046','8000000000000000023','zh-CN','计量单位',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000047','8000000000000000023','en-US','Unit',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000024','wms.inventory.viewdetails',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000048','8000000000000000024','zh-CN','查看明细',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000049','8000000000000000024','en-US','View Details',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000025','wms.inventory.back',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000050','8000000000000000025','zh-CN','返回总览',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000051','8000000000000000025','en-US','Back to Overview',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000026','wms.inventory.retry',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000052','8000000000000000026','zh-CN','重试',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000053','8000000000000000026','en-US','Retry',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000027','wms.inventory.forbidden',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000054','8000000000000000027','zh-CN','没有库存查询权限',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000055','8000000000000000027','en-US','Inventory read permission is required',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000028','wms.inventory.loadfailed',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000056','8000000000000000028','zh-CN','库存加载失败，请重试',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000057','8000000000000000028','en-US','Inventory failed to load. Please retry.',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000029','wms.inventory.all',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000058','8000000000000000029','zh-CN','全部',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000059','8000000000000000029','en-US','All',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000030','wms.inventory.quality.pending',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000060','8000000000000000030','zh-CN','待检',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000061','8000000000000000030','en-US','Pending',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000031','wms.inventory.quality.qualified',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000062','8000000000000000031','zh-CN','合格',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000063','8000000000000000031','en-US','Qualified',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000032','wms.inventory.quality.restricted',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000064','8000000000000000032','zh-CN','受限',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000065','8000000000000000032','en-US','Restricted',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000033','wms.inventory.quality.rejected',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000066','8000000000000000033','zh-CN','不合格',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000067','8000000000000000033','en-US','Rejected',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000034','wms.inventory.warehouse.normal',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000068','8000000000000000034','zh-CN','正常',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000069','8000000000000000034','en-US','Normal',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000035','wms.inventory.warehouse.frozen',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000070','8000000000000000035','zh-CN','冻结',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000071','8000000000000000035','en-US','Frozen',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_message_definition (id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8000000000000000099','wms.error.inventory_unavailable',true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000198','8000000000000000099','zh-CN','库存数据库暂不可用',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());
INSERT INTO wms_i18n_translation (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at) VALUES ('8100000000000000199','8000000000000000099','en-US','Inventory database is temporarily unavailable',0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now());

COMMENT ON TABLE wms_i18n_message_definition IS 'WMS 自有动态文案定义';
COMMENT ON COLUMN wms_i18n_message_definition.id IS '文案定义技术主键';
COMMENT ON COLUMN wms_i18n_message_definition.version IS '文案定义乐观锁版本';
COMMENT ON TABLE wms_i18n_translation IS 'WMS 自有动态译文';
COMMENT ON COLUMN wms_i18n_translation.id IS '译文技术主键';
COMMENT ON COLUMN wms_i18n_translation.message_id IS '所属文案定义 ID';
COMMENT ON COLUMN wms_i18n_translation.version IS '译文乐观锁版本';
