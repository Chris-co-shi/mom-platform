CREATE TABLE wms_warehouse_inventory_state (
    id varchar(19) PRIMARY KEY,
    warehouse_id varchar(19) NOT NULL UNIQUE,
    warehouse_code varchar(64) NOT NULL,
    state varchar(32) NOT NULL CHECK (state IN ('INITIALIZING','PENDING_ACTIVATION','ACTIVE')),
    opening_document_id varchar(19),
    activated_by varchar(128),
    activated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE wms_opening_document (
    id varchar(19) PRIMARY KEY,
    document_no varchar(32) NOT NULL UNIQUE,
    warehouse_id varchar(19) NOT NULL,
    warehouse_code varchar(64) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('DRAFT','POSTED')),
    transaction_id varchar(19),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_wms_opening_document_transaction
    ON wms_opening_document(transaction_id) WHERE transaction_id IS NOT NULL;
CREATE UNIQUE INDEX uk_wms_opening_document_one_per_warehouse
    ON wms_opening_document(warehouse_id);

CREATE TABLE wms_opening_document_line (
    id varchar(19) PRIMARY KEY,
    document_id varchar(19) NOT NULL,
    line_no integer NOT NULL,
    material_id varchar(19) NOT NULL,
    material_code varchar(64) NOT NULL,
    material_name varchar(200) NOT NULL,
    batch_code varchar(128),
    sn_code varchar(128),
    warehouse_id varchar(19) NOT NULL,
    warehouse_code varchar(64) NOT NULL,
    location_id varchar(19) NOT NULL,
    location_code varchar(64) NOT NULL,
    quality_status varchar(16) NOT NULL CHECK (quality_status IN ('PENDING','QUALIFIED','RESTRICTED','REJECTED')),
    warehouse_status varchar(16) NOT NULL CHECK (warehouse_status IN ('NORMAL','FROZEN')),
    quantity numeric(24,6) NOT NULL CHECK (quantity > 0),
    uom_code varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(document_id, line_no)
);
CREATE INDEX ix_wms_opening_line_document ON wms_opening_document_line(document_id,line_no);
CREATE UNIQUE INDEX uk_wms_inventory_balance_sn ON wms_inventory_balance(sn_code) WHERE sn_code IS NOT NULL;

COMMENT ON TABLE wms_warehouse_inventory_state IS 'WMS 仓库库存启用生命周期；不属于 MDM 静态仓库主数据';
COMMENT ON COLUMN wms_warehouse_inventory_state.id IS 'WMS 仓库库存状态技术主键';
COMMENT ON COLUMN wms_warehouse_inventory_state.warehouse_id IS 'MDM 仓库稳定 ID 引用；不建立跨 Schema 外键';
COMMENT ON COLUMN wms_warehouse_inventory_state.warehouse_code IS '状态创建时的仓库编码快照';
COMMENT ON COLUMN wms_warehouse_inventory_state.state IS 'INITIALIZING 建账中，PENDING_ACTIVATION 已过账待核对启用，ACTIVE 已正式启用';
COMMENT ON COLUMN wms_warehouse_inventory_state.opening_document_id IS '将仓库置为待启用的期初建账单 ID';
COMMENT ON COLUMN wms_warehouse_inventory_state.activated_by IS '实际执行仓库库存启用的操作人';
COMMENT ON COLUMN wms_warehouse_inventory_state.activated_at IS '仓库库存正式启用时间';
COMMENT ON TABLE wms_opening_document IS '期初库存建账单；过账后不可变并关联唯一库存交易';
COMMENT ON COLUMN wms_opening_document.id IS '期初单技术主键';
COMMENT ON COLUMN wms_opening_document.document_no IS '期初单业务编号';
COMMENT ON COLUMN wms_opening_document.warehouse_id IS 'MDM 仓库稳定 ID 引用';
COMMENT ON COLUMN wms_opening_document.warehouse_code IS '建账时仓库编码快照';
COMMENT ON COLUMN wms_opening_document.status IS 'DRAFT 可维护，POSTED 已过账且不可修改或删除';
COMMENT ON COLUMN wms_opening_document.transaction_id IS '期初过账产生的不可变库存交易 ID';
COMMENT ON COLUMN wms_opening_document.created_by IS '创建期初草稿的实际操作人';
COMMENT ON COLUMN wms_opening_document.version IS '明细变更版本，用于事务外引用核验后的提交冲突检查';
COMMENT ON TABLE wms_opening_document_line IS '期初数量及可信 MDM 展示快照';
COMMENT ON COLUMN wms_opening_document_line.id IS '期初明细技术主键';
COMMENT ON COLUMN wms_opening_document_line.document_id IS '所属期初单技术 ID';
COMMENT ON COLUMN wms_opening_document_line.material_id IS 'MDM 物料稳定 ID 引用';
COMMENT ON COLUMN wms_opening_document_line.material_code IS 'MDM 物料编码快照';
COMMENT ON COLUMN wms_opening_document_line.material_name IS 'MDM 物料名称快照';
COMMENT ON COLUMN wms_opening_document_line.batch_code IS '沿用真实既有批次身份；策略要求批次时不得生成虚假收货批次';
COMMENT ON COLUMN wms_opening_document_line.sn_code IS '可空的唯一标签码；有值时由 PostgreSQL 全局唯一索引约束';
COMMENT ON COLUMN wms_opening_document_line.warehouse_id IS 'MDM 仓库稳定 ID 引用';
COMMENT ON COLUMN wms_opening_document_line.warehouse_code IS '建账时仓库编码快照';
COMMENT ON COLUMN wms_opening_document_line.location_id IS 'MDM 库位稳定 ID 引用';
COMMENT ON COLUMN wms_opening_document_line.location_code IS 'MDM 库位编码快照';
COMMENT ON COLUMN wms_opening_document_line.quality_status IS '期初入账质量状态；不直接表示库存可用';
COMMENT ON COLUMN wms_opening_document_line.warehouse_status IS '期初入账仓储状态；不替代仓库启用状态';
COMMENT ON COLUMN wms_opening_document_line.quantity IS '以库存单位记录的正数期初数量';
COMMENT ON COLUMN wms_opening_document_line.uom_code IS '物料当前库存基础计量单位编码快照';
