-- 旧余额及旧业务动作无法可靠还原责任主体和首次创建时间，保持 NULL。
ALTER TABLE wms_inventory_balance ADD COLUMN created_at timestamptz;
ALTER TABLE wms_inventory_balance ALTER COLUMN created_at SET DEFAULT now();
COMMENT ON COLUMN wms_inventory_balance.created_at IS '该完整库存维度的余额行首次建立时间；迁移前旧行未知';

ALTER TABLE wms_opening_document ADD COLUMN updated_by varchar(128);
ALTER TABLE wms_opening_document ADD COLUMN posted_by varchar(128);
ALTER TABLE wms_opening_document ADD COLUMN posted_at timestamptz;
COMMENT ON COLUMN wms_opening_document.updated_by IS '最后修改草稿明细的实际操作人；旧记录可能未知';
COMMENT ON COLUMN wms_opening_document.posted_by IS '执行期初过账的实际操作人；旧记录可能未知';
COMMENT ON COLUMN wms_opening_document.posted_at IS '期初过账成功的数据库时间；旧记录可能未知';

ALTER TABLE wms_warehouse_inventory_state ADD COLUMN updated_by varchar(128);
COMMENT ON COLUMN wms_warehouse_inventory_state.updated_by IS '最近一次仓库库存生命周期转换的实际责任主体；旧记录可能未知';
