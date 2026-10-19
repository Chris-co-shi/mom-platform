-- Phase 1：余额是实时投影；交易及分录是不可变事实。Material、Warehouse、Location 仅引用 MDM 稳定 ID。
CREATE TABLE wms_inventory_balance (
    id varchar(19) NOT NULL,
    material_id varchar(19) NOT NULL,
    material_code varchar(64) NOT NULL,
    material_name varchar(200) NOT NULL,
    uom_code varchar(64) NOT NULL,
    batch_code varchar(128),
    sn_code varchar(128),
    warehouse_id varchar(19) NOT NULL,
    warehouse_code varchar(64) NOT NULL,
    location_id varchar(19) NOT NULL,
    location_code varchar(64) NOT NULL,
    quality_status varchar(16) NOT NULL,
    warehouse_status varchar(16) NOT NULL,
    quantity numeric(24,6) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_inventory_balance PRIMARY KEY (id),
    CONSTRAINT ck_wms_inventory_balance_nonnegative CHECK (quantity >= 0),
    CONSTRAINT ck_wms_inventory_balance_quality CHECK (quality_status IN ('PENDING','QUALIFIED','RESTRICTED','REJECTED')),
    CONSTRAINT ck_wms_inventory_balance_warehouse_status CHECK (warehouse_status IN ('NORMAL','FROZEN')),
    CONSTRAINT uk_wms_inventory_balance_dimension UNIQUE NULLS NOT DISTINCT
      (material_id, batch_code, sn_code, location_id, quality_status, warehouse_status)
);

CREATE TABLE wms_inventory_transaction (
    id varchar(19) NOT NULL,
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_inventory_transaction PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_transaction_request_key UNIQUE (request_key)
);

CREATE TABLE wms_inventory_transaction_entry (
    id varchar(19) NOT NULL,
    transaction_id varchar(19) NOT NULL,
    line_no integer NOT NULL,
    balance_id varchar(19) NOT NULL,
    delta_quantity numeric(24,6) NOT NULL,
    CONSTRAINT pk_wms_inventory_transaction_entry PRIMARY KEY (id),
    CONSTRAINT ck_wms_inventory_transaction_entry_nonzero CHECK (delta_quantity <> 0),
    CONSTRAINT uk_wms_inventory_transaction_entry_line UNIQUE (transaction_id, line_no)
);

-- 查询模式：实时页只看非零库存，按 material_id 与 warehouse_id 过滤；规模预计为 10^3～10^5 条余额维度。
CREATE INDEX ix_wms_balance_live_material_warehouse
    ON wms_inventory_balance (material_id, warehouse_id, id) WHERE quantity > 0;

CREATE FUNCTION wms_reject_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'inventory history is immutable';
END;
$$;
CREATE TRIGGER wms_transaction_immutable BEFORE UPDATE OR DELETE ON wms_inventory_transaction
    FOR EACH ROW EXECUTE FUNCTION wms_reject_history_change();
CREATE TRIGGER wms_entry_immutable BEFORE UPDATE OR DELETE ON wms_inventory_transaction_entry
    FOR EACH ROW EXECUTE FUNCTION wms_reject_history_change();

COMMENT ON TABLE wms_inventory_balance IS '按物料、批次、SN、库位、质量及仓储状态唯一确定的权威余额';
COMMENT ON COLUMN wms_inventory_balance.id IS '余额技术主键';
COMMENT ON COLUMN wms_inventory_balance.material_id IS 'MDM 物料稳定 ID 快照引用';
COMMENT ON COLUMN wms_inventory_balance.warehouse_id IS 'MDM 仓库稳定 ID 快照引用';
COMMENT ON COLUMN wms_inventory_balance.location_id IS 'MDM 库位稳定 ID 快照引用';
COMMENT ON TABLE wms_inventory_transaction IS '按业务请求键幂等提交的库存交易';
COMMENT ON COLUMN wms_inventory_transaction.id IS '库存交易技术主键';
COMMENT ON TABLE wms_inventory_transaction_entry IS '库存交易不可变的正负数量分录';
COMMENT ON COLUMN wms_inventory_transaction_entry.id IS '分录技术主键';
COMMENT ON COLUMN wms_inventory_transaction_entry.transaction_id IS '所属库存交易 ID';
COMMENT ON COLUMN wms_inventory_transaction_entry.balance_id IS '受影响余额 ID';
