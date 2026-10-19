-- WMS Phase 3：可独立追踪的仓储冻结限制与解冻事实。
-- SN 在同一仓储状态仍唯一；允许冻结过账把同一 SN 分别投影到 NORMAL 与 FROZEN 余额。
DROP INDEX uk_wms_inventory_balance_sn;
CREATE UNIQUE INDEX uk_wms_inventory_balance_sn
    ON wms_inventory_balance (sn_code, warehouse_status) WHERE sn_code IS NOT NULL;

CREATE TABLE wms_inventory_freeze (
    id varchar(19) NOT NULL,
    transaction_id varchar(19) NOT NULL,
    original_balance_id varchar(19) NOT NULL,
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
    reason_code varchar(32) NOT NULL,
    note varchar(500),
    original_quantity numeric(24,6) NOT NULL,
    remaining_quantity numeric(24,6) NOT NULL,
    source_type varchar(64) NOT NULL,
    source_id varchar(128) NOT NULL,
    operator_id varchar(128) NOT NULL,
    frozen_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_inventory_freeze PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_freeze_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_wms_inventory_freeze_reason CHECK (reason_code IN
      ('PACKAGE_DAMAGE','STOCK_DISCREPANCY','WAREHOUSE_CHECK','SAFETY_HOLD','OTHER')),
    CONSTRAINT ck_wms_inventory_freeze_quality CHECK (quality_status IN
      ('PENDING','QUALIFIED','RESTRICTED','REJECTED')),
    CONSTRAINT ck_wms_inventory_freeze_quantities CHECK
      (original_quantity > 0 AND remaining_quantity >= 0 AND remaining_quantity <= original_quantity),
    CONSTRAINT ck_wms_inventory_freeze_other_note CHECK
      (reason_code <> 'OTHER' OR (note IS NOT NULL AND length(btrim(note)) > 0))
);

CREATE TABLE wms_inventory_unfreeze (
    id varchar(19) NOT NULL,
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    freeze_id varchar(19) NOT NULL,
    transaction_id varchar(19) NOT NULL,
    quantity numeric(24,6) NOT NULL,
    note varchar(500),
    source_type varchar(64) NOT NULL,
    source_id varchar(128) NOT NULL,
    operator_id varchar(128) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_inventory_unfreeze PRIMARY KEY (id),
    CONSTRAINT uk_wms_inventory_unfreeze_request UNIQUE (request_key),
    CONSTRAINT uk_wms_inventory_unfreeze_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_wms_inventory_unfreeze_quantity CHECK (quantity > 0),
    CONSTRAINT ck_wms_inventory_unfreeze_note CHECK (note IS NOT NULL AND length(btrim(note)) > 0)
);

-- 查询模式：按明细库存维度读取有效限制，预计每个库存维度的历史冻结记录较少。
CREATE INDEX ix_wms_inventory_freeze_active_dimension
    ON wms_inventory_freeze (material_id, warehouse_id, location_id, batch_code, sn_code, quality_status, id)
    WHERE remaining_quantity > 0;

COMMENT ON TABLE wms_inventory_freeze IS '独立追踪每次仓储冻结来源、原因、库存快照及剩余受限数量';
COMMENT ON COLUMN wms_inventory_freeze.reason_code IS 'WMS 受控冻结原因编码，不表示 QMS 质量结论';
COMMENT ON COLUMN wms_inventory_freeze.remaining_quantity IS '该冻结限制尚未解除的数量，由行锁与条件更新保护';
COMMENT ON TABLE wms_inventory_unfreeze IS '每次解冻的不可变处理历史；重复请求键返回原交易';
COMMENT ON COLUMN wms_inventory_unfreeze.note IS '解冻处理说明快照';

CREATE FUNCTION wms_reject_unfreeze_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'inventory unfreeze history is immutable';
END;
$$;
CREATE TRIGGER wms_unfreeze_immutable BEFORE UPDATE OR DELETE ON wms_inventory_unfreeze
    FOR EACH ROW EXECUTE FUNCTION wms_reject_unfreeze_history_change();

CREATE FUNCTION wms_reject_freeze_fact_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' OR
       (to_jsonb(NEW) - 'remaining_quantity') IS DISTINCT FROM (to_jsonb(OLD) - 'remaining_quantity') OR
       NEW.remaining_quantity > OLD.remaining_quantity THEN
        RAISE EXCEPTION 'inventory freeze fact is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER wms_freeze_fact_immutable BEFORE UPDATE OR DELETE ON wms_inventory_freeze
    FOR EACH ROW EXECUTE FUNCTION wms_reject_freeze_fact_change();
