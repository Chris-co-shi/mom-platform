-- SN 库位移动保留数量归零的来源余额历史行；零余额不得阻止同状态目标库位入账。
-- 正余额预计 10^3～10^5 行；按 SN 查询当前正余额，仍限制每个 SN/仓储状态最多一条正余额。
DROP INDEX uk_wms_inventory_balance_sn;
CREATE UNIQUE INDEX uk_wms_inventory_balance_sn
    ON wms_inventory_balance (sn_code, warehouse_status)
    WHERE sn_code IS NOT NULL AND quantity > 0;
