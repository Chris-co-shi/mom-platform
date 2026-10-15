-- ProductionVersion 固定一个已发布配方修订和路线修订，供未来工单按稳定 ID 取快照。
CREATE TABLE mdm_production_version (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    business_version varchar(64) NOT NULL,
    plant_id varchar(19) NOT NULL,
    output_material_id varchar(19) NOT NULL,
    formula_revision_id varchar(19) NOT NULL,
    routing_revision_id varchar(19) NOT NULL,
    valid_from date NOT NULL,
    valid_to date,
    status varchar(16) NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    released_at timestamptz,
    obsolete_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_production_version PRIMARY KEY (id),
    CONSTRAINT uk_mdm_production_version_code UNIQUE (code),
    CONSTRAINT uk_mdm_production_version_business UNIQUE (plant_id, output_material_id, business_version),
    CONSTRAINT ck_mdm_production_version_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_mdm_production_version_lock CHECK (lock_version >= 0),
    CONSTRAINT ck_mdm_production_version_status CHECK (status IN ('DRAFT','RELEASED','OBSOLETE')),
    CONSTRAINT ck_mdm_production_version_times CHECK (
        (status = 'DRAFT' AND released_at IS NULL AND obsolete_at IS NULL) OR
        (status = 'RELEASED' AND released_at IS NOT NULL AND obsolete_at IS NULL) OR
        (status = 'OBSOLETE' AND released_at IS NOT NULL AND obsolete_at IS NOT NULL)
    )
);

-- 工厂/产出物料下按有效期检查重叠并按 code/id 稳定分页；预期每组合为个位至数十版本。
CREATE INDEX ix_mdm_production_version_scope_dates
    ON mdm_production_version (plant_id, output_material_id, status, valid_from, valid_to);
CREATE INDEX ix_mdm_production_version_scope_code
    ON mdm_production_version (plant_id, output_material_id, code, id);

COMMENT ON TABLE mdm_production_version IS '已发布制造定义组合；未来 MES 工单按 ID 读取并固定快照';
COMMENT ON COLUMN mdm_production_version.business_version IS '业务版本标识，与 lock_version 技术并发版本分离';
COMMENT ON COLUMN mdm_production_version.valid_to IS '工厂业务日期的半开区间上界；NULL 表示无穷远';
