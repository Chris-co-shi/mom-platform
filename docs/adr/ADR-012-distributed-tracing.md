# ADR-012：分布式链路追踪

- 状态：Accepted
- 日期：2026-07-18

采用 Micrometer Observation、Micrometer Tracing、OpenTelemetry、OTLP Collector 与 Grafana Tempo。

必须覆盖 HTTP、Gateway、Feign、RocketMQ、定时任务、Integration Hub 和 PCS/WCS 命令链路。

`trace_id` 只表示技术调用链；长业务流程额外使用 `correlation_id`、`workflow_id`、`event_id` 与 `command_id`。
