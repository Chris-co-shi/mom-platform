CREATE TABLE mdm_dimension (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    time_exponent smallint NOT NULL,
    length_exponent smallint NOT NULL,
    mass_exponent smallint NOT NULL,
    electric_current_exponent smallint NOT NULL,
    temperature_exponent smallint NOT NULL,
    amount_exponent smallint NOT NULL,
    luminous_intensity_exponent smallint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_dimension PRIMARY KEY (id),
    CONSTRAINT uk_mdm_dimension_code UNIQUE (code),
    CONSTRAINT uk_mdm_dimension_vector UNIQUE (
        time_exponent, length_exponent, mass_exponent, electric_current_exponent,
        temperature_exponent, amount_exponent, luminous_intensity_exponent
    ),
    CONSTRAINT ck_mdm_dimension_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_dimension_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_uom_category (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    dimension_id varchar(19) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_uom_category PRIMARY KEY (id),
    CONSTRAINT uk_mdm_uom_category_code UNIQUE (code),
    CONSTRAINT ck_mdm_uom_category_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_uom_category_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_uom (
    id varchar(19) NOT NULL,
    code varchar(64) NOT NULL,
    name_zh varchar(200) NOT NULL,
    name_en varchar(200),
    symbol varchar(32) NOT NULL,
    category_id varchar(19) NOT NULL,
    reference_unit boolean NOT NULL DEFAULT false,
    ucum_not_applicable_reason varchar(500),
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_mdm_uom PRIMARY KEY (id),
    CONSTRAINT uk_mdm_uom_code UNIQUE (code),
    CONSTRAINT ck_mdm_uom_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_uom_custom_code_reason CHECK ((code LIKE 'mom:%' AND ucum_not_applicable_reason IS NOT NULL AND btrim(ucum_not_applicable_reason) <> '') OR (code NOT LIKE 'mom:%' AND ucum_not_applicable_reason IS NULL)),
    CONSTRAINT ck_mdm_uom_version_non_negative CHECK (version >= 0)
);

CREATE TABLE mdm_uom_conversion_rule (
    id varchar(19) NOT NULL,
    uom_id varchar(19) NOT NULL,
    version_no integer NOT NULL,
    algorithm_type varchar(16) NOT NULL,
    multiplier numeric(50, 30) NOT NULL,
    offset_value numeric(50, 30) NOT NULL,
    calculation_precision integer NOT NULL,
    rounding_mode varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_at timestamptz NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_mdm_uom_conversion_rule PRIMARY KEY (id),
    CONSTRAINT uk_mdm_uom_conversion_rule_version UNIQUE (uom_id, version_no),
    CONSTRAINT ck_mdm_uom_conversion_rule_version_positive CHECK (version_no > 0),
    CONSTRAINT ck_mdm_uom_conversion_rule_algorithm CHECK (algorithm_type = 'AFFINE'),
    CONSTRAINT ck_mdm_uom_conversion_rule_multiplier_positive CHECK (multiplier > 0),
    CONSTRAINT ck_mdm_uom_conversion_rule_precision CHECK (calculation_precision BETWEEN 1 AND 34),
    CONSTRAINT ck_mdm_uom_conversion_rule_rounding CHECK (rounding_mode IN (
        'UP', 'DOWN', 'CEILING', 'FLOOR', 'HALF_UP', 'HALF_DOWN', 'HALF_EVEN', 'UNNECESSARY'
    )),
    CONSTRAINT ck_mdm_uom_conversion_rule_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_mdm_uom_conversion_rule_lock_version_non_negative CHECK (lock_version >= 0)
);

CREATE INDEX ix_mdm_uom_category_dimension_status_code
    ON mdm_uom_category (dimension_id, status, code, id);
CREATE INDEX ix_mdm_uom_category_status_code
    ON mdm_uom_category (status, code, id);
CREATE INDEX ix_mdm_uom_by_category_status_code
    ON mdm_uom (category_id, status, code, id);
CREATE UNIQUE INDEX uk_mdm_uom_category_reference
    ON mdm_uom (category_id) WHERE reference_unit = true AND deleted = false;
CREATE UNIQUE INDEX uk_mdm_uom_conversion_rule_current
    ON mdm_uom_conversion_rule (uom_id) WHERE status = 'ENABLED';

COMMENT ON TABLE mdm_dimension IS '计量单位的物理量纲定义，使用固定七维 SI 指数向量';
COMMENT ON COLUMN mdm_dimension.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_dimension.code IS '全局唯一且创建后不可普通修改的量纲业务编码';
COMMENT ON COLUMN mdm_dimension.name_zh IS '量纲中文名称';
COMMENT ON COLUMN mdm_dimension.name_en IS '量纲可选英文名称';
COMMENT ON COLUMN mdm_dimension.time_exponent IS 'SI 时间基础量纲 T 的整数指数';
COMMENT ON COLUMN mdm_dimension.length_exponent IS 'SI 长度基础量纲 L 的整数指数';
COMMENT ON COLUMN mdm_dimension.mass_exponent IS 'SI 质量基础量纲 M 的整数指数';
COMMENT ON COLUMN mdm_dimension.electric_current_exponent IS 'SI 电流基础量纲 I 的整数指数';
COMMENT ON COLUMN mdm_dimension.temperature_exponent IS 'SI 热力学温度基础量纲 Θ 的整数指数';
COMMENT ON COLUMN mdm_dimension.amount_exponent IS 'SI 物质的量基础量纲 N 的整数指数';
COMMENT ON COLUMN mdm_dimension.luminous_intensity_exponent IS 'SI 发光强度基础量纲 J 的整数指数';
COMMENT ON COLUMN mdm_dimension.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_dimension.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_dimension.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_dimension.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_dimension.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_dimension.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_dimension.deleted IS '逻辑删除标识；V1 不提供删除 API且编码不可复用';

COMMENT ON TABLE mdm_uom_category IS '面向业务用户的计量单位类别及换算兼容边界';
COMMENT ON COLUMN mdm_uom_category.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_uom_category.code IS '全局唯一且创建后不可普通修改的计量单位类别编码';
COMMENT ON COLUMN mdm_uom_category.name_zh IS '计量单位类别中文名称';
COMMENT ON COLUMN mdm_uom_category.name_en IS '计量单位类别可选英文名称';
COMMENT ON COLUMN mdm_uom_category.dimension_id IS '所属物理量纲 ID；由 Application 保证引用完整性且不建立物理外键';
COMMENT ON COLUMN mdm_uom_category.status IS '简单生命周期状态：ENABLED 或 DISABLED';
COMMENT ON COLUMN mdm_uom_category.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_uom_category.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_uom_category.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_uom_category.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_uom_category.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_uom_category.deleted IS '逻辑删除标识；V1 不提供删除 API且编码不可复用';

COMMENT ON TABLE mdm_uom IS 'GEO 权威计量单位目录；每个单位只能属于一个计量单位类别';
COMMENT ON COLUMN mdm_uom.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_uom.code IS '全局唯一单位编码；优先直接使用 UCUM，企业扩展使用 mom: 命名空间';
COMMENT ON COLUMN mdm_uom.name_zh IS '计量单位中文名称';
COMMENT ON COLUMN mdm_uom.name_en IS '计量单位可选英文名称';
COMMENT ON COLUMN mdm_uom.symbol IS '面向用户的显示符号；允许不同单位使用相同符号';
COMMENT ON COLUMN mdm_uom.category_id IS '所属计量单位类别 ID；由 Application 保证引用完整性且不建立物理外键';
COMMENT ON COLUMN mdm_uom.reference_unit IS '是否为类别唯一基准单位；基准身份创建后不可变';
COMMENT ON COLUMN mdm_uom.ucum_not_applicable_reason IS 'mom: 企业扩展单位无法采用 UCUM 时的必填治理说明';
COMMENT ON COLUMN mdm_uom.status IS '简单生命周期状态：ENABLED 或 DISABLED；基准单位不独立启停';
COMMENT ON COLUMN mdm_uom.created_at IS '记录首次持久化 UTC 时间';
COMMENT ON COLUMN mdm_uom.created_by IS '创建 Actor ID';
COMMENT ON COLUMN mdm_uom.updated_at IS '最近一次持久化修改 UTC 时间';
COMMENT ON COLUMN mdm_uom.updated_by IS '最近修改 Actor ID';
COMMENT ON COLUMN mdm_uom.version IS 'MyBatis-Plus 乐观锁版本号';
COMMENT ON COLUMN mdm_uom.deleted IS '逻辑删除标识；V1 不提供删除 API且编码不可复用';

COMMENT ON TABLE mdm_uom_conversion_rule IS '非基准单位到类别基准单位的不可变仿射换算规则版本';
COMMENT ON COLUMN mdm_uom_conversion_rule.id IS 'MOM String 技术主键';
COMMENT ON COLUMN mdm_uom_conversion_rule.uom_id IS '非基准计量单位 ID；由 Application 保证引用完整性且不建立物理外键';
COMMENT ON COLUMN mdm_uom_conversion_rule.version_no IS '同一 UOM 内从 1 递增且不可复用的业务规则版本号';
COMMENT ON COLUMN mdm_uom_conversion_rule.algorithm_type IS '受控算法类型；V1 只允许 AFFINE';
COMMENT ON COLUMN mdm_uom_conversion_rule.multiplier IS '换算到基准单位时使用的正乘数';
COMMENT ON COLUMN mdm_uom_conversion_rule.offset_value IS '乘法后加到基准单位值上的偏移量';
COMMENT ON COLUMN mdm_uom_conversion_rule.calculation_precision IS 'BigDecimal MathContext 使用的有效数字精度，范围 1..34';
COMMENT ON COLUMN mdm_uom_conversion_rule.rounding_mode IS '计算无法精确表示时使用的 Java BigDecimal 舍入模式';
COMMENT ON COLUMN mdm_uom_conversion_rule.status IS '规则版本状态；同一 UOM 最多一个 ENABLED 版本';
COMMENT ON COLUMN mdm_uom_conversion_rule.created_at IS '规则版本首次发布 UTC 时间';
COMMENT ON COLUMN mdm_uom_conversion_rule.created_by IS '规则版本创建 Actor ID';
COMMENT ON COLUMN mdm_uom_conversion_rule.updated_at IS '规则状态最近修改 UTC 时间';
COMMENT ON COLUMN mdm_uom_conversion_rule.updated_by IS '规则状态最近修改 Actor ID';
COMMENT ON COLUMN mdm_uom_conversion_rule.lock_version IS '规则状态切换使用的 MyBatis-Plus 乐观锁版本号';
