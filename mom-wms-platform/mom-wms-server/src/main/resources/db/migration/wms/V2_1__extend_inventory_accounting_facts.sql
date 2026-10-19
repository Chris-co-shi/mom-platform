-- V2 已被 WMS i18n 占用；2.1 在其后为 Phase 1.1 记账事实演进。
-- Phase 1 交易没有来源、责任主体或分录维度快照，旧记录保持 NULL，不从当前余额伪造历史。
ALTER TABLE wms_inventory_transaction
    ADD COLUMN transaction_type varchar(64),
    ADD COLUMN source_type varchar(64),
    ADD COLUMN source_id varchar(128),
    ADD COLUMN operator_id varchar(128);

ALTER TABLE wms_inventory_transaction
    ADD CONSTRAINT ck_wms_inventory_transaction_facts_complete
    CHECK (
        (transaction_type IS NULL AND source_type IS NULL AND source_id IS NULL AND operator_id IS NULL)
        OR
        (transaction_type IS NOT NULL AND btrim(transaction_type) <> ''
         AND source_type IS NOT NULL AND btrim(source_type) <> ''
         AND source_id IS NOT NULL AND btrim(source_id) <> ''
         AND operator_id IS NOT NULL AND btrim(operator_id) <> '')
    );

ALTER TABLE wms_inventory_transaction_entry
    ADD COLUMN material_id varchar(19),
    ADD COLUMN material_code varchar(64),
    ADD COLUMN material_name varchar(200),
    ADD COLUMN uom_code varchar(64),
    ADD COLUMN batch_code varchar(128),
    ADD COLUMN sn_code varchar(128),
    ADD COLUMN warehouse_id varchar(19),
    ADD COLUMN warehouse_code varchar(64),
    ADD COLUMN location_id varchar(19),
    ADD COLUMN location_code varchar(64),
    ADD COLUMN quality_status varchar(16),
    ADD COLUMN warehouse_status varchar(16),
    ADD COLUMN before_quantity numeric(24,6),
    ADD COLUMN after_quantity numeric(24,6);

ALTER TABLE wms_inventory_transaction_entry
    ADD CONSTRAINT ck_wms_inventory_transaction_entry_snapshot_complete
    CHECK (
        (material_id IS NULL AND material_code IS NULL AND material_name IS NULL AND uom_code IS NULL
         AND batch_code IS NULL AND sn_code IS NULL AND warehouse_id IS NULL AND warehouse_code IS NULL
         AND location_id IS NULL AND location_code IS NULL AND quality_status IS NULL
         AND warehouse_status IS NULL AND before_quantity IS NULL AND after_quantity IS NULL)
        OR
        (material_id IS NOT NULL AND material_code IS NOT NULL AND material_name IS NOT NULL
         AND uom_code IS NOT NULL AND warehouse_id IS NOT NULL AND warehouse_code IS NOT NULL
         AND location_id IS NOT NULL AND location_code IS NOT NULL AND quality_status IS NOT NULL
         AND warehouse_status IS NOT NULL AND before_quantity IS NOT NULL AND after_quantity IS NOT NULL
         AND before_quantity >= 0 AND after_quantity >= 0
         AND before_quantity + delta_quantity = after_quantity)
    ),
    ADD CONSTRAINT ck_wms_inventory_transaction_entry_snapshot_quality
        CHECK (quality_status IS NULL OR quality_status IN ('PENDING','QUALIFIED','RESTRICTED','REJECTED')),
    ADD CONSTRAINT ck_wms_inventory_transaction_entry_snapshot_warehouse_status
        CHECK (warehouse_status IS NULL OR warehouse_status IN ('NORMAL','FROZEN'));

COMMENT ON COLUMN wms_inventory_transaction.transaction_type IS '交易业务类型；Phase 1 历史交易缺失时为空';
COMMENT ON COLUMN wms_inventory_transaction.source_type IS '来源业务类型；Phase 1 历史交易缺失时为空';
COMMENT ON COLUMN wms_inventory_transaction.source_id IS '来源业务对象标识；Phase 1 历史交易缺失时为空';
COMMENT ON COLUMN wms_inventory_transaction.operator_id IS '实际操作责任主体标识；Phase 1 历史交易缺失时为空';
COMMENT ON COLUMN wms_inventory_transaction_entry.balance_id IS '记账时受影响余额行标识，仅作技术关联';
COMMENT ON COLUMN wms_inventory_transaction_entry.material_id IS '记账发生时物料稳定标识快照';
COMMENT ON COLUMN wms_inventory_transaction_entry.batch_code IS '记账发生时批次快照，非批次库存为空';
COMMENT ON COLUMN wms_inventory_transaction_entry.sn_code IS '记账发生时序列号快照，非 SN 管理库存为空';
COMMENT ON COLUMN wms_inventory_transaction_entry.warehouse_id IS '记账发生时仓库稳定标识快照';
COMMENT ON COLUMN wms_inventory_transaction_entry.location_id IS '记账发生时库位稳定标识快照';
COMMENT ON COLUMN wms_inventory_transaction_entry.quality_status IS '记账发生时质量状态快照';
COMMENT ON COLUMN wms_inventory_transaction_entry.warehouse_status IS '记账发生时仓储状态快照';
COMMENT ON COLUMN wms_inventory_transaction_entry.before_quantity IS '本分录作用前该库存维度数量';
COMMENT ON COLUMN wms_inventory_transaction_entry.after_quantity IS '本分录作用后该库存维度数量';
