-- P1.5 清理：Phase 01 技术验收表退出正式 MDM Schema。
-- 已发布的历史迁移保持不变；真实基础设施 Smoke 在受控测试环境中单独创建所需技术表。
DROP TABLE IF EXISTS technical_seata_at_coordinator;
DROP TABLE IF EXISTS technical_data_probe;

