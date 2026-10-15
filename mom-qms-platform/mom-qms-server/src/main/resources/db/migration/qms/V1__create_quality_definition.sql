CREATE TABLE qms_inspection_characteristic (
    id varchar(19) NOT NULL, code varchar(64) NOT NULL, name varchar(200) NOT NULL,
    default_uom_id varchar(19) NOT NULL, default_uom_version bigint NOT NULL,
    enabled boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL, created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL, updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_qms_inspection_characteristic PRIMARY KEY (id),
    CONSTRAINT uk_qms_inspection_characteristic_code UNIQUE (code),
    CONSTRAINT ck_qms_inspection_characteristic_version CHECK (version >= 0),
    CONSTRAINT ck_qms_inspection_characteristic_uom_version CHECK (default_uom_version >= 0)
);
CREATE TABLE qms_inspection_method (
    id varchar(19) NOT NULL, code varchar(64) NOT NULL, name varchar(200) NOT NULL,
    description varchar(1000), enabled boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL, created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL, updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_qms_inspection_method PRIMARY KEY (id),
    CONSTRAINT uk_qms_inspection_method_code UNIQUE (code),
    CONSTRAINT ck_qms_inspection_method_version CHECK (version >= 0)
);
CREATE TABLE qms_quality_specification (
    id varchar(19) NOT NULL, code varchar(64) NOT NULL, name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL, material_id varchar(19) NOT NULL, inspection_usage varchar(16) NOT NULL,
    plant_version bigint NOT NULL, material_revision_no integer NOT NULL, material_version bigint NOT NULL,
    created_at timestamptz NOT NULL, created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL, updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_qms_quality_specification PRIMARY KEY (id),
    CONSTRAINT uk_qms_quality_specification_code UNIQUE (code),
    CONSTRAINT uk_qms_quality_specification_scope UNIQUE (plant_id, material_id, inspection_usage),
    CONSTRAINT ck_qms_quality_specification_usage CHECK (inspection_usage = 'INCOMING')
);
CREATE INDEX ix_qms_quality_specification_material ON qms_quality_specification (material_id, plant_id, id);
CREATE TABLE qms_specification_revision (
    id varchar(19) NOT NULL, specification_id varchar(19) NOT NULL, revision_no integer NOT NULL,
    status varchar(16) NOT NULL, lock_version bigint NOT NULL DEFAULT 0,
    released_at timestamptz, obsolete_at timestamptz,
    created_at timestamptz NOT NULL, created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL, updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_qms_specification_revision PRIMARY KEY (id),
    CONSTRAINT uk_qms_specification_revision UNIQUE (specification_id, revision_no),
    CONSTRAINT ck_qms_specification_revision_no CHECK (revision_no >= 1),
    CONSTRAINT ck_qms_specification_revision_lock CHECK (lock_version >= 0),
    CONSTRAINT ck_qms_specification_revision_status CHECK (status IN ('DRAFT','RELEASED','OBSOLETE')),
    CONSTRAINT ck_qms_specification_revision_times CHECK (
      (status = 'DRAFT' AND released_at IS NULL AND obsolete_at IS NULL) OR
      (status = 'RELEASED' AND released_at IS NOT NULL AND obsolete_at IS NULL) OR
      (status = 'OBSOLETE' AND released_at IS NOT NULL AND obsolete_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_qms_specification_one_released ON qms_specification_revision (specification_id) WHERE status = 'RELEASED';
CREATE TABLE qms_specification_item (
    id varchar(19) NOT NULL, revision_id varchar(19) NOT NULL, sequence_no integer NOT NULL,
    characteristic_id varchar(19) NOT NULL, characteristic_version bigint NOT NULL,
    method_id varchar(19) NOT NULL, method_version bigint NOT NULL,
    uom_id varchar(19) NOT NULL, uom_version bigint NOT NULL,
    lower_limit numeric(24,9), target_value numeric(24,9), upper_limit numeric(24,9),
    mandatory boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL, created_by varchar(128) NOT NULL,
    CONSTRAINT pk_qms_specification_item PRIMARY KEY (id),
    CONSTRAINT uk_qms_specification_item_sequence UNIQUE (revision_id, sequence_no),
    CONSTRAINT ck_qms_specification_item_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_qms_specification_item_range CHECK (
      (lower_limit IS NULL OR target_value IS NULL OR lower_limit <= target_value) AND
      (target_value IS NULL OR upper_limit IS NULL OR target_value <= upper_limit) AND
      (lower_limit IS NULL OR upper_limit IS NULL OR lower_limit <= upper_limit))
);
COMMENT ON TABLE qms_quality_specification IS 'QMS 质量标准身份；MDM ID 和版本仅为发布校验快照，不是跨 Schema 外键';
COMMENT ON TABLE qms_specification_revision IS '质量标准业务修订；lock_version 是技术乐观锁';
COMMENT ON TABLE qms_specification_item IS 'V1 数值型检验特性范围，不保存检验结果';
