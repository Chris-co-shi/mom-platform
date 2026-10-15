-- Formula 是 MDM 制造定义；MES 工单将来只引用已发布修订并固定快照。
-- 主表按 code 精确查找并稳定排序；唯一约束即可服务该路径，不额外建立重复索引。
CREATE TABLE mdm_formula (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL,
    output_material_id varchar(19) NOT NULL,
    base_batch_quantity numeric(24,9) NOT NULL,
    base_uom_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_formula PRIMARY KEY (id),
    CONSTRAINT uk_mdm_formula_code UNIQUE (code),
    CONSTRAINT ck_mdm_formula_batch_positive CHECK (base_batch_quantity > 0)
);

-- 按 plant 过滤公式列表，再按 code/id 稳定分页；预期每工厂数百到数千配方。
CREATE INDEX ix_mdm_formula_plant_code ON mdm_formula (plant_id, code, id);

CREATE TABLE mdm_formula_revision (
    id varchar(19) NOT NULL,
    formula_id varchar(19) NOT NULL,
    revision_no integer NOT NULL,
    status varchar(16) NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    released_at timestamptz,
    obsolete_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_formula_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_formula_revision_no UNIQUE (formula_id, revision_no),
    CONSTRAINT ck_mdm_formula_revision_positive CHECK (revision_no >= 1),
    CONSTRAINT ck_mdm_formula_revision_lock CHECK (lock_version >= 0),
    CONSTRAINT ck_mdm_formula_revision_status CHECK (status IN ('DRAFT','RELEASED','OBSOLETE')),
    CONSTRAINT ck_mdm_formula_revision_times CHECK (
        (status = 'DRAFT' AND released_at IS NULL AND obsolete_at IS NULL) OR
        (status = 'RELEASED' AND released_at IS NOT NULL AND obsolete_at IS NULL) OR
        (status = 'OBSOLETE' AND released_at IS NOT NULL AND obsolete_at IS NOT NULL)
    )
);

CREATE TABLE mdm_formula_item (
    id varchar(19) NOT NULL,
    revision_id varchar(19) NOT NULL,
    sequence_no integer NOT NULL,
    material_id varchar(19) NOT NULL,
    quantity numeric(24,9) NOT NULL,
    uom_id varchar(19) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_formula_item PRIMARY KEY (id),
    CONSTRAINT uk_mdm_formula_item_sequence UNIQUE (revision_id, sequence_no),
    CONSTRAINT ck_mdm_formula_item_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_mdm_formula_item_quantity CHECK (quantity > 0)
);

-- 只查询单修订明细并按 sequence_no 排序，唯一约束已提供左前缀访问路径。
COMMENT ON TABLE mdm_formula IS 'MDM 配方身份和不随修订变化的工厂/产出批量基准';
COMMENT ON TABLE mdm_formula_revision IS '配方业务修订，DRAFT→RELEASED→OBSOLETE，lock_version 是技术锁版本';
COMMENT ON TABLE mdm_formula_item IS '配方修订组成明细；已发布修订的明细不得修改';
COMMENT ON COLUMN mdm_formula.base_batch_quantity IS '精确批量基准；跨量纲 kg/L 换算不由配方主档隐式推断';
