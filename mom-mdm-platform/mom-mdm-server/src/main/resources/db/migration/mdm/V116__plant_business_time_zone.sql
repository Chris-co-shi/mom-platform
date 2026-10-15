-- 存量工厂保留待补状态；新建请求由 Application 强制选择权威 IANA 时区。
ALTER TABLE mdm_plant ADD COLUMN time_zone_id varchar(64);
COMMENT ON COLUMN mdm_plant.time_zone_id IS '工厂业务日期使用的权威 IANA Zone ID；存量 NULL 表示待治理，不可供新业务引用';
