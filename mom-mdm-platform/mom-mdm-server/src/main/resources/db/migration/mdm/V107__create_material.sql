CREATE TABLE mdm_material (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    category_id varchar(19) NOT NULL,
    base_uom_id varchar(19) NOT NULL,
    shelf_life_days integer,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_material PRIMARY KEY (id),
    CONSTRAINT uk_mdm_material_code UNIQUE (code),
    CONSTRAINT ck_mdm_material_shelf_life_non_negative
        CHECK (shelf_life_days IS NULL OR shelf_life_days >= 0),
    CONSTRAINT ck_mdm_material_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_material_version_non_negative CHECK (version >= 0)
);

-- 物料预计为 10^4～10^5 级；以下索引对应后台按分类、基础单位和状态的分页及引用保护查询。
CREATE INDEX ix_mdm_material_category_status_code
    ON mdm_material (category_id, status, code, id) WHERE deleted = false;
CREATE INDEX ix_mdm_material_base_uom_status_code
    ON mdm_material (base_uom_id, status, code, id) WHERE deleted = false;
CREATE INDEX ix_mdm_material_status_code
    ON mdm_material (status, code, id) WHERE deleted = false;

COMMENT ON TABLE mdm_material IS 'GEO 权威物料主数据；不包含包装、批次、库存或动态规格事实';
COMMENT ON COLUMN mdm_material.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN mdm_material.code IS '平台全局唯一且创建后不通过普通 API 修改的物料业务编码';
COMMENT ON COLUMN mdm_material.name IS '物料面向业务用户的名称；多语言显示由独立翻译能力扩展';
COMMENT ON COLUMN mdm_material.category_id IS '唯一叶子物料分类 ID；由 Application 保证引用完整性且不建立物理外键';
COMMENT ON COLUMN mdm_material.base_uom_id IS '物料库存与数量事实使用的基础计量单位 ID；由 Application 保证完整启用链';
COMMENT ON COLUMN mdm_material.shelf_life_days IS '可选非负保质期天数；实际失效日期属于未来 Lot 事实';
COMMENT ON COLUMN mdm_material.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_material.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_material.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_material.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_material.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_material.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_material.deleted IS '逻辑删除标识；V1 不提供删除 API且业务编码不可复用';
