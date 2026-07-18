-- P01-S07 为技术消息消费结果增加当前 Consumer Span 标识。
-- 字段只用于真实 RocketMQ / OpenTelemetry 兼容性验收，不作为业务关联或幂等条件。

ALTER TABLE technical_message_receipt
    ADD COLUMN trace_id VARCHAR(32),
    ADD COLUMN span_id VARCHAR(16);

COMMENT ON COLUMN technical_message_receipt.trace_id IS 'Spring Cloud Stream 消费函数当前 Trace ID，仅用于追踪验收';
COMMENT ON COLUMN technical_message_receipt.span_id IS 'Spring Cloud Stream 消费函数当前 Span ID，仅用于追踪验收';
