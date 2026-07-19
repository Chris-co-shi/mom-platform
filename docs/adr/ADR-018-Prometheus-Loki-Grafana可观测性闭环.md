# ADR-018：Prometheus、Loki 与 Grafana 可观测性闭环

- 状态：Accepted
- 日期：2026-07-19
- 关联：ADR-012、ADR-017、可观测性架构、Phase 01 技术骨架计划

## 1. 背景

P01-S07 已完成 Micrometer Tracing、OpenTelemetry Collector 与 Tempo 的真实 Trace 闭环，但 Phase 01 的完成定义还要求 Grafana 同时查看 Trace、日志和指标。平台还需要统一指标标签、日志字段、告警规则和 Dashboard 的版本化治理，避免各服务自行拼装监控栈。

制造系统中的业务单号、用户、事件、命令与 Trace 数量都可能持续增长。如果把这些值直接作为 Prometheus 或 Loki 标签，会产生不可控的时间序列和日志流基数，最终拖垮查询、存储和告警。

Promtail 已进入长期支持结束阶段并于 2026-03-02 结束生命周期，因此新基线不能继续引入 Promtail。

## 2. 决策

采用以下三类数据面与统一展示层：

```text
Spring Boot Actuator / Micrometer → Prometheus
Spring Boot 结构化 JSON 日志 → Grafana Alloy → Loki
OpenTelemetry OTLP → OpenTelemetry Collector → Tempo
Prometheus + Loki + Tempo → Grafana Provisioning
```

应用只负责暴露指标、输出标准日志和发送 Trace。Prometheus、Alloy、Loki、Collector、Tempo 与 Grafana 属于部署基础设施，不进入领域代码或本地事务。

## 3. 指标治理

所有平台指标自动增加 `application` 和 `environment` 两个公共低基数标签。允许的标签值必须来自受控枚举或稳定配置，例如服务、路由、事件类型以及有限结果状态。

禁止把用户、IP 原文、Trace ID、Span ID、Correlation ID、事件 ID、命令 ID、聚合 ID、业务单号、URL 参数和完整异常消息作为 Prometheus Label。

当前平台自定义指标包括：

- `mom.gateway.rate.limit.requests`；
- `mom.outbox.publish.results`；
- `mom.inbox.process.results`。

指标记录失败不得改变限流结果、数据库事务、Outbox 状态机、Inbox 幂等或消息确认语义。

## 4. 日志治理

应用使用 Spring Boot 原生结构化 JSON 日志。日志保留服务、环境、级别、消息以及存在时的 Trace、Span 和业务关联标识。

Alloy 只把 `service_name`、`environment` 和受控 `level` 作为 Loki 标签。Trace、Span、Correlation、Event 与 Command 标识进入 JSON 内容或 Structured Metadata，不创建日志流标签。

日志中继续禁止 Token、Cookie、密码、密钥、数据库凭证、完整大型 Payload 和未脱敏敏感业务数据。

## 5. Grafana Provisioning

Grafana 数据源、Dashboard Provider 和 Dashboard JSON 必须保存在仓库并通过 provisioning 加载。生产验收不能依赖人工进入 Grafana UI 创建或修改配置。

固定数据源 UID：`mom-prometheus`、`mom-loki`、`mom-tempo`。Loki 的 Trace 派生字段指向 Tempo；Tempo 的 Trace-to-Logs 查询指向 Loki；服务图指标指向 Prometheus。Dashboard 使用固定 UID，不使用部署时随机生成的数据源 ID。

## 6. 告警基线

Phase 01 至少版本化以下规则：服务不可抓取、HTTP 5xx 比例升高、HTTP P95 延迟过高、Hikari 连接池接近耗尽、Gateway 限流基础设施不可用、Outbox DEAD、Inbox 消费持续失败。

告警规则只使用低基数聚合标签。业务单据级诊断通过日志和 Trace 完成，不为单个单号创建告警时间序列。

## 7. 故障策略

Prometheus 使用 Pull 模型。Prometheus、Loki、Alloy、Grafana、Tempo 或 Collector 不可用时，HTTP、数据库事务和消息处理继续执行，不修改业务响应，不改变消息状态，也不启用无界内存缓冲。

可观测性基础设施故障是诊断能力降级，不是业务一致性故障。安全审计等法规要求的数据仍必须写入权威业务存储，不能只依赖 Loki。

## 8. 方案比较

### 方案 A：Prometheus + Alloy + Loki + Tempo + Grafana

与现有 Micrometer/OpenTelemetry 基线一致，三类数据可关联，配置可版本化。采用。

### 方案 B：继续使用 Promtail

历史资料多，但已经结束生命周期。拒绝。

### 方案 C：应用直接向 Loki Push

会把日志后端网络故障引入应用线程，并产生 SDK、重试和缓冲耦合。拒绝。应用只写标准输出或受控文件，由 Alloy 采集。

## 9. 验证

P01-S08 使用真实容器与打包应用验证 Prometheus 抓取、HTTP/JVM/Hikari/自定义指标、告警加载、Alloy→Loki、固定 Trace 日志与 Tempo 查询、标签基数约束、Grafana 三数据源与 Dashboard provisioning，以及 Prometheus/Loki 停止后业务仍成功。JDK 25 全量测试和 RocketMQ、Seata、既有 Trace CI 必须无回归。
