CREATE TABLE mdm_inspection_characteristic (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    default_uom_id varchar(19) NOT NULL,
    default_uom_version bigint NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_inspection_characteristic PRIMARY KEY (id),
    CONSTRAINT uk_mdm_inspection_characteristic_code UNIQUE (code),
    CONSTRAINT ck_mdm_inspection_characteristic_version CHECK (version >= 0),
    CONSTRAINT ck_mdm_inspection_characteristic_uom_version CHECK (default_uom_version >= 0)
);

CREATE TABLE mdm_inspection_method (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_inspection_method PRIMARY KEY (id),
    CONSTRAINT uk_mdm_inspection_method_code UNIQUE (code),
    CONSTRAINT ck_mdm_inspection_method_version CHECK (version >= 0)
);

CREATE TABLE mdm_quality_specification (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL,
    material_id varchar(19) NOT NULL,
    inspection_usage varchar(16) NOT NULL,
    plant_version bigint NOT NULL,
    material_revision_no integer NOT NULL,
    material_version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_quality_specification PRIMARY KEY (id),
    CONSTRAINT uk_mdm_quality_specification_code UNIQUE (code),
    CONSTRAINT uk_mdm_quality_specification_scope UNIQUE (plant_id, material_id, inspection_usage),
    CONSTRAINT ck_mdm_quality_specification_usage CHECK (inspection_usage = 'INCOMING')
);

-- 中小规模管理列表按物料、工厂过滤，ID 作稳定排序尾键。
CREATE INDEX ix_mdm_quality_specification_material
    ON mdm_quality_specification (material_id, plant_id, id);

CREATE TABLE mdm_specification_revision (
    id varchar(19) NOT NULL,
    specification_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    status varchar(16) NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    released_at timestamptz,
    obsolete_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_specification_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_specification_revision_no UNIQUE (specification_id, revision_no),
    CONSTRAINT ck_mdm_specification_revision_no CHECK (revision_no >= 1),
    CONSTRAINT ck_mdm_specification_revision_lock CHECK (lock_version >= 0),
    CONSTRAINT ck_mdm_specification_revision_status CHECK (status IN ('DRAFT','RELEASED','OBSOLETE')),
    CONSTRAINT ck_mdm_specification_revision_times CHECK (
        (status = 'DRAFT' AND released_at IS NULL AND obsolete_at IS NULL) OR
        (status = 'RELEASED' AND released_at IS NOT NULL AND obsolete_at IS NULL) OR
        (status = 'OBSOLETE' AND released_at IS NOT NULL AND obsolete_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_mdm_specification_one_released
    ON mdm_specification_revision (specification_id) WHERE status = 'RELEASED';

CREATE TABLE mdm_specification_item (
    id varchar(19) NOT NULL,
    revision_id varchar(19) NOT NULL,
    sequence_no integer NOT NULL,
    characteristic_id varchar(19) NOT NULL,
    characteristic_version bigint NOT NULL,
    method_id varchar(19) NOT NULL,
    method_version bigint NOT NULL,
    uom_id varchar(19) NOT NULL,
    uom_version bigint NOT NULL,
    lower_limit numeric(24,9),
    target_value numeric(24,9),
    upper_limit numeric(24,9),
    mandatory boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_specification_item PRIMARY KEY (id),
    CONSTRAINT uk_mdm_specification_item_sequence UNIQUE (revision_id, sequence_no),
    CONSTRAINT ck_mdm_specification_item_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_mdm_specification_item_range CHECK (
        (lower_limit IS NULL OR target_value IS NULL OR lower_limit <= target_value) AND
        (target_value IS NULL OR upper_limit IS NULL OR target_value <= upper_limit) AND
        (lower_limit IS NULL OR upper_limit IS NULL OR lower_limit <= upper_limit)
    )
);

COMMENT ON TABLE mdm_inspection_characteristic IS '数值型检验特性主数据，不保存检验结果';
COMMENT ON COLUMN mdm_inspection_characteristic.id IS '检验特性稳定字符串 ID';
COMMENT ON COLUMN mdm_inspection_characteristic.default_uom_id IS '默认计量单位 ID，由 MDM 本地事务校验';
COMMENT ON COLUMN mdm_inspection_characteristic.version IS '特性技术乐观锁版本';
COMMENT ON TABLE mdm_inspection_method IS '检验方法静态定义，不包含仪器或实验室执行';
COMMENT ON COLUMN mdm_inspection_method.id IS '检验方法稳定字符串 ID';
COMMENT ON COLUMN mdm_inspection_method.version IS '方法技术乐观锁版本';
COMMENT ON TABLE mdm_quality_specification IS '工厂、物料和用途唯一确定的质量标准主数据';
COMMENT ON COLUMN mdm_quality_specification.id IS '质量标准稳定字符串 ID';
COMMENT ON COLUMN mdm_quality_specification.plant_id IS '适用工厂 ID，无物理外键';
COMMENT ON COLUMN mdm_quality_specification.material_id IS '适用物料 ID，无物理外键';
COMMENT ON TABLE mdm_specification_revision IS '质量标准业务修订；lock_version 是技术乐观锁';
COMMENT ON COLUMN mdm_specification_revision.id IS '质量标准修订稳定字符串 ID';
COMMENT ON COLUMN mdm_specification_revision.specification_id IS '所属质量标准 ID，由 Application 保证关系完整性';
COMMENT ON COLUMN mdm_specification_revision.status IS '业务修订状态：DRAFT、RELEASED 或 OBSOLETE';
COMMENT ON TABLE mdm_specification_item IS '已版本化的数值范围和特性、方法、单位快照，不保存检验结果';
COMMENT ON COLUMN mdm_specification_item.id IS '质量标准条目稳定字符串 ID';
COMMENT ON COLUMN mdm_specification_item.revision_id IS '所属业务修订 ID，无物理外键';
COMMENT ON COLUMN mdm_specification_item.characteristic_id IS '检验特性 ID 快照引用';
COMMENT ON COLUMN mdm_specification_item.method_id IS '检验方法 ID 快照引用';
COMMENT ON COLUMN mdm_specification_item.uom_id IS '检验数值单位 ID 快照引用';
