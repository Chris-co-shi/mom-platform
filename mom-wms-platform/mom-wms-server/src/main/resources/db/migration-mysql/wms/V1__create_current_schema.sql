-- wms：根据 PostgreSQL 17 空库 Flyway 迁移至 V17 后的有效结构建立 MySQL 8.4 基线。

CREATE TABLE wms_code_rule (
    id varchar(19) NOT NULL COMMENT 'WMS 编码规则身份的技术主键',
    rule_code varchar(64) NOT NULL,
    usage_scenario varchar(120),
    description varchar(1000),
    version bigint NOT NULL DEFAULT 0 COMMENT '规则编辑的乐观并发版本，从零开始递增',
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT ck_wms_code_rule_usage_scenario CHECK (((usage_scenario IS NULL) OR (char_length(trim((usage_scenario))) > 0))),
    CONSTRAINT ck_wms_code_rule_version CHECK ((version >= 0)),
    CONSTRAINT pk_wms_code_rule PRIMARY KEY (id),
    CONSTRAINT uk_wms_code_rule_code UNIQUE (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 编码规则身份；规则行是发布与正式发号的并发串行化边界';

CREATE TABLE wms_code_rule_issue (
    rule_id varchar(19) NOT NULL COMMENT '所属 WMS 规则身份 ID；完整编码在该规则内唯一',
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    code varchar(128) NOT NULL,
    revision_id varchar(19) NOT NULL COMMENT '本次正式发号实际使用的不可变发布修订 ID',
    revision_no int NOT NULL,
    created_at datetime(6) NOT NULL,
    CONSTRAINT ck_wms_code_rule_issue_revision CHECK ((revision_no > 0)),
    CONSTRAINT pk_wms_code_rule_issue PRIMARY KEY (request_key),
    CONSTRAINT uk_wms_code_rule_issue_code UNIQUE (rule_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 正式发号幂等记录和规则内完整编码唯一约束';

CREATE TABLE wms_code_rule_revision (
    id varchar(19) NOT NULL COMMENT '规则修订的技术主键',
    rule_id varchar(19) NOT NULL COMMENT '所属 WMS 编码规则身份；由应用层保证引用存在',
    revision_no int NOT NULL,
    status varchar(16) NOT NULL COMMENT '修订生命周期：草稿、已启用或已停用',
    definition json NOT NULL,
    created_at datetime(6) NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    draft_rule_id_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'DRAFT') THEN rule_id END) STORED COMMENT '条件唯一约束的生效键',
    enabled_rule_id_key varchar(19) GENERATED ALWAYS AS (CASE WHEN ((status) = 'ENABLED') THEN rule_id END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_wms_code_rule_revision_definition CHECK (((lower(json_type(definition)) = 'object') AND ((json_unquote(json_extract(definition, '$.schemaVersion'))) = '1') AND (lower(json_type(json_extract(definition, '$.segments'))) = 'array'))),
    CONSTRAINT ck_wms_code_rule_revision_no CHECK ((revision_no >= 1)),
    CONSTRAINT ck_wms_code_rule_revision_status CHECK (((status) IN ('DRAFT', 'ENABLED', 'DISABLED'))),
    CONSTRAINT pk_wms_code_rule_revision PRIMARY KEY (id),
    CONSTRAINT uk_wms_code_rule_revision_no UNIQUE (rule_id, revision_no),
    CONSTRAINT uk_wms_code_rule_one_draft UNIQUE (draft_rule_id_key),
    CONSTRAINT uk_wms_code_rule_one_enabled UNIQUE (enabled_rule_id_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 编码规则不可变发布修订；草稿可编辑';

CREATE TABLE wms_code_rule_sequence (
    rule_id varchar(19) NOT NULL COMMENT '规则身份 ID；不建立业务外键',
    `last_value` bigint NOT NULL COMMENT '已成功提交的最大流水号；回滚时不消耗',
    scope_key varchar(8) NOT NULL DEFAULT '' COMMENT 'UTC 年/月/日周期键；空字符串表示不重置',
    CONSTRAINT ck_wms_code_rule_sequence_positive CHECK (`last_value` > 0),
    CONSTRAINT pk_wms_code_rule_sequence PRIMARY KEY (rule_id, scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 编码规则正式流水，作用域为规则身份和 UTC 重置周期；旧流水保留在空周期';

CREATE TABLE wms_i18n_message_definition (
    id varchar(19) NOT NULL COMMENT '文案定义技术主键',
    message_key varchar(160) NOT NULL,
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT '文案定义乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_wms_i18n_message_definition_key CHECK ((regexp_like(message_key, '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*\\z', 'c'))),
    CONSTRAINT ck_wms_i18n_message_definition_version CHECK ((version >= 0)),
    CONSTRAINT pk_wms_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_wms_i18n_message_definition_key UNIQUE (message_key),
    KEY ix_wms_i18n_message_runtime (deleted, enabled, message_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 自有动态文案定义';

CREATE TABLE wms_i18n_translation (
    id varchar(19) NOT NULL COMMENT '译文技术主键',
    message_id varchar(19) NOT NULL COMMENT '所属文案定义 ID',
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL,
    version bigint NOT NULL DEFAULT 0 COMMENT '译文乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_wms_i18n_translation_text CHECK (((char_length((message_text)) >= 1) AND (char_length((message_text)) <= 4096))),
    CONSTRAINT ck_wms_i18n_translation_version CHECK ((version >= 0)),
    CONSTRAINT pk_wms_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_wms_i18n_translation_message_locale UNIQUE (message_id, locale_code),
    KEY ix_wms_i18n_translation_runtime (deleted, locale_code, message_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 自有动态译文';

CREATE TABLE wms_inventory_balance (
    id varchar(19) NOT NULL COMMENT '余额技术主键',
    material_id varchar(19) NOT NULL COMMENT 'MDM 物料稳定 ID 快照引用',
    material_code varchar(64) NOT NULL,
    material_name varchar(200) NOT NULL,
    uom_code varchar(64) NOT NULL,
    batch_code varchar(128),
    sn_code varchar(128),
    warehouse_id varchar(19) NOT NULL COMMENT 'MDM 仓库稳定 ID 快照引用',
    warehouse_code varchar(64) NOT NULL,
    location_id varchar(19) NOT NULL COMMENT 'MDM 库位稳定 ID 快照引用',
    location_code varchar(64) NOT NULL,
    quality_status varchar(16) NOT NULL,
    warehouse_status varchar(16) NOT NULL,
    quantity decimal(24,6) NOT NULL,
    updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at datetime(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '该完整库存维度的余额行首次建立时间；迁移前旧行未知',
    batch_code_is_null boolean GENERATED ALWAYS AS (batch_code IS NULL) STORED COMMENT 'batch_code 空值判重标记',
    batch_code_key varchar(128) GENERATED ALWAYS AS (coalesce(batch_code, '')) STORED COMMENT 'batch_code 判重值',
    sn_code_is_null boolean GENERATED ALWAYS AS (sn_code IS NULL) STORED COMMENT 'sn_code 空值判重标记',
    sn_code_key varchar(128) GENERATED ALWAYS AS (coalesce(sn_code, '')) STORED COMMENT 'sn_code 判重值',
    sn_unique_key varchar(128) GENERATED ALWAYS AS (CASE WHEN ((sn_code IS NOT NULL) AND (quantity > 0)) THEN sn_code END) STORED COMMENT '条件唯一约束的生效键',
    CONSTRAINT ck_wms_inventory_balance_nonnegative CHECK ((quantity >= 0)),
    CONSTRAINT ck_wms_inventory_balance_quality CHECK (((quality_status) IN ('PENDING', 'QUALIFIED', 'RESTRICTED', 'REJECTED'))),
    CONSTRAINT ck_wms_inventory_balance_warehouse_status CHECK (((warehouse_status) IN ('NORMAL', 'FROZEN'))),
    CONSTRAINT pk_wms_inventory_balance PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_balance_dimension UNIQUE (material_id, batch_code_is_null, batch_code_key, sn_code_is_null, sn_code_key, location_id, quality_status, warehouse_status),
    KEY ix_wms_balance_live_material_warehouse (material_id, warehouse_id, id),
    KEY ix_wms_inventory_balance_count_warehouse (warehouse_id, id),
    CONSTRAINT uk_wms_inventory_balance_sn UNIQUE (sn_unique_key, warehouse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='按物料、批次、SN、库位、质量及仓储状态唯一确定的权威余额';

CREATE TABLE wms_inventory_count_line (
    id varchar(19) NOT NULL COMMENT '盘点明细技术主键',
    task_id varchar(19) NOT NULL COMMENT '所属盘点任务 ID',
    material_id varchar(19) NOT NULL COMMENT '账面或账外实物对应 MDM 物料稳定 ID',
    material_code varchar(64) NOT NULL COMMENT '快照时物料编码',
    material_name varchar(200) NOT NULL COMMENT '快照时物料名称',
    uom_code varchar(64) NOT NULL COMMENT '盘点数量使用的物料库存基础单位',
    batch_code varchar(128) COMMENT '真实已有批次身份；空值表示无批次',
    sn_code varchar(128) COMMENT '物理 SN 标签；可对应不同仓储状态明细',
    warehouse_id varchar(19) NOT NULL COMMENT '盘点仓库稳定 ID',
    warehouse_code varchar(64) NOT NULL COMMENT '盘点仓库编码快照',
    location_id varchar(19) NOT NULL COMMENT '盘点库位稳定 ID',
    location_code varchar(64) NOT NULL COMMENT '盘点库位编码快照',
    quality_status varchar(16) NOT NULL COMMENT '实物明确归属的质量状态',
    warehouse_status varchar(16) NOT NULL COMMENT '实物明确归属的仓储状态',
    book_quantity decimal(24,6) NOT NULL COMMENT '开始盘点时固化的账面数量',
    counted_quantity decimal(24,6) COMMENT '人工实盘数量；NULL 表示未盘，零表示实盘为零',
    snapshot_by varchar(128) NOT NULL COMMENT '生成账面快照或录入账外行的操作人',
    snapshot_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生成账面快照或录入账外行的数据库时间',
    counted_by varchar(128) COMMENT '最后录入实盘数量的可信操作人',
    counted_at datetime(6) COMMENT '最后录入实盘数量的数据库时间',
    batch_code_is_null boolean GENERATED ALWAYS AS (batch_code IS NULL) STORED COMMENT 'batch_code 空值判重标记',
    batch_code_key varchar(128) GENERATED ALWAYS AS (coalesce(batch_code, '')) STORED COMMENT 'batch_code 判重值',
    sn_code_is_null boolean GENERATED ALWAYS AS (sn_code IS NULL) STORED COMMENT 'sn_code 空值判重标记',
    sn_code_key varchar(128) GENERATED ALWAYS AS (coalesce(sn_code, '')) STORED COMMENT 'sn_code 判重值',
    CONSTRAINT ck_wms_inventory_count_line_count_actor CHECK ((((counted_quantity IS NULL) AND (counted_by IS NULL) AND (counted_at IS NULL)) OR ((counted_quantity IS NOT NULL) AND (counted_by IS NOT NULL) AND (counted_at IS NOT NULL)))),
    CONSTRAINT ck_wms_inventory_count_line_quality CHECK (((quality_status) IN ('PENDING', 'QUALIFIED', 'RESTRICTED', 'REJECTED'))),
    CONSTRAINT ck_wms_inventory_count_line_quantities CHECK (((book_quantity >= 0) AND ((counted_quantity IS NULL) OR (counted_quantity >= 0)))),
    CONSTRAINT ck_wms_inventory_count_line_warehouse_status CHECK (((warehouse_status) IN ('NORMAL', 'FROZEN'))),
    CONSTRAINT pk_wms_inventory_count_line PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_count_line_dimension UNIQUE (task_id, material_id, batch_code_is_null, batch_code_key, sn_code_is_null, sn_code_key, location_id, quality_status, warehouse_status),
    KEY ix_wms_inventory_count_line_task (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='盘点开始时账面快照及人工实盘数量；确认后不可变';

CREATE TABLE wms_inventory_count_scope_lock (
    warehouse_id varchar(19) NOT NULL COMMENT '被盘点独占的 MDM 仓库稳定 ID',
    task_id varchar(19) NOT NULL COMMENT '当前持有整仓锁的盘点任务 ID',
    acquired_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '整仓锁写入数据库时间',
    CONSTRAINT pk_wms_inventory_count_scope_lock PRIMARY KEY (warehouse_id),
    CONSTRAINT uk_wms_inventory_count_scope_lock_task UNIQUE (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='整仓盘点独占锁；与任务状态及库存调整同一事务管理';

CREATE TABLE wms_inventory_count_task (
    id varchar(19) NOT NULL COMMENT '盘点任务技术主键',
    task_no varchar(32) NOT NULL COMMENT 'WMS 编码规则生成的盘点业务编号',
    request_key varchar(128) NOT NULL COMMENT '创建盘点草稿的客户端稳定幂等请求键',
    warehouse_id varchar(19) NOT NULL COMMENT 'MDM 仓库稳定 ID',
    warehouse_code varchar(64) NOT NULL COMMENT '创建时仓库编码快照',
    location_ids json NOT NULL DEFAULT (json_array()) COMMENT '限定库位 ID 集合；空数组表示整仓全部库位',
    material_ids json NOT NULL DEFAULT (json_array()) COMMENT '限定物料 ID 集合；空数组表示全部物料',
    status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、COUNTING、CONFIRMED、COMPLETED 或 CANCELLED',
    transaction_id varchar(19) COMMENT '整单库存调整产生的唯一交易 ID',
    created_by varchar(128) NOT NULL COMMENT '创建任务的可信操作人',
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务创建数据库时间',
    updated_by varchar(128) NOT NULL COMMENT '最后编辑草稿范围的可信操作人',
    updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最后一次任务状态或范围更新时间',
    started_by varchar(128) COMMENT '开始盘点并生成快照的可信操作人',
    started_at datetime(6) COMMENT '锁定整仓并生成快照的数据库时间',
    confirmed_by varchar(128) COMMENT '确认差异的可信操作人',
    confirmed_at datetime(6) COMMENT '差异确认数据库时间',
    completed_by varchar(128) COMMENT '执行整单库存调整的可信操作人',
    completed_at datetime(6) COMMENT '调整完成并释放整仓锁的数据库时间',
    cancelled_by varchar(128) COMMENT '取消盘点的可信操作人',
    cancelled_at datetime(6) COMMENT '取消并释放整仓锁的数据库时间',
    version bigint NOT NULL DEFAULT 0 COMMENT '任务状态和范围更新版本',
    CONSTRAINT ck_wms_inventory_count_task_lifecycle CHECK (((((status) <> 'DRAFT') OR (started_at IS NULL)) AND (((status) NOT IN ('COUNTING', 'CONFIRMED', 'COMPLETED')) OR (started_at IS NOT NULL)) AND (((status) NOT IN ('CONFIRMED', 'COMPLETED')) OR (confirmed_at IS NOT NULL)) AND (((status) <> 'COMPLETED') OR (completed_at IS NOT NULL)) AND (((status) <> 'CANCELLED') OR (cancelled_at IS NOT NULL)))),
    CONSTRAINT ck_wms_inventory_count_task_status CHECK (((status) IN ('DRAFT', 'COUNTING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'))),
    CONSTRAINT ck_wms_inventory_count_task_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_wms_inventory_count_task PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_count_task_no UNIQUE (task_no),
    CONSTRAINT uk_wms_inventory_count_task_request UNIQUE (request_key),
    CONSTRAINT uk_wms_inventory_count_task_transaction UNIQUE (transaction_id),
    KEY ix_wms_inventory_count_task_warehouse_created (warehouse_id, created_at DESC, id DESC),
    CONSTRAINT ck_wms_inventory_count_task_location_ids_array CHECK (json_type(location_ids) = 'ARRAY'),
    CONSTRAINT ck_wms_inventory_count_task_material_ids_array CHECK (json_type(material_ids) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='单仓停账盘点任务；完成和取消后保留业务生命周期审计';

CREATE TABLE wms_inventory_freeze (
    id varchar(19) NOT NULL COMMENT '冻结限制技术主键',
    transaction_id varchar(19) NOT NULL COMMENT '创建该限制的唯一冻结库存交易',
    original_balance_id varchar(19) NOT NULL COMMENT '冻结前 NORMAL 权威余额行标识',
    material_id varchar(19) NOT NULL COMMENT '冻结时 MDM 物料稳定标识快照',
    material_code varchar(64) NOT NULL,
    material_name varchar(200) NOT NULL,
    uom_code varchar(64) NOT NULL,
    batch_code varchar(128),
    sn_code varchar(128),
    warehouse_id varchar(19) NOT NULL COMMENT '冻结时 MDM 仓库稳定标识快照',
    warehouse_code varchar(64) NOT NULL,
    location_id varchar(19) NOT NULL COMMENT '冻结时 MDM 库位稳定标识快照',
    location_code varchar(64) NOT NULL,
    quality_status varchar(16) NOT NULL,
    reason_code varchar(32) NOT NULL COMMENT 'WMS 受控冻结原因编码，不表示 QMS 质量结论',
    note varchar(500),
    original_quantity decimal(24,6) NOT NULL,
    remaining_quantity decimal(24,6) NOT NULL COMMENT '该冻结限制尚未解除的数量，由行锁与条件更新保护',
    source_type varchar(64) NOT NULL,
    source_id varchar(128) NOT NULL COMMENT '发起冻结的业务来源对象标识快照',
    operator_id varchar(128) NOT NULL COMMENT '执行冻结的责任主体标识快照',
    frozen_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_wms_inventory_freeze_other_note CHECK ((((reason_code) <> 'OTHER') OR ((note IS NOT NULL) AND (char_length(trim((note))) > 0)))),
    CONSTRAINT ck_wms_inventory_freeze_quality CHECK (((quality_status) IN ('PENDING', 'QUALIFIED', 'RESTRICTED', 'REJECTED'))),
    CONSTRAINT ck_wms_inventory_freeze_quantities CHECK (((original_quantity > 0) AND (remaining_quantity >= 0) AND (remaining_quantity <= original_quantity))),
    CONSTRAINT ck_wms_inventory_freeze_reason CHECK (((reason_code) IN ('PACKAGE_DAMAGE', 'STOCK_DISCREPANCY', 'WAREHOUSE_CHECK', 'SAFETY_HOLD', 'OTHER'))),
    CONSTRAINT pk_wms_inventory_freeze PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_freeze_transaction UNIQUE (transaction_id),
    KEY ix_wms_inventory_freeze_active_dimension (material_id, warehouse_id, location_id, batch_code, sn_code, quality_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='独立追踪每次仓储冻结来源、原因、库存快照及剩余受限数量';

CREATE TABLE wms_inventory_transaction (
    id varchar(19) NOT NULL COMMENT '库存交易技术主键',
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    occurred_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    transaction_type varchar(64) COMMENT '交易业务类型；Phase 1 历史交易缺失时为空',
    source_type varchar(64) COMMENT '来源业务类型；Phase 1 历史交易缺失时为空',
    source_id varchar(128) COMMENT '来源业务对象标识；Phase 1 历史交易缺失时为空',
    operator_id varchar(128) COMMENT '实际操作责任主体标识；Phase 1 历史交易缺失时为空',
    CONSTRAINT ck_wms_inventory_transaction_facts_complete CHECK ((((transaction_type IS NULL) AND (source_type IS NULL) AND (source_id IS NULL) AND (operator_id IS NULL)) OR ((transaction_type IS NOT NULL) AND (trim((transaction_type)) <> '') AND (source_type IS NOT NULL) AND (trim((source_type)) <> '') AND (source_id IS NOT NULL) AND (trim((source_id)) <> '') AND (operator_id IS NOT NULL) AND (trim((operator_id)) <> '')))),
    CONSTRAINT pk_wms_inventory_transaction PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_transaction_request_key UNIQUE (request_key),
    KEY ix_wms_history_transaction_source (source_type, source_id, id),
    KEY ix_wms_history_transaction_time (occurred_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='按业务请求键幂等提交的库存交易';

CREATE TABLE wms_inventory_transaction_entry (
    id varchar(19) NOT NULL COMMENT '分录技术主键',
    transaction_id varchar(19) NOT NULL COMMENT '所属库存交易 ID',
    line_no int NOT NULL,
    balance_id varchar(19) NOT NULL COMMENT '记账时受影响余额行标识，仅作技术关联',
    delta_quantity decimal(24,6) NOT NULL,
    material_id varchar(19) COMMENT '记账发生时物料稳定标识快照',
    material_code varchar(64),
    material_name varchar(200),
    uom_code varchar(64),
    batch_code varchar(128) COMMENT '记账发生时批次快照，非批次库存为空',
    sn_code varchar(128) COMMENT '记账发生时序列号快照，非 SN 管理库存为空',
    warehouse_id varchar(19) COMMENT '记账发生时仓库稳定标识快照',
    warehouse_code varchar(64),
    location_id varchar(19) COMMENT '记账发生时库位稳定标识快照',
    location_code varchar(64),
    quality_status varchar(16) COMMENT '记账发生时质量状态快照',
    warehouse_status varchar(16) COMMENT '记账发生时仓储状态快照',
    before_quantity decimal(24,6) COMMENT '本分录作用前该库存维度数量',
    after_quantity decimal(24,6) COMMENT '本分录作用后该库存维度数量',
    CONSTRAINT ck_wms_inventory_transaction_entry_nonzero CHECK ((delta_quantity <> 0)),
    CONSTRAINT ck_wms_inventory_transaction_entry_snapshot_complete CHECK ((((material_id IS NULL) AND (material_code IS NULL) AND (material_name IS NULL) AND (uom_code IS NULL) AND (batch_code IS NULL) AND (sn_code IS NULL) AND (warehouse_id IS NULL) AND (warehouse_code IS NULL) AND (location_id IS NULL) AND (location_code IS NULL) AND (quality_status IS NULL) AND (warehouse_status IS NULL) AND (before_quantity IS NULL) AND (after_quantity IS NULL)) OR ((material_id IS NOT NULL) AND (material_code IS NOT NULL) AND (material_name IS NOT NULL) AND (uom_code IS NOT NULL) AND (warehouse_id IS NOT NULL) AND (warehouse_code IS NOT NULL) AND (location_id IS NOT NULL) AND (location_code IS NOT NULL) AND (quality_status IS NOT NULL) AND (warehouse_status IS NOT NULL) AND (before_quantity IS NOT NULL) AND (after_quantity IS NOT NULL) AND (before_quantity >= 0) AND (after_quantity >= 0) AND ((before_quantity + delta_quantity) = after_quantity)))),
    CONSTRAINT ck_wms_inventory_transaction_entry_snapshot_quality CHECK (((quality_status IS NULL) OR ((quality_status) IN ('PENDING', 'QUALIFIED', 'RESTRICTED', 'REJECTED')))),
    CONSTRAINT ck_wms_inventory_transaction_entry_snapshot_warehouse_status CHECK (((warehouse_status IS NULL) OR ((warehouse_status) IN ('NORMAL', 'FROZEN')))),
    CONSTRAINT pk_wms_inventory_transaction_entry PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_transaction_entry_line UNIQUE (transaction_id, line_no),
    KEY ix_wms_history_entry_batch (batch_code, transaction_id),
    KEY ix_wms_history_entry_material (material_id, transaction_id),
    KEY ix_wms_history_entry_sn (sn_code, transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='库存交易不可变的正负数量分录';

CREATE TABLE wms_inventory_unfreeze (
    id varchar(19) NOT NULL COMMENT '解冻历史技术主键',
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    freeze_id varchar(19) NOT NULL COMMENT '被部分或全部解除的冻结限制',
    transaction_id varchar(19) NOT NULL COMMENT '本次解冻对应的唯一库存交易',
    quantity decimal(24,6) NOT NULL,
    note varchar(500) COMMENT '解冻处理说明快照',
    source_type varchar(64) NOT NULL,
    source_id varchar(128) NOT NULL COMMENT '发起解冻的业务来源对象标识快照',
    operator_id varchar(128) NOT NULL COMMENT '执行解冻的责任主体标识快照',
    occurred_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_wms_inventory_unfreeze_note CHECK (((note IS NOT NULL) AND (char_length(trim((note))) > 0))),
    CONSTRAINT ck_wms_inventory_unfreeze_quantity CHECK ((quantity > 0)),
    CONSTRAINT pk_wms_inventory_unfreeze PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_unfreeze_request UNIQUE (request_key),
    CONSTRAINT uk_wms_inventory_unfreeze_transaction UNIQUE (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='每次解冻的不可变处理历史；重复请求键返回原交易';

CREATE TABLE wms_opening_document (
    id varchar(19) NOT NULL COMMENT '期初单技术主键',
    document_no varchar(32) NOT NULL COMMENT '期初单业务编号',
    warehouse_id varchar(19) NOT NULL COMMENT 'MDM 仓库稳定 ID 引用',
    warehouse_code varchar(64) NOT NULL COMMENT '建账时仓库编码快照',
    status varchar(16) NOT NULL COMMENT 'DRAFT 可维护，POSTED 已过账且不可修改或删除',
    transaction_id varchar(19) COMMENT '期初过账产生的不可变库存交易 ID',
    created_by varchar(128) NOT NULL COMMENT '创建期初草稿的实际操作人',
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version bigint NOT NULL DEFAULT 0 COMMENT '明细变更版本，用于事务外引用核验后的提交冲突检查',
    updated_by varchar(128) COMMENT '最后修改草稿明细的实际操作人；旧记录可能未知',
    posted_by varchar(128) COMMENT '执行期初过账的实际操作人；旧记录可能未知',
    posted_at datetime(6) COMMENT '期初过账成功的数据库时间；旧记录可能未知',
    CONSTRAINT ck_wms_opening_document_status CHECK (((status) IN ('DRAFT', 'POSTED'))),
    CONSTRAINT ck_wms_opening_document_version_non_negative CHECK (version >= 0),
    CONSTRAINT pk_wms_opening_document PRIMARY KEY (id),
    CONSTRAINT uk_wms_opening_document_document_no UNIQUE (document_no),
    CONSTRAINT uk_wms_opening_document_one_per_warehouse UNIQUE (warehouse_id),
    CONSTRAINT uk_wms_opening_document_transaction UNIQUE (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='期初库存建账单；过账后不可变并关联唯一库存交易';

CREATE TABLE wms_opening_document_line (
    id varchar(19) NOT NULL COMMENT '期初明细技术主键',
    document_id varchar(19) NOT NULL COMMENT '所属期初单技术 ID',
    line_no int NOT NULL,
    material_id varchar(19) NOT NULL COMMENT 'MDM 物料稳定 ID 引用',
    material_code varchar(64) NOT NULL COMMENT 'MDM 物料编码快照',
    material_name varchar(200) NOT NULL COMMENT 'MDM 物料名称快照',
    batch_code varchar(128) COMMENT '沿用真实既有批次身份；策略要求批次时不得生成虚假收货批次',
    sn_code varchar(128) COMMENT '可空的唯一标签码；有值时由库存记账的正余额唯一约束保护',
    warehouse_id varchar(19) NOT NULL COMMENT 'MDM 仓库稳定 ID 引用',
    warehouse_code varchar(64) NOT NULL COMMENT '建账时仓库编码快照',
    location_id varchar(19) NOT NULL COMMENT 'MDM 库位稳定 ID 引用',
    location_code varchar(64) NOT NULL COMMENT 'MDM 库位编码快照',
    quality_status varchar(16) NOT NULL COMMENT '期初入账质量状态；不直接表示库存可用',
    warehouse_status varchar(16) NOT NULL COMMENT '期初入账仓储状态；不替代仓库启用状态',
    quantity decimal(24,6) NOT NULL COMMENT '以库存单位记录的正数期初数量',
    uom_code varchar(64) NOT NULL COMMENT '物料当前库存基础计量单位编码快照',
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_wms_opening_document_line_quality_status CHECK (((quality_status) IN ('PENDING', 'QUALIFIED', 'RESTRICTED', 'REJECTED'))),
    CONSTRAINT ck_wms_opening_document_line_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_wms_opening_document_line_warehouse_status CHECK (((warehouse_status) IN ('NORMAL', 'FROZEN'))),
    CONSTRAINT pk_wms_opening_document_line PRIMARY KEY (id),
    CONSTRAINT uk_wms_opening_document_line_document_id_line_no UNIQUE (document_id, line_no),
    KEY ix_wms_opening_line_document (document_id, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='期初数量及可信 MDM 展示快照';

CREATE TABLE wms_warehouse_inventory_state (
    id varchar(19) NOT NULL COMMENT 'WMS 仓库库存状态技术主键',
    warehouse_id varchar(19) NOT NULL COMMENT 'MDM 仓库稳定 ID 引用；不建立跨 Schema 外键',
    warehouse_code varchar(64) NOT NULL COMMENT '状态创建时的仓库编码快照',
    state varchar(32) NOT NULL COMMENT 'INITIALIZING 建账中，PENDING_ACTIVATION 已过账待核对启用，ACTIVE 已正式启用',
    opening_document_id varchar(19) COMMENT '将仓库置为待启用的期初建账单 ID',
    activated_by varchar(128) COMMENT '实际执行仓库库存启用的操作人',
    activated_at datetime(6) COMMENT '仓库库存正式启用时间',
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by varchar(128) COMMENT '最近一次仓库库存生命周期转换的实际责任主体；旧记录可能未知',
    CONSTRAINT ck_wms_warehouse_inventory_state_state CHECK (((state) IN ('INITIALIZING', 'PENDING_ACTIVATION', 'ACTIVE'))),
    CONSTRAINT pk_wms_warehouse_inventory_state PRIMARY KEY (id),
    CONSTRAINT uk_wms_warehouse_inventory_state_warehouse_id UNIQUE (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='WMS 仓库库存启用生命周期；不属于 MDM 静态仓库主数据';

CREATE TRIGGER wms_transaction_immutable_update BEFORE UPDATE ON wms_inventory_transaction
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory history is immutable';

CREATE TRIGGER wms_transaction_immutable_delete BEFORE DELETE ON wms_inventory_transaction
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory history is immutable';

CREATE TRIGGER wms_entry_immutable_update BEFORE UPDATE ON wms_inventory_transaction_entry
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory history is immutable';

CREATE TRIGGER wms_entry_immutable_delete BEFORE DELETE ON wms_inventory_transaction_entry
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory history is immutable';

CREATE TRIGGER wms_unfreeze_immutable_update BEFORE UPDATE ON wms_inventory_unfreeze
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory unfreeze history is immutable';

CREATE TRIGGER wms_unfreeze_immutable_delete BEFORE DELETE ON wms_inventory_unfreeze
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory unfreeze history is immutable';

CREATE TABLE wms_inventory_accounting_lock (
    lock_key varchar(160) NOT NULL COMMENT '仓库、库位、序列号或请求的稳定事务锁键',
    CONSTRAINT pk_wms_inventory_accounting_lock PRIMARY KEY (lock_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='库存记账事务级互斥锁行';

CREATE TRIGGER wms_freeze_fact_immutable_delete BEFORE DELETE ON wms_inventory_freeze
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory freeze fact is immutable';

DELIMITER $$
CREATE TRIGGER wms_freeze_fact_immutable_update BEFORE UPDATE ON wms_inventory_freeze
FOR EACH ROW
BEGIN
    IF NEW.remaining_quantity > OLD.remaining_quantity
       OR NOT (NEW.id <=> OLD.id)
       OR NOT (NEW.transaction_id <=> OLD.transaction_id)
       OR NOT (NEW.original_balance_id <=> OLD.original_balance_id)
       OR NOT (NEW.material_id <=> OLD.material_id)
       OR NOT (NEW.material_code <=> OLD.material_code)
       OR NOT (NEW.material_name <=> OLD.material_name)
       OR NOT (NEW.uom_code <=> OLD.uom_code)
       OR NOT (NEW.batch_code <=> OLD.batch_code)
       OR NOT (NEW.sn_code <=> OLD.sn_code)
       OR NOT (NEW.warehouse_id <=> OLD.warehouse_id)
       OR NOT (NEW.warehouse_code <=> OLD.warehouse_code)
       OR NOT (NEW.location_id <=> OLD.location_id)
       OR NOT (NEW.location_code <=> OLD.location_code)
       OR NOT (NEW.quality_status <=> OLD.quality_status)
       OR NOT (NEW.reason_code <=> OLD.reason_code)
       OR NOT (NEW.note <=> OLD.note)
       OR NOT (NEW.original_quantity <=> OLD.original_quantity)
       OR NOT (NEW.source_type <=> OLD.source_type)
       OR NOT (NEW.source_id <=> OLD.source_id)
       OR NOT (NEW.operator_id <=> OLD.operator_id)
       OR NOT (NEW.frozen_at <=> OLD.frozen_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory freeze fact is immutable';
    END IF;
END$$

CREATE TRIGGER wms_count_line_guard_insert BEFORE INSERT ON wms_inventory_count_line
FOR EACH ROW
BEGIN
    DECLARE task_status varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
    SET task_status = (SELECT status FROM wms_inventory_count_task WHERE id = NEW.task_id);
    IF NOT ((task_status = 'DRAFT' AND NEW.counted_quantity IS NULL)
         OR (task_status = 'COUNTING' AND NEW.book_quantity = 0 AND NEW.counted_quantity IS NOT NULL)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory count line insertion is not allowed in this task state';
    END IF;
END$$

CREATE TRIGGER wms_count_line_guard_update BEFORE UPDATE ON wms_inventory_count_line
FOR EACH ROW
BEGIN
    DECLARE task_status varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
    SET task_status = (SELECT status FROM wms_inventory_count_task WHERE id = NEW.task_id);
    IF task_status <> 'COUNTING' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory count line is immutable in this task state';
    END IF;
    IF NOT (NEW.id <=> OLD.id)
       OR NOT (NEW.task_id <=> OLD.task_id)
       OR NOT (NEW.material_id <=> OLD.material_id)
       OR NOT (NEW.material_code <=> OLD.material_code)
       OR NOT (NEW.material_name <=> OLD.material_name)
       OR NOT (NEW.uom_code <=> OLD.uom_code)
       OR NOT (NEW.batch_code <=> OLD.batch_code)
       OR NOT (NEW.sn_code <=> OLD.sn_code)
       OR NOT (NEW.warehouse_id <=> OLD.warehouse_id)
       OR NOT (NEW.warehouse_code <=> OLD.warehouse_code)
       OR NOT (NEW.location_id <=> OLD.location_id)
       OR NOT (NEW.location_code <=> OLD.location_code)
       OR NOT (NEW.quality_status <=> OLD.quality_status)
       OR NOT (NEW.warehouse_status <=> OLD.warehouse_status)
       OR NOT (NEW.book_quantity <=> OLD.book_quantity)
       OR NOT (NEW.snapshot_by <=> OLD.snapshot_by)
       OR NOT (NEW.snapshot_at <=> OLD.snapshot_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory count book snapshot is immutable';
    END IF;
END$$

CREATE TRIGGER wms_count_line_guard_delete BEFORE DELETE ON wms_inventory_count_line
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory count line is immutable in this task state'$$
DELIMITER ;
