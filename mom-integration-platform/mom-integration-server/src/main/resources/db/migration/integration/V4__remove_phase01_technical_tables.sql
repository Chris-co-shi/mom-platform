-- P1.5 清理：Phase 01 技术验收表退出正式 Integration Schema。
-- mom_inbox_event 与 undo_log 属于正式基础设施能力，必须保留。
DROP TABLE IF EXISTS technical_seata_at_participant;
DROP TABLE IF EXISTS technical_message_receipt;

