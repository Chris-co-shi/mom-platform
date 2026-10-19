-- 查询模式：按最长 366 天的交易时间范围稳定倒序分页，并可按物料、批次或 SN 精确过滤。
-- 预计历史分录为 10^5～10^7 行；交易 ID 与分录 transaction_id/line_no 已有主键或唯一索引。
-- 来源类型与标识用于期初单据精确追溯；仓库、库位等条件先在有界时间结果中筛选。
CREATE INDEX ix_wms_history_transaction_time ON wms_inventory_transaction (occurred_at DESC, id DESC);
CREATE INDEX ix_wms_history_transaction_source ON wms_inventory_transaction (source_type, source_id, id)
    WHERE source_type IS NOT NULL;
CREATE INDEX ix_wms_history_entry_material ON wms_inventory_transaction_entry (material_id, transaction_id);
CREATE INDEX ix_wms_history_entry_batch ON wms_inventory_transaction_entry (batch_code, transaction_id)
    WHERE batch_code IS NOT NULL;
CREATE INDEX ix_wms_history_entry_sn ON wms_inventory_transaction_entry (sn_code, transaction_id)
    WHERE sn_code IS NOT NULL;
