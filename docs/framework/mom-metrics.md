# mom-metrics：指标名称与公共标签

**状态：Gateway 限流及 Outbox/Inbox 基础设施采用。** 模块提供 `MomMetricNames` 常量及 `MomMetricsAutoConfiguration`。源码入口为 [`mom-metrics`](../../mom-framework/mom-metrics/)；已用指标包括 Gateway 限流、Outbox 发布结果和 Inbox 处理结果。

- 自动配置仅在已有 `MeterRegistry` 等条件满足时提供公共标签定制；模块依赖 Prometheus Registry，但不自行创建或验证 Exporter、Collector 和抓取链路。
- 业务埋点应使用稳定低基数名称与标签，不把事件 ID、请求 ID、Token、Payload、业务主键作为标签。指标失败不得改变 Outbox/Inbox 的事务和消息重试语义。
- 具体命名、隐私、运行环境与监控验收见[可观测性架构](../architecture/可观测性架构.md)。
