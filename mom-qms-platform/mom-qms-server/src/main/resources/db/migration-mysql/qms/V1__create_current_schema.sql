-- qms：根据 PostgreSQL 17 空库 Flyway 迁移至 V6 后的有效结构建立 MySQL 8.4 基线。

CREATE TABLE qms_i18n_message_definition (
    id varchar(19) NOT NULL COMMENT 'QMS 消息定义的稳定技术 ID',
    message_key varchar(160) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT '消息定义的技术乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_qms_i18n_message_definition_key CHECK (((regexp_like(message_key, '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*\\z', 'c')) AND ((char_length((message_key)) >= 1) AND (char_length((message_key)) <= 160)))),
    CONSTRAINT ck_qms_i18n_message_definition_version CHECK ((version >= 0)),
    CONSTRAINT pk_qms_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_qms_i18n_message_definition_key UNIQUE (message_key),
    KEY ix_qms_i18n_message_runtime (deleted, enabled, message_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='QMS 所有的国际化消息定义，保留停用和逻辑删除历史';

CREATE TABLE qms_i18n_translation (
    id varchar(19) NOT NULL COMMENT 'QMS 译文的稳定技术 ID',
    message_id varchar(19) NOT NULL COMMENT '所属 QMS 消息定义 ID，无物理外键',
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL,
    version bigint NOT NULL DEFAULT 0 COMMENT '译文的技术乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_qms_i18n_translation_locale CHECK ((regexp_like(locale_code, '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?\\z', 'c'))),
    CONSTRAINT ck_qms_i18n_translation_text CHECK ((((char_length((message_text)) >= 1) AND (char_length((message_text)) <= 4096)) AND (NOT regexp_like(message_text, '<[^>]*>', 'c')))),
    CONSTRAINT ck_qms_i18n_translation_version CHECK ((version >= 0)),
    CONSTRAINT pk_qms_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_qms_i18n_translation_locale UNIQUE (message_id, locale_code),
    KEY ix_qms_i18n_translation_runtime (deleted, locale_code, message_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='QMS 消息定义对应的本地化译文及其审计历史';

CREATE TABLE qms_inspection_characteristic (
    id varchar(19) NOT NULL COMMENT '已退役检验特性的历史技术 ID，现权威位于 MDM',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    default_uom_id varchar(19) NOT NULL COMMENT '历史默认计量单位的 MDM ID 快照引用',
    default_uom_version bigint NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT '历史检验特性的技术乐观锁版本',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_qms_inspection_characteristic_uom_version CHECK ((default_uom_version >= 0)),
    CONSTRAINT ck_qms_inspection_characteristic_version CHECK ((version >= 0)),
    CONSTRAINT pk_qms_inspection_characteristic PRIMARY KEY (id),
    CONSTRAINT uk_qms_inspection_characteristic_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='LEGACY READ-ONLY DATA: 检验特性权威已转移至 MDM，QMS 运行时不再读写本表';

CREATE TABLE qms_inspection_method (
    id varchar(19) NOT NULL COMMENT '已退役检验方法的历史技术 ID，现权威位于 MDM',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT '历史检验方法的技术乐观锁版本',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_qms_inspection_method_version CHECK ((version >= 0)),
    CONSTRAINT pk_qms_inspection_method PRIMARY KEY (id),
    CONSTRAINT uk_qms_inspection_method_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='LEGACY READ-ONLY DATA: 检验方法权威已转移至 MDM，QMS 运行时不再读写本表';

CREATE TABLE qms_quality_specification (
    id varchar(19) NOT NULL COMMENT '已退役质量标准身份的历史技术 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL COMMENT '历史标准适用的 MDM 工厂 ID 快照引用',
    material_id varchar(19) NOT NULL COMMENT '历史标准适用的 MDM 物料 ID 快照引用',
    inspection_usage varchar(16) NOT NULL,
    plant_version bigint NOT NULL,
    material_revision_no int NOT NULL,
    material_version bigint NOT NULL,
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_qms_quality_specification_usage CHECK (((inspection_usage) = 'INCOMING')),
    CONSTRAINT pk_qms_quality_specification PRIMARY KEY (id),
    CONSTRAINT uk_qms_quality_specification_code UNIQUE (code),
    CONSTRAINT uk_qms_quality_specification_scope UNIQUE (plant_id, material_id, inspection_usage),
    KEY ix_qms_quality_specification_material (material_id, plant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='LEGACY READ-ONLY DATA: 质量标准权威已转移至 MDM，本表仅保留历史数据';

CREATE TABLE qms_specification_item (
    id varchar(19) NOT NULL COMMENT '已退役质量标准条目的历史技术 ID',
    revision_id varchar(19) NOT NULL COMMENT '所属历史质量标准业务修订 ID',
    sequence_no int NOT NULL,
    characteristic_id varchar(19) NOT NULL COMMENT '历史检验特性 ID 快照引用',
    characteristic_version bigint NOT NULL,
    method_id varchar(19) NOT NULL COMMENT '历史检验方法 ID 快照引用',
    method_version bigint NOT NULL,
    uom_id varchar(19) NOT NULL COMMENT '历史检验限值对应的 MDM 计量单位 ID 快照引用',
    uom_version bigint NOT NULL,
    lower_limit decimal(24,9),
    target_value decimal(24,9),
    upper_limit decimal(24,9),
    mandatory boolean NOT NULL DEFAULT true,
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT ck_qms_specification_item_range CHECK ((((lower_limit IS NULL) OR (target_value IS NULL) OR (lower_limit <= target_value)) AND ((target_value IS NULL) OR (upper_limit IS NULL) OR (target_value <= upper_limit)) AND ((lower_limit IS NULL) OR (upper_limit IS NULL) OR (lower_limit <= upper_limit)))),
    CONSTRAINT ck_qms_specification_item_sequence CHECK ((sequence_no > 0)),
    CONSTRAINT pk_qms_specification_item PRIMARY KEY (id),
    CONSTRAINT uk_qms_specification_item_sequence UNIQUE (revision_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='LEGACY READ-ONLY DATA: 质量标准条目权威已转移至 MDM';

CREATE TABLE qms_specification_revision (
    id varchar(19) NOT NULL COMMENT '已退役质量标准业务修订的历史技术 ID',
    specification_id varchar(19) NOT NULL COMMENT '所属历史质量标准身份 ID',
    revision_no int NOT NULL,
    status varchar(16) NOT NULL COMMENT '历史标准修订状态：DRAFT、RELEASED 或 OBSOLETE',
    lock_version bigint NOT NULL DEFAULT 0,
    released_at datetime(6),
    obsolete_at datetime(6),
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    specification_id_positive_unique_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'RELEASED') THEN specification_id END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_qms_specification_revision_lock CHECK ((lock_version >= 0)),
    CONSTRAINT ck_qms_specification_revision_no CHECK ((revision_no >= 1)),
    CONSTRAINT ck_qms_specification_revision_status CHECK (((status) IN ('DRAFT', 'RELEASED', 'OBSOLETE'))),
    CONSTRAINT ck_qms_specification_revision_times CHECK (((((status) = 'DRAFT') AND (released_at IS NULL) AND (obsolete_at IS NULL)) OR (((status) = 'RELEASED') AND (released_at IS NOT NULL) AND (obsolete_at IS NULL)) OR (((status) = 'OBSOLETE') AND (released_at IS NOT NULL) AND (obsolete_at IS NOT NULL)))),
    CONSTRAINT pk_qms_specification_revision PRIMARY KEY (id),
    CONSTRAINT uk_qms_specification_revision_no UNIQUE (specification_id, revision_no),
    CONSTRAINT uk_qms_specification_one_released UNIQUE (specification_id_positive_unique_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='LEGACY READ-ONLY DATA: 质量标准修订权威已转移至 MDM';
