CREATE TABLE mdm_material_category (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    parent_id varchar(19),
    sort integer NOT NULL,
    default_batch_managed boolean,
    default_shelf_life_days integer,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_material_category PRIMARY KEY (id),
    CONSTRAINT uk_mdm_material_category_code UNIQUE (code),
    CONSTRAINT ck_mdm_material_category_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_material_category_shelf_life_non_negative
        CHECK (default_shelf_life_days IS NULL OR default_shelf_life_days >= 0),
    CONSTRAINT ck_mdm_material_category_version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_mdm_material_category_parent_sort
    ON mdm_material_category (parent_id, sort, code, id);

COMMENT ON TABLE mdm_material_category IS 'GEO 企业动态维护的物料分类树及 Material 创建默认建议值';
COMMENT ON COLUMN mdm_material_category.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN mdm_material_category.code IS '平台全局唯一且创建后不通过普通 API 修改的物料分类业务编码';
COMMENT ON COLUMN mdm_material_category.name_zh IS '物料分类中文名称，必填且允许普通更新';
COMMENT ON COLUMN mdm_material_category.name_en IS '物料分类可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_material_category.parent_id IS '可选父分类 mdm_material_category.id 引用；根分类为空且不建立物理外键';
COMMENT ON COLUMN mdm_material_category.sort IS '同级分类的显示排序值；最终稳定顺序继续使用 code 和 id';
COMMENT ON COLUMN mdm_material_category.default_batch_managed IS '创建 Material 时可复制的批次管理默认建议；可空且不形成永久继承';
COMMENT ON COLUMN mdm_material_category.default_shelf_life_days IS '创建 Material 时可复制的保质期天数默认建议；可空且必须非负';
COMMENT ON COLUMN mdm_material_category.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_material_category.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_material_category.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_material_category.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_material_category.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_material_category.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_material_category.deleted IS '逻辑删除标识；V1 不提供删除 API且业务编码不可复用';
