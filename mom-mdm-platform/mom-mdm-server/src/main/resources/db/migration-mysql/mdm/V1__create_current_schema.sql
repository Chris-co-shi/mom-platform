-- mdm：根据 PostgreSQL 17 空库 Flyway 迁移至 V134 后的有效结构建立 MySQL 8.4 基线。

CREATE TABLE mdm_code_rule (
    id varchar(19) NOT NULL COMMENT '当前 Owner 内规则身份的 varchar(19) 技术主键',
    rule_code varchar(64) NOT NULL COMMENT '当前 Owner 内唯一、不可改名的规则编码',
    version bigint NOT NULL DEFAULT 0 COMMENT '同 ruleCode 所有写入共用的规则级并发版本',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    usage_scenario varchar(120) COMMENT '规则级使用场景；新建必填，历史未补充数据允许为空，各修订共用',
    description varchar(1000) COMMENT '规则级纯文本描述；草稿保存时与规则版本、审计在同一本地事务中更新',
    CONSTRAINT ck_mdm_code_rule_usage_scenario CHECK (((usage_scenario IS NULL) OR (char_length(trim((usage_scenario))) > 0))),
    CONSTRAINT ck_mdm_code_rule_version CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_code_rule PRIMARY KEY (id),
    CONSTRAINT uk_mdm_code_rule_code UNIQUE (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='MDM 编码规则身份；version 是整个规则族的写入 CAS 令牌';

CREATE TABLE mdm_code_rule_issue (
    rule_id varchar(19) NOT NULL COMMENT '所属 MDM 规则身份 ID；完整编码在该规则内唯一',
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    code varchar(128) NOT NULL,
    revision_id varchar(19) NOT NULL COMMENT '本次正式发号实际使用的不可变发布修订 ID',
    revision_no int NOT NULL,
    created_at datetime(6) NOT NULL,
    CONSTRAINT ck_mdm_code_rule_issue_revision CHECK ((revision_no > 0)),
    CONSTRAINT pk_mdm_code_rule_issue PRIMARY KEY (request_key),
    CONSTRAINT uk_mdm_code_rule_issue_code UNIQUE (rule_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='MDM 正式发号幂等记录和规则内完整编码唯一约束';

CREATE TABLE mdm_code_rule_revision (
    id varchar(19) NOT NULL COMMENT '规则修订的 varchar(19) 技术主键',
    rule_id varchar(19) NOT NULL COMMENT '所属规则身份 ID，由 Application 保障存在性',
    revision_no int NOT NULL,
    status varchar(16) NOT NULL COMMENT 'DRAFT、ENABLED 或 DISABLED；发布后 definition 不再修改',
    definition json NOT NULL COMMENT '与 CodeRuleDefinition V1 一致的 JSONB，保持 segment 数组顺序及参数',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    draft_rule_id_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'DRAFT') THEN rule_id END) STORED COMMENT '条件唯一约束的生效键',
    enabled_rule_id_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'ENABLED') THEN rule_id END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_mdm_code_rule_revision_definition CHECK (((lower(json_type(definition)) = 'object') AND ((json_unquote(json_extract(definition, '$.schemaVersion'))) = '1') AND (lower(json_type(json_extract(definition, '$.segments'))) = 'array'))),
    CONSTRAINT ck_mdm_code_rule_revision_no CHECK ((revision_no >= 1)),
    CONSTRAINT ck_mdm_code_rule_revision_status CHECK (((status) IN ('DRAFT', 'ENABLED', 'DISABLED'))),
    CONSTRAINT pk_mdm_code_rule_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_code_rule_revision_no UNIQUE (rule_id, revision_no),
    CONSTRAINT uk_mdm_code_rule_one_draft UNIQUE (draft_rule_id_key),
    CONSTRAINT uk_mdm_code_rule_one_enabled UNIQUE (enabled_rule_id_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='编码规则不可变发布修订；仅 DRAFT 的 definition 可以更新';

CREATE TABLE mdm_code_rule_sequence (
    rule_id varchar(19) NOT NULL COMMENT '所属 MDM 规则身份 ID，不随修订变化',
    scope_key varchar(8) NOT NULL DEFAULT '' COMMENT 'UTC 年/月/日周期键；空字符串表示不重置',
    `last_value` bigint NOT NULL,
    CONSTRAINT ck_mdm_code_rule_sequence_positive CHECK (`last_value` > 0),
    CONSTRAINT pk_mdm_code_rule_sequence PRIMARY KEY (rule_id, scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='MDM 编码规则正式流水，作用域为规则身份和 UTC 重置周期';

CREATE TABLE mdm_dimension (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '全局唯一且创建后不可普通修改的量纲业务编码',
    name varchar(200) NOT NULL COMMENT '量纲面向业务用户的名称；多语言显示由独立翻译能力扩展',
    time_exponent smallint NOT NULL COMMENT 'SI 时间基础量纲 T 的整数指数',
    length_exponent smallint NOT NULL COMMENT 'SI 长度基础量纲 L 的整数指数',
    mass_exponent smallint NOT NULL COMMENT 'SI 质量基础量纲 M 的整数指数',
    electric_current_exponent smallint NOT NULL COMMENT 'SI 电流基础量纲 I 的整数指数',
    temperature_exponent smallint NOT NULL COMMENT 'SI 热力学温度基础量纲 Θ 的整数指数',
    amount_exponent smallint NOT NULL COMMENT 'SI 物质的量基础量纲 N 的整数指数',
    luminous_intensity_exponent smallint NOT NULL COMMENT 'SI 发光强度基础量纲 J 的整数指数',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API且编码不可复用',
    CONSTRAINT ck_mdm_dimension_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_dimension_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_dimension PRIMARY KEY (id),
    CONSTRAINT uk_mdm_dimension_code UNIQUE (code),
    CONSTRAINT uk_mdm_dimension_vector UNIQUE (time_exponent, length_exponent, mass_exponent, electric_current_exponent, temperature_exponent, amount_exponent, luminous_intensity_exponent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='计量单位的物理量纲定义，使用固定七维 SI 指数向量';

CREATE TABLE mdm_formula (
    id varchar(19) NOT NULL COMMENT '配方身份的稳定技术 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL COMMENT '配方所属工厂的 MDM ID',
    output_material_id varchar(19) NOT NULL COMMENT '配方产出物料的 MDM ID',
    base_batch_quantity decimal(24,9) NOT NULL COMMENT '精确批量基准；跨量纲 kg/L 换算不由配方主档隐式推断',
    base_uom_id varchar(19) NOT NULL COMMENT '基准批量对应的 MDM 计量单位 ID',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_formula_batch_positive CHECK ((base_batch_quantity > 0)),
    CONSTRAINT pk_mdm_formula PRIMARY KEY (id),
    CONSTRAINT uk_mdm_formula_code UNIQUE (code),
    KEY ix_mdm_formula_plant_code (plant_id, code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='MDM 配方身份和不随修订变化的工厂/产出批量基准';

CREATE TABLE mdm_formula_item (
    id varchar(19) NOT NULL COMMENT '配方组成条目的稳定技术 ID',
    revision_id varchar(19) NOT NULL COMMENT '所属配方业务修订 ID',
    sequence_no int NOT NULL,
    material_id varchar(19) NOT NULL COMMENT '组成物料的 MDM ID',
    quantity decimal(24,9) NOT NULL,
    uom_id varchar(19) NOT NULL COMMENT '组成数量对应的 MDM 计量单位 ID',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_formula_item_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_mdm_formula_item_sequence CHECK ((sequence_no > 0)),
    CONSTRAINT pk_mdm_formula_item PRIMARY KEY (id),
    CONSTRAINT uk_mdm_formula_item_sequence UNIQUE (revision_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='配方修订组成明细；已发布修订的明细不得修改';

CREATE TABLE mdm_formula_revision (
    id varchar(19) NOT NULL COMMENT '配方业务修订的稳定技术 ID',
    formula_id varchar(19) NOT NULL COMMENT '所属配方身份 ID',
    revision_no int NOT NULL,
    status varchar(16) NOT NULL COMMENT '配方修订状态：DRAFT、RELEASED 或 OBSOLETE',
    lock_version bigint NOT NULL DEFAULT 0,
    released_at datetime(6),
    obsolete_at datetime(6),
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_formula_revision_lock CHECK ((lock_version >= 0)),
    CONSTRAINT ck_mdm_formula_revision_positive CHECK ((revision_no >= 1)),
    CONSTRAINT ck_mdm_formula_revision_status CHECK (((status) IN ('DRAFT', 'RELEASED', 'OBSOLETE'))),
    CONSTRAINT ck_mdm_formula_revision_times CHECK (((((status) = 'DRAFT') AND (released_at IS NULL) AND (obsolete_at IS NULL)) OR (((status) = 'RELEASED') AND (released_at IS NOT NULL) AND (obsolete_at IS NULL)) OR (((status) = 'OBSOLETE') AND (released_at IS NOT NULL) AND (obsolete_at IS NOT NULL)))),
    CONSTRAINT pk_mdm_formula_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_formula_revision_no UNIQUE (formula_id, revision_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='配方业务修订，DRAFT→RELEASED→OBSOLETE，lock_version 是技术锁版本';

CREATE TABLE mdm_i18n_message_definition (
    id varchar(19) NOT NULL COMMENT '应用侧 ASSIGN_ID 生成的 varchar(19) 技术主键',
    message_key varchar(160) NOT NULL COMMENT 'Owner 内唯一且创建后不可修改的小写稳定消息键',
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_mdm_i18n_message_definition_key CHECK ((regexp_like(message_key, '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*\\z', 'c'))),
    CONSTRAINT ck_mdm_i18n_message_definition_version CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_mdm_i18n_message_definition_key UNIQUE (message_key),
    KEY ix_mdm_i18n_message_definition_runtime (deleted, enabled, message_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='MDM Owner 内唯一、不可变的稳定 I18n Key 定义';

CREATE TABLE mdm_i18n_translation (
    id varchar(19) NOT NULL COMMENT '应用侧 ASSIGN_ID 生成的 varchar(19) 技术主键',
    message_id varchar(19) NOT NULL COMMENT '同 Schema mdm_i18n_message_definition 技术主键引用，无跨 Schema 外键',
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL,
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_mdm_i18n_translation_locale CHECK ((regexp_like(locale_code, '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?\\z', 'c'))),
    CONSTRAINT ck_mdm_i18n_translation_text CHECK ((((char_length((message_text)) >= 1) AND (char_length((message_text)) <= 4096)) AND (NOT regexp_like(message_text, '[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]', 'c')) AND (NOT regexp_like(message_text, '<[^>]*>', 'c')))),
    CONSTRAINT ck_mdm_i18n_translation_version CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_mdm_i18n_translation_message_locale UNIQUE (message_id, locale_code),
    KEY ix_mdm_i18n_translation_runtime (deleted, locale_code, message_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='MDM 自有动态国际化译文；locale_code 为平台 Locale 的逻辑引用';

CREATE TABLE mdm_inspection_characteristic (
    id varchar(19) NOT NULL COMMENT '检验特性稳定字符串 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    default_uom_id varchar(19) NOT NULL COMMENT '默认计量单位 ID，由 MDM 本地事务校验',
    default_uom_version bigint NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT '特性技术乐观锁版本',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_inspection_characteristic_uom_version CHECK ((default_uom_version >= 0)),
    CONSTRAINT ck_mdm_inspection_characteristic_version CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_inspection_characteristic PRIMARY KEY (id),
    CONSTRAINT uk_mdm_inspection_characteristic_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='数值型检验特性主数据，不保存检验结果';

CREATE TABLE mdm_inspection_method (
    id varchar(19) NOT NULL COMMENT '检验方法稳定字符串 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT '方法技术乐观锁版本',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_inspection_method_version CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_inspection_method PRIMARY KEY (id),
    CONSTRAINT uk_mdm_inspection_method_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='检验方法静态定义，不包含仪器或实验室执行';

CREATE TABLE mdm_location (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '所属 Plant 内唯一且创建后不通过普通 API 修改的可寻址位置编码',
    name varchar(200) NOT NULL COMMENT 'Location 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    plant_id varchar(19) NOT NULL COMMENT '位置所属 Plant；必填且参与位置编码唯一约束',
    warehouse_area_id varchar(19) COMMENT '可选仓库区域引用；非仓储位置为空',
    location_type_id varchar(19) NOT NULL COMMENT '所属 mdm_location_type.id 引用；不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_location_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_location_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_location PRIMARY KEY (id),
    CONSTRAINT uk_mdm_location_plant_code UNIQUE (plant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='GEO 厂内统一可寻址物理位置；仓库区域引用可空';

CREATE TABLE mdm_location_type (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '平台唯一且创建后不通过普通 API 修改的动态分类编码',
    name varchar(200) NOT NULL COMMENT 'LocationType 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_location_type_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_location_type_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_location_type PRIMARY KEY (id),
    CONSTRAINT uk_mdm_location_type_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='动态维护的位置分类主数据；不承载按 Code 分支的业务能力';

CREATE TABLE mdm_material (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    code varchar(64) NOT NULL COMMENT '平台全局唯一且创建后不通过普通 API 修改的物料业务编码',
    name varchar(200) NOT NULL COMMENT '物料面向业务用户的名称；多语言显示由独立翻译能力扩展',
    category_id varchar(19) NOT NULL COMMENT '唯一叶子物料分类 ID；由 Application 保证引用完整性且不建立物理外键',
    base_uom_id varchar(19) NOT NULL COMMENT '创建后不可经普通业务接口修改的基础计量单位 ID',
    shelf_life_days int COMMENT '可选非负保质期天数；实际失效日期属于未来 Lot 事实',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API且业务编码不可复用',
    material_type varchar(24) NOT NULL COMMENT '物料用途；UNSPECIFIED 只表示迁移前存量待治理记录',
    batch_tracking_policy varchar(16) NOT NULL COMMENT '物料独立批次追踪策略；不存储实际 Lot',
    revision_no int NOT NULL DEFAULT 1 COMMENT '业务修订号，从 1 开始；与技术乐观锁 version 分离',
    CONSTRAINT ck_mdm_material_batch_policy CHECK (((batch_tracking_policy) IN ('UNSPECIFIED', 'NONE', 'REQUIRED'))),
    CONSTRAINT ck_mdm_material_revision_positive CHECK ((revision_no >= 1)),
    CONSTRAINT ck_mdm_material_shelf_life_non_negative CHECK (((shelf_life_days IS NULL) OR (shelf_life_days >= 0))),
    CONSTRAINT ck_mdm_material_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_material_type CHECK (((material_type) IN ('UNSPECIFIED', 'RAW_MATERIAL', 'SEMI_FINISHED', 'FINISHED_PRODUCT'))),
    CONSTRAINT ck_mdm_material_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_material PRIMARY KEY (id),
    CONSTRAINT uk_mdm_material_code UNIQUE (code),
    KEY ix_mdm_material_base_uom_status_code (deleted, base_uom_id, status, code, id),
    KEY ix_mdm_material_category_status_code (deleted, category_id, status, code, id),
    KEY ix_mdm_material_status_code (deleted, status, code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='GEO 权威物料主数据；不包含包装、批次、库存或动态规格事实';

CREATE TABLE mdm_material_category (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    code varchar(64) NOT NULL COMMENT '平台全局唯一且创建后不通过普通 API 修改的物料分类业务编码',
    name varchar(200) NOT NULL COMMENT '物料分类面向业务用户的名称；多语言显示由独立翻译能力扩展',
    parent_id varchar(19) COMMENT '可选父分类 mdm_material_category.id 引用；根分类为空且不建立物理外键',
    sort int NOT NULL COMMENT '同级分类的显示排序值；最终稳定顺序继续使用 code 和 id',
    default_shelf_life_days int COMMENT '创建 Material 时可复制的保质期天数默认建议；可空且必须非负',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API且业务编码不可复用',
    default_batch_tracking_policy varchar(16) COMMENT '可空创建建议；Material 创建时复制最终策略，分类变化不回写',
    CONSTRAINT ck_mdm_material_category_batch_policy CHECK (((default_batch_tracking_policy IS NULL) OR ((default_batch_tracking_policy) IN ('NONE', 'REQUIRED')))),
    CONSTRAINT ck_mdm_material_category_shelf_life_non_negative CHECK (((default_shelf_life_days IS NULL) OR (default_shelf_life_days >= 0))),
    CONSTRAINT ck_mdm_material_category_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_material_category_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_material_category PRIMARY KEY (id),
    CONSTRAINT uk_mdm_material_category_code UNIQUE (code),
    KEY ix_mdm_material_category_parent_sort (parent_id, sort, code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='GEO 企业动态维护的物料分类树及 Material 创建默认建议值';

CREATE TABLE mdm_plant (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    code varchar(64) NOT NULL COMMENT '平台全局唯一且创建后不通过普通 API 修改的 Plant 业务编码',
    name varchar(200) NOT NULL COMMENT 'Plant 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    time_zone_id varchar(64) COMMENT '工厂业务日期使用的权威 IANA Zone ID；存量 NULL 表示待治理，不可供新业务引用',
    CONSTRAINT ck_mdm_plant_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_plant_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_plant PRIMARY KEY (id),
    CONSTRAINT uk_mdm_plant_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='GEO 制造与仓储业务的顶层执行范围主数据';

CREATE TABLE mdm_production_line (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '所属 Workshop 内唯一且创建后不通过普通 API 修改的业务编码',
    name varchar(200) NOT NULL COMMENT 'ProductionLine 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    workshop_id varchar(19) NOT NULL COMMENT '所属 mdm_workshop.id 引用；不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_production_line_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_production_line_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_production_line PRIMARY KEY (id),
    CONSTRAINT uk_mdm_production_line_workshop_code UNIQUE (workshop_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Workshop 下的生产线主数据；父级完整性由 MDM Application 保证';

CREATE TABLE mdm_production_version (
    id varchar(19) NOT NULL COMMENT '生产版本的稳定技术 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    business_version varchar(64) NOT NULL COMMENT '业务版本标识，与 lock_version 技术并发版本分离',
    plant_id varchar(19) NOT NULL COMMENT '生产版本适用的 MDM 工厂 ID',
    output_material_id varchar(19) NOT NULL COMMENT '生产版本产出物料的 MDM ID',
    formula_revision_id varchar(19) NOT NULL COMMENT '生产版本固定引用的配方业务修订 ID',
    routing_revision_id varchar(19) NOT NULL COMMENT '生产版本固定引用的工艺路线业务修订 ID',
    valid_from date NOT NULL,
    valid_to date COMMENT '工厂业务日期的半开区间上界；NULL 表示无穷远',
    status varchar(16) NOT NULL COMMENT '生产版本状态：DRAFT、RELEASED 或 OBSOLETE',
    lock_version bigint NOT NULL DEFAULT 0,
    released_at datetime(6),
    obsolete_at datetime(6),
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_production_version_dates CHECK (((valid_to IS NULL) OR (valid_to > valid_from))),
    CONSTRAINT ck_mdm_production_version_lock CHECK ((lock_version >= 0)),
    CONSTRAINT ck_mdm_production_version_status CHECK (((status) IN ('DRAFT', 'RELEASED', 'OBSOLETE'))),
    CONSTRAINT ck_mdm_production_version_times CHECK (((((status) = 'DRAFT') AND (released_at IS NULL) AND (obsolete_at IS NULL)) OR (((status) = 'RELEASED') AND (released_at IS NOT NULL) AND (obsolete_at IS NULL)) OR (((status) = 'OBSOLETE') AND (released_at IS NOT NULL) AND (obsolete_at IS NOT NULL)))),
    CONSTRAINT pk_mdm_production_version PRIMARY KEY (id),
    CONSTRAINT uk_mdm_production_version_business UNIQUE (plant_id, output_material_id, business_version),
    CONSTRAINT uk_mdm_production_version_code UNIQUE (code),
    KEY ix_mdm_production_version_scope_code (plant_id, output_material_id, code, id),
    KEY ix_mdm_production_version_scope_dates (plant_id, output_material_id, status, valid_from, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='已发布制造定义组合；未来 MES 工单按 ID 读取并固定快照';

CREATE TABLE mdm_quality_specification (
    id varchar(19) NOT NULL COMMENT '质量标准稳定字符串 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL COMMENT '适用工厂 ID，无物理外键',
    material_id varchar(19) NOT NULL COMMENT '适用物料 ID，无物理外键',
    inspection_usage varchar(16) NOT NULL,
    plant_version bigint NOT NULL,
    material_revision_no int NOT NULL,
    material_version bigint NOT NULL,
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_quality_specification_usage CHECK (((inspection_usage) IN ('INCOMING', 'FINISHED_GOODS'))),
    CONSTRAINT pk_mdm_quality_specification PRIMARY KEY (id),
    CONSTRAINT uk_mdm_quality_specification_code UNIQUE (code),
    CONSTRAINT uk_mdm_quality_specification_scope UNIQUE (plant_id, material_id, inspection_usage),
    KEY ix_mdm_quality_specification_material (material_id, plant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='工厂、物料和用途唯一确定的质量标准主数据';

CREATE TABLE mdm_routing (
    id varchar(19) NOT NULL COMMENT '工艺路线身份的稳定技术 ID',
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    plant_id varchar(19) NOT NULL COMMENT '工艺路线所属工厂的 MDM ID',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_mdm_routing PRIMARY KEY (id),
    CONSTRAINT uk_mdm_routing_code UNIQUE (code),
    KEY ix_mdm_routing_plant_code (plant_id, code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='静态工艺路线身份；Plant 是资源一致性边界，不代表 MES 排程';

CREATE TABLE mdm_routing_revision (
    id varchar(19) NOT NULL COMMENT '工艺路线业务修订的稳定技术 ID',
    routing_id varchar(19) NOT NULL COMMENT '所属工艺路线身份 ID',
    revision_no int NOT NULL,
    status varchar(16) NOT NULL COMMENT '路线修订状态：DRAFT、RELEASED 或 OBSOLETE',
    lock_version bigint NOT NULL DEFAULT 0,
    released_at datetime(6),
    obsolete_at datetime(6),
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_routing_revision_lock CHECK ((lock_version >= 0)),
    CONSTRAINT ck_mdm_routing_revision_positive CHECK ((revision_no >= 1)),
    CONSTRAINT ck_mdm_routing_revision_status CHECK (((status) IN ('DRAFT', 'RELEASED', 'OBSOLETE'))),
    CONSTRAINT ck_mdm_routing_revision_times CHECK (((((status) = 'DRAFT') AND (released_at IS NULL) AND (obsolete_at IS NULL)) OR (((status) = 'RELEASED') AND (released_at IS NOT NULL) AND (obsolete_at IS NULL)) OR (((status) = 'OBSOLETE') AND (released_at IS NOT NULL) AND (obsolete_at IS NOT NULL)))),
    CONSTRAINT pk_mdm_routing_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_routing_revision_no UNIQUE (routing_id, revision_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='工艺路线业务修订；lock_version 是独立技术锁';

CREATE TABLE mdm_routing_step (
    id varchar(19) NOT NULL COMMENT '路线工序条目的稳定技术 ID',
    revision_id varchar(19) NOT NULL COMMENT '所属工艺路线业务修订 ID',
    sequence_no int NOT NULL,
    operation_code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    resource_type varchar(32) NOT NULL,
    resource_id varchar(19) NOT NULL COMMENT '工序引用的 MDM 产线或工作站 ID，由 resource_type 区分',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_routing_step_resource CHECK (((resource_type) IN ('PRODUCTION_LINE', 'WORKSTATION'))),
    CONSTRAINT ck_mdm_routing_step_sequence CHECK ((sequence_no > 0)),
    CONSTRAINT pk_mdm_routing_step PRIMARY KEY (id),
    CONSTRAINT uk_mdm_routing_step_sequence UNIQUE (revision_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='有序工序及 MDM 资源目录引用；发布后不可改写';

CREATE TABLE mdm_specification_item (
    id varchar(19) NOT NULL COMMENT '质量标准条目稳定字符串 ID',
    revision_id varchar(19) NOT NULL COMMENT '所属业务修订 ID，无物理外键',
    sequence_no int NOT NULL,
    characteristic_id varchar(19) NOT NULL COMMENT '检验特性 ID 快照引用',
    characteristic_version bigint NOT NULL,
    method_id varchar(19) NOT NULL COMMENT '检验方法 ID 快照引用',
    method_version bigint NOT NULL,
    uom_id varchar(19) NOT NULL COMMENT '检验数值单位 ID 快照引用',
    uom_version bigint NOT NULL,
    lower_limit decimal(24,9),
    target_value decimal(24,9),
    upper_limit decimal(24,9),
    mandatory boolean NOT NULL DEFAULT true,
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT ck_mdm_specification_item_range CHECK ((((lower_limit IS NULL) OR (target_value IS NULL) OR (lower_limit <= target_value)) AND ((target_value IS NULL) OR (upper_limit IS NULL) OR (target_value <= upper_limit)) AND ((lower_limit IS NULL) OR (upper_limit IS NULL) OR (lower_limit <= upper_limit)))),
    CONSTRAINT ck_mdm_specification_item_sequence CHECK ((sequence_no > 0)),
    CONSTRAINT pk_mdm_specification_item PRIMARY KEY (id),
    CONSTRAINT uk_mdm_specification_item_sequence UNIQUE (revision_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='已版本化的数值范围和特性、方法、单位快照，不保存检验结果';

CREATE TABLE mdm_specification_revision (
    id varchar(19) NOT NULL COMMENT '质量标准修订稳定字符串 ID',
    specification_id varchar(19) NOT NULL COMMENT '所属质量标准 ID，由 Application 保证关系完整性',
    revision_no int NOT NULL,
    status varchar(16) NOT NULL COMMENT '业务修订状态：DRAFT、RELEASED 或 OBSOLETE',
    lock_version bigint NOT NULL DEFAULT 0,
    released_at datetime(6),
    obsolete_at datetime(6),
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    plant_version bigint COMMENT '该修订发布时的工厂技术版本；旧修订未知时为空',
    material_revision_no int COMMENT '该修订发布时的物料业务修订号；旧修订未知时为空',
    material_version bigint COMMENT '该修订发布时的物料技术版本；旧修订未知时为空',
    specification_id_positive_unique_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'RELEASED') THEN specification_id END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_mdm_specification_revision_lock CHECK ((lock_version >= 0)),
    CONSTRAINT ck_mdm_specification_revision_no CHECK ((revision_no >= 1)),
    CONSTRAINT ck_mdm_specification_revision_scope_version CHECK ((((plant_version IS NULL) AND (material_revision_no IS NULL) AND (material_version IS NULL)) OR ((plant_version IS NOT NULL) AND (material_revision_no IS NOT NULL) AND (material_version IS NOT NULL) AND (plant_version >= 0) AND (material_revision_no >= 1) AND (material_version >= 0)))),
    CONSTRAINT ck_mdm_specification_revision_status CHECK (((status) IN ('DRAFT', 'RELEASED', 'OBSOLETE'))),
    CONSTRAINT ck_mdm_specification_revision_times CHECK (((((status) = 'DRAFT') AND (released_at IS NULL) AND (obsolete_at IS NULL)) OR (((status) = 'RELEASED') AND (released_at IS NOT NULL) AND (obsolete_at IS NULL)) OR (((status) = 'OBSOLETE') AND (released_at IS NOT NULL) AND (obsolete_at IS NOT NULL)))),
    CONSTRAINT pk_mdm_specification_revision PRIMARY KEY (id),
    CONSTRAINT uk_mdm_specification_revision_no UNIQUE (specification_id, revision_no),
    CONSTRAINT uk_mdm_specification_one_released UNIQUE (specification_id_positive_unique_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='质量标准业务修订；lock_version 是技术乐观锁';

CREATE TABLE mdm_uom (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '全局唯一单位编码；优先直接使用 UCUM，企业扩展使用 mom: 命名空间',
    name varchar(200) NOT NULL COMMENT '计量单位面向业务用户的名称；多语言显示由独立翻译能力扩展',
    symbol varchar(32) NOT NULL COMMENT '面向用户的显示符号；允许不同单位使用相同符号',
    category_id varchar(19) NOT NULL COMMENT '所属计量单位类别 ID；由 Application 保证引用完整性且不建立物理外键',
    reference_unit boolean NOT NULL DEFAULT false COMMENT '是否为类别唯一基准单位；基准身份创建后不可变',
    ucum_not_applicable_reason varchar(500) COMMENT 'mom: 企业扩展单位无法采用 UCUM 时的必填治理说明',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED；基准单位不独立启停',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API且编码不可复用',
    category_reference_unique_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((reference_unit = true) AND (deleted = false)) THEN category_id END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_mdm_uom_custom_code_reason CHECK (((((code) LIKE 'mom:%') AND (ucum_not_applicable_reason IS NOT NULL) AND (trim((ucum_not_applicable_reason)) <> '')) OR (((code) NOT LIKE 'mom:%') AND (ucum_not_applicable_reason IS NULL)))),
    CONSTRAINT ck_mdm_uom_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_uom_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_uom PRIMARY KEY (id),
    CONSTRAINT uk_mdm_uom_code UNIQUE (code),
    KEY ix_mdm_uom_by_category_status_code (category_id, status, code, id),
    CONSTRAINT uk_mdm_uom_category_reference UNIQUE (category_reference_unique_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='GEO 权威计量单位目录；每个单位只能属于一个计量单位类别';

CREATE TABLE mdm_uom_category (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '全局唯一且创建后不可普通修改的计量单位类别编码',
    name varchar(200) NOT NULL COMMENT '计量单位类别面向业务用户的名称；多语言显示由独立翻译能力扩展',
    dimension_id varchar(19) NOT NULL COMMENT '所属物理量纲 ID；由 Application 保证引用完整性且不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API且编码不可复用',
    CONSTRAINT ck_mdm_uom_category_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_uom_category_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_uom_category PRIMARY KEY (id),
    CONSTRAINT uk_mdm_uom_category_code UNIQUE (code),
    KEY ix_mdm_uom_category_dimension_status_code (dimension_id, status, code, id),
    KEY ix_mdm_uom_category_status_code (status, code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='面向业务用户的计量单位类别及换算兼容边界';

CREATE TABLE mdm_uom_conversion_rule (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    uom_id varchar(19) NOT NULL COMMENT '非基准计量单位 ID；由 Application 保证引用完整性且不建立物理外键',
    version_no int NOT NULL COMMENT '同一 UOM 内从 1 递增且不可复用的业务规则版本号',
    algorithm_type varchar(16) NOT NULL COMMENT '受控算法类型；V1 只允许 AFFINE',
    multiplier decimal(50,30) NOT NULL COMMENT '换算到基准单位时使用的正乘数',
    offset_value decimal(50,30) NOT NULL COMMENT '乘法后加到基准单位值上的偏移量',
    calculation_precision int NOT NULL COMMENT 'BigDecimal MathContext 使用的有效数字精度，范围 1..34',
    rounding_mode varchar(32) NOT NULL COMMENT '计算无法精确表示时使用的 Java BigDecimal 舍入模式',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '规则版本状态；同一 UOM 最多一个 ENABLED 版本',
    created_at datetime(6) NOT NULL COMMENT '规则版本首次发布 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '规则版本创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '规则状态最近修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '规则状态最近修改 Actor ID',
    lock_version bigint NOT NULL DEFAULT 0 COMMENT '规则状态切换使用的 MyBatis-Plus 乐观锁版本号',
    current_unique_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'ENABLED') THEN uom_id END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_mdm_uom_conversion_rule_algorithm CHECK (((algorithm_type) = 'AFFINE')),
    CONSTRAINT ck_mdm_uom_conversion_rule_lock_version_non_negative CHECK ((lock_version >= 0)),
    CONSTRAINT ck_mdm_uom_conversion_rule_multiplier_positive CHECK ((multiplier > 0)),
    CONSTRAINT ck_mdm_uom_conversion_rule_precision CHECK (((calculation_precision >= 1) AND (calculation_precision <= 34))),
    CONSTRAINT ck_mdm_uom_conversion_rule_rounding CHECK (((rounding_mode) IN ('UP', 'DOWN', 'CEILING', 'FLOOR', 'HALF_UP', 'HALF_DOWN', 'HALF_EVEN', 'UNNECESSARY'))),
    CONSTRAINT ck_mdm_uom_conversion_rule_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_uom_conversion_rule_version_positive CHECK ((version_no > 0)),
    CONSTRAINT pk_mdm_uom_conversion_rule PRIMARY KEY (id),
    CONSTRAINT uk_mdm_uom_conversion_rule_version UNIQUE (uom_id, version_no),
    CONSTRAINT uk_mdm_uom_conversion_rule_current UNIQUE (current_unique_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='非基准单位到类别基准单位的不可变仿射换算规则版本';

CREATE TABLE mdm_warehouse (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '所属 Plant 内唯一且创建后不通过普通 API 修改的业务编码',
    name varchar(200) NOT NULL COMMENT 'Warehouse 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    plant_id varchar(19) NOT NULL COMMENT '所属 mdm_plant.id 引用；不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_warehouse_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_warehouse_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_warehouse PRIMARY KEY (id),
    CONSTRAINT uk_mdm_warehouse_plant_code UNIQUE (plant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Plant 下的仓库主数据；不承载库存策略和容量事实';

CREATE TABLE mdm_warehouse_area (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '所属 Warehouse 内唯一且创建后不通过普通 API 修改的业务编码',
    name varchar(200) NOT NULL COMMENT 'WarehouseArea 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    warehouse_id varchar(19) NOT NULL COMMENT '所属 mdm_warehouse.id 引用；不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_warehouse_area_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_warehouse_area_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_warehouse_area PRIMARY KEY (id),
    CONSTRAINT uk_mdm_warehouse_area_warehouse_code UNIQUE (warehouse_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Warehouse 下的仓库区域主数据；不承载库存事实';

CREATE TABLE mdm_workshop (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '所属 Plant 内唯一且创建后不通过普通 API 修改的业务编码',
    name varchar(200) NOT NULL COMMENT 'Workshop 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    plant_id varchar(19) NOT NULL COMMENT '所属 mdm_plant.id 引用；不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_workshop_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_workshop_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_workshop PRIMARY KEY (id),
    CONSTRAINT uk_mdm_workshop_plant_code UNIQUE (plant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Plant 下的车间主数据；父级完整性由 MDM Application 保证';

CREATE TABLE mdm_workstation (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    code varchar(64) NOT NULL COMMENT '所属 ProductionLine 内唯一且创建后不通过普通 API 修改的业务编码',
    name varchar(200) NOT NULL COMMENT 'Workstation 面向业务用户的名称；多语言显示由独立翻译能力扩展',
    production_line_id varchar(19) NOT NULL COMMENT '所属 mdm_production_line.id 引用；不建立物理外键',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '简单生命周期状态：ENABLED 或 DISABLED',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识；V1 不提供删除 API',
    CONSTRAINT ck_mdm_workstation_status CHECK (((status) IN ('ENABLED', 'DISABLED'))),
    CONSTRAINT ck_mdm_workstation_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_mdm_workstation PRIMARY KEY (id),
    CONSTRAINT uk_mdm_workstation_line_code UNIQUE (production_line_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='ProductionLine 下的工位主数据；父级完整性由 MDM Application 保证';
