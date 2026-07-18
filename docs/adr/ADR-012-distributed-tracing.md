# ADR-012：分布式链路追踪

- 状态：Accepted
- 日期：2026-07-18
- 关联需求：NFR-OBS-001～NFR-OBS-005
- 关联文档：[可观测性架构](../architecture/可观测性架构.md)

## 1. 背景

MOM 的业务链路会跨越 Gateway、Feign、RocketMQ、Integration Hub、PCS、WCS 和定时补偿任务。仅依赖日志无法快速定位请求、消息和设备回执之间的因果关系，也难以在面试演示中说明故障恢复过程。

## 2. 候选方案

- 只使用日志和业务单号：成本低，但跨服务关联和耗时分析困难。
- 直接绑定某个 APM Agent：接入快，但业务自定义观测和后端迁移能力受限。
- Micrometer Observation + OpenTelemetry + Tempo：兼顾 Spring 生态、开放标准和自托管能力。

## 3. 决策

采用：

- Spring Boot Actuator。
- Micrometer Observation。
- Micrometer Tracing。
- OpenTelemetry 与 OTLP。
- OpenTelemetry Collector。
- Grafana Tempo。
- Loki、Prometheus 和 Grafana 进行日志、指标和 Trace 关联。

业务代码优先使用 Micrometer Observation API，不直接依赖具体 Trace 后端 SDK。

## 4. 覆盖范围

必须覆盖：

- HTTP、Gateway 和 Feign。
- RocketMQ 生产、消费、重试和死信。
- Outbox 发布任务和 Inbox 消费。
- 定时任务与补偿任务。
- Integration Hub 入站和出站调用。
- PCS/WCS 命令、状态和回执。

## 5. 长流程处理

`trace_id` 只表示一次技术调用链。持续数小时或数天的制造流程不得保持一个超长 Trace，而应：

- 每个阶段创建新的 Trace。
- 使用 `correlation_id`、`workflow_id`、`event_id` 和 `command_id` 关联。
- 适当使用 Span Link 表达异步因果关系。

## 6. 数据控制

- Span 属性使用低基数分类字段。
- 业务单号可以进入日志或受控 Span 属性，但不得成为 Prometheus Label。
- Token、密码、密钥和大型 Payload 不得进入 Trace。
- 采样率按环境配置，错误和关键业务链路可提高采样。

## 7. 后果

正向：能够进行跨服务根因分析、性能定位、日志关联和故障演示；后端组件可替换。

负向：增加 Collector、Tempo、存储、采样和上下文传播治理成本。

## 8. 验证方式

- 查看 Gateway → MES → WMS 同步 Trace。
- 查看 MES → RocketMQ → PCS → MES 异步 Trace。
- 从错误日志跳转到 Trace，再从 Trace 关联日志。
- 重复消息显示多次尝试但只有一次业务成功。
- PCS 超时、重试和恢复产生完整观测记录。

## 9. 替代条件

未来可以替换 Tempo 或增加托管 APM，但 OpenTelemetry 协议、Micrometer 观测接口和业务关联标识保持稳定。