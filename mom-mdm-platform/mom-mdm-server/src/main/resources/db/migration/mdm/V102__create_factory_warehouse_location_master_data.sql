CREATE TABLE mdm_plant (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_plant PRIMARY KEY (id),
    CONSTRAINT uk_mdm_plant_code UNIQUE (code),
    CONSTRAINT ck_mdm_plant_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_plant_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_workshop (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    plant_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_workshop PRIMARY KEY (id),
    CONSTRAINT uk_mdm_workshop_plant_code UNIQUE (plant_id, code),
    CONSTRAINT ck_mdm_workshop_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_workshop_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_production_line (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    workshop_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_production_line PRIMARY KEY (id),
    CONSTRAINT uk_mdm_production_line_workshop_code UNIQUE (workshop_id, code),
    CONSTRAINT ck_mdm_production_line_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_production_line_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_workstation (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    production_line_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_workstation PRIMARY KEY (id),
    CONSTRAINT uk_mdm_workstation_line_code UNIQUE (production_line_id, code),
    CONSTRAINT ck_mdm_workstation_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_workstation_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_warehouse (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    plant_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_warehouse PRIMARY KEY (id),
    CONSTRAINT uk_mdm_warehouse_plant_code UNIQUE (plant_id, code),
    CONSTRAINT ck_mdm_warehouse_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_warehouse_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_warehouse_area (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    warehouse_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_warehouse_area PRIMARY KEY (id),
    CONSTRAINT uk_mdm_warehouse_area_warehouse_code UNIQUE (warehouse_id, code),
    CONSTRAINT ck_mdm_warehouse_area_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_warehouse_area_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_location_type (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_location_type PRIMARY KEY (id),
    CONSTRAINT uk_mdm_location_type_code UNIQUE (code),
    CONSTRAINT ck_mdm_location_type_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_location_type_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_location (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    plant_id varchar(19) NOT NULL,
    warehouse_area_id varchar(19),
    location_type_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_location PRIMARY KEY (id),
    CONSTRAINT uk_mdm_location_plant_code UNIQUE (plant_id, code),
    CONSTRAINT ck_mdm_location_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_location_version_non_negative CHECK (version >= 0)
);

COMMENT ON TABLE mdm_plant IS 'GEO 制造与仓储业务的顶层执行范围主数据';
COMMENT ON TABLE mdm_workshop IS 'Plant 下的车间主数据；父级完整性由 MDM Application 保证';
COMMENT ON TABLE mdm_production_line IS 'Workshop 下的生产线主数据；父级完整性由 MDM Application 保证';
COMMENT ON TABLE mdm_workstation IS 'ProductionLine 下的工位主数据；父级完整性由 MDM Application 保证';
COMMENT ON TABLE mdm_warehouse IS 'Plant 下的仓库主数据；不承载库存策略和容量事实';
COMMENT ON TABLE mdm_warehouse_area IS 'Warehouse 下的仓库区域主数据；不承载库存事实';
COMMENT ON TABLE mdm_location_type IS '动态维护的位置分类主数据；不承载按 Code 分支的业务能力';
COMMENT ON TABLE mdm_location IS 'GEO 厂内统一可寻址物理位置；仓库区域引用可空';

COMMENT ON COLUMN mdm_plant.code IS '平台全局唯一且创建后不通过普通 API 修改的 Plant 业务编码';
COMMENT ON COLUMN mdm_workshop.plant_id IS '所属 mdm_plant.id 引用；不建立物理外键';
COMMENT ON COLUMN mdm_production_line.workshop_id IS '所属 mdm_workshop.id 引用；不建立物理外键';
COMMENT ON COLUMN mdm_workstation.production_line_id IS '所属 mdm_production_line.id 引用；不建立物理外键';
COMMENT ON COLUMN mdm_warehouse.plant_id IS '所属 mdm_plant.id 引用；不建立物理外键';
COMMENT ON COLUMN mdm_warehouse_area.warehouse_id IS '所属 mdm_warehouse.id 引用；不建立物理外键';
COMMENT ON COLUMN mdm_location.location_type_id IS '所属 mdm_location_type.id 引用；不建立物理外键';
COMMENT ON COLUMN mdm_location.plant_id IS '位置所属 Plant；必填且参与位置编码唯一约束';
COMMENT ON COLUMN mdm_location.warehouse_area_id IS '可选仓库区域引用；非仓储位置为空';

COMMENT ON COLUMN mdm_plant.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_workshop.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_production_line.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_workstation.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_warehouse.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_warehouse_area.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_location_type.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_location.status IS '简单生命周期状态：ENABLED 或 DISABLED';

COMMENT ON COLUMN mdm_plant.id IS 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)';
COMMENT ON COLUMN mdm_plant.name_zh IS 'Plant 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_plant.name_en IS 'Plant 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_plant.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_plant.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_plant.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_plant.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_plant.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_plant.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_workshop.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_workshop.code IS '所属 Plant 内唯一且创建后不通过普通 API 修改的业务编码';
COMMENT ON COLUMN mdm_workshop.name_zh IS 'Workshop 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_workshop.name_en IS 'Workshop 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_workshop.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_workshop.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_workshop.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_workshop.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_workshop.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_workshop.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_production_line.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_production_line.code IS '所属 Workshop 内唯一且创建后不通过普通 API 修改的业务编码';
COMMENT ON COLUMN mdm_production_line.name_zh IS 'ProductionLine 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_production_line.name_en IS 'ProductionLine 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_production_line.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_production_line.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_production_line.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_production_line.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_production_line.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_production_line.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_workstation.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_workstation.code IS '所属 ProductionLine 内唯一且创建后不通过普通 API 修改的业务编码';
COMMENT ON COLUMN mdm_workstation.name_zh IS 'Workstation 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_workstation.name_en IS 'Workstation 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_workstation.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_workstation.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_workstation.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_workstation.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_workstation.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_workstation.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_warehouse.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_warehouse.code IS '所属 Plant 内唯一且创建后不通过普通 API 修改的业务编码';
COMMENT ON COLUMN mdm_warehouse.name_zh IS 'Warehouse 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_warehouse.name_en IS 'Warehouse 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_warehouse.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_warehouse.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_warehouse.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_warehouse.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_warehouse.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_warehouse.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_warehouse_area.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_warehouse_area.code IS '所属 Warehouse 内唯一且创建后不通过普通 API 修改的业务编码';
COMMENT ON COLUMN mdm_warehouse_area.name_zh IS 'WarehouseArea 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_warehouse_area.name_en IS 'WarehouseArea 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_warehouse_area.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_warehouse_area.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_warehouse_area.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_warehouse_area.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_warehouse_area.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_warehouse_area.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_location_type.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_location_type.code IS '平台唯一且创建后不通过普通 API 修改的动态分类编码';
COMMENT ON COLUMN mdm_location_type.name_zh IS 'LocationType 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_location_type.name_en IS 'LocationType 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_location_type.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_location_type.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_location_type.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_location_type.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_location_type.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_location_type.deleted IS '逻辑删除标识；V1 不提供删除 API';

COMMENT ON COLUMN mdm_location.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_location.code IS '所属 Plant 内唯一且创建后不通过普通 API 修改的可寻址位置编码';
COMMENT ON COLUMN mdm_location.name_zh IS 'Location 中文名称，允许普通更新';
COMMENT ON COLUMN mdm_location.name_en IS 'Location 可选英文名称，允许普通更新';
COMMENT ON COLUMN mdm_location.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_location.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_location.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_location.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_location.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_location.deleted IS '逻辑删除标识；V1 不提供删除 API';
