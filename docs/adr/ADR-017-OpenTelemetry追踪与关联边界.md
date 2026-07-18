# ADR-017：OpenTelemetry 追踪与关联边界

- 状态：Accepted
- 日期：2026-07-19
- 关联文档：[可观测性架构](../architecture/可观测性架构.md)

## 1. 背景

MOM 的同步请求、Outbox、RocketMQ、设备命令和长制造流程跨越多个服务与基础设施。只记录独立日志无法可靠判断请求经过哪些服务、耗时集中在哪里，也无法把同步调用和消息消费的技术故障联系起来。

当前固定技术组合为 JDK 25、Spring Boot 4.1.0、Spring Cloud 2025.1.2 和 Spring Cloud Alibaba 2025.1.0.0，需要验证 Micrometer、OpenTelemetry、OTLP Collector 与 Tempo 的真实兼容性。

## 2. 候选方案

### 2.1 OpenTelemetry Java Agent 全自动注入

接入速度快，但运行参数、版本和自动埋点范围脱离应用依赖治理，也更难在 Framework 中固定标签、采样和失败边界。

### 2.2 业务代码直接使用 OpenTelemetry SDK

控制力强，但会让领域代码绑定具体追踪实现，增加迁移和测试成本。

### 2.3 Spring Boot OpenTelemetry Starter + Micrometer 门面

Boot 管理 SDK、Exporter 和自动配置；业务与 Framework 使用 Micrometer Observation/Tracing；OTLP 发送给 Collector，再由 Collector 写入 Tempo。

## 3. 决策

采用方案 2.3。

### 3.1 依赖与自动埋点

- 使用 Spring Boot 4.1 管理的 `spring-boot-starter-opentelemetry`；
- Framework 和业务代码优先使用 Micrometer Observation/Tracing，不直接依赖 OpenTelemetry SDK；
- Gateway、Servlet HTTP、OpenFeign 和 Spring Cloud Stream 优先使用官方自动 Observation；
- OpenFeign 必须包含 `feign-micrometer`；
- Spring Cloud Stream Imperative Function 必须包含 Actuator、Reactor Micrometer 与 Tracer Bridge；
- 自定义 Span 只用于官方自动埋点不能表达的平台操作，例如 Outbox 发布尝试。

### 3.2 传播协议

- 服务间统一使用 W3C Trace Context；
- 不手工复制 `traceparent`、`tracestate`，由框架 Propagator 负责注入与提取；
- 接收到合法上游 Trace Context 时继续同一 Trace；
- 没有上游上下文时创建新 Trace；
- 不信任客户端提供的 Trace ID 作为权限、审计主体、业务身份或幂等依据。

### 3.3 Trace 生命周期

- 同步 Gateway → 服务 → OpenFeign 调用保持一个短 Trace；
- 完整制造流程不得维持小时级或天级超长 Trace；
- Outbox 每次发布尝试创建短 `mom.outbox.publish` Observation；
- 消费者从消息上下文恢复发布 Trace，并为每次投递创建消费 Span；
- 不同业务阶段、重试或人工接管可以创建新 Trace，通过 `correlation_id`、`workflow_id`、`event_id`、`command_id` 和业务单号关联；
- Trace ID 不写入领域唯一约束，也不替代 Outbox/Inbox 幂等。

### 3.4 采样与属性

- 本地和生产默认采样概率为 0.1；兼容性 CI 使用 1.0；
- 采样由平台配置管理，业务代码不得自行随机采样；
- 服务、路由、HTTP 方法、状态、事件类型和结果可以作为低基数属性；
- 用户 ID、业务单号、事件 ID、命令 ID、Trace ID 和完整 URL 参数不得作为 Prometheus Label；
- `event_id`、`correlation_id` 等只允许作为受控高基数 Span 属性或结构化日志字段；
- Payload、Token、Cookie、密码、密钥和未脱敏敏感数据不得进入 Span 属性或事件。

### 3.5 导出与失败策略

- OTLP 导出默认关闭，部署环境显式开启；
- 应用通过 OTLP/HTTP 把 Trace 发送给 OpenTelemetry Collector；
- 应用不直接绑定 Tempo 地址；Collector 负责后端路由、批处理和未来扩展；
- Collector 或 Tempo 不可用时，业务请求、数据库事务和消息处理继续执行；
- Exporter 异常只能影响遥测完整性，不得改变业务 HTTP 状态、Outbox 状态或消息确认语义；
- 遥测丢失必须通过 Collector/Exporter 指标和告警暴露，不能静默当作正常。

### 3.6 日志关联

- 日志 MDC 使用 Micrometer 提供的 `traceId`、`spanId`；
- 输出字段统一命名为 `trace_id`、`span_id`；
- 没有活动 Span 时字段为空，不伪造标识；
- 日志关联不等于日志 OTLP 导出。Loki 收集、保留和脱敏策略由后续可观测性切片完成。

## 4. 验证方式

独立 Observability CI 必须验证：

1. 带固定 W3C `traceparent` 的请求经过 Gateway、Integration 和 MDM；
2. 三个服务属于同一 Trace，Integration 与 MDM Server Span 不同；
3. OpenFeign 真实传播 Trace Context；
4. 应用日志包含相同 `trace_id` 和当前 `span_id`；
5. Collector 收到 OTLP，并能从 Tempo 查询完整 Trace；
6. Collector 停止后业务请求仍成功；
7. Outbox 发布和 RocketMQ Consumer 存在活动 Span；
8. 重复消息仍由 Inbox 保证业务只执行一次。

## 5. 后果

### 正向

- 应用依赖、配置和测试可由 Maven 与 CI 统一治理；
- 领域代码不绑定具体 Trace 后端；
- 同步和异步故障可以从 Trace、日志和业务关联标识联合排查；
- Collector 后端可从 Tempo 扩展到其他兼容系统。

### 负向

- 追踪会增加少量 CPU、内存、网络和存储成本；
- 采样意味着不能保证每个请求都有 Trace；
- 异步重试可能出现多个消费 Span，需要结合 Inbox 和事件 ID 判断唯一业务结果；
- Collector/Tempo 的容量、保留和高可用仍需 `mom-infra` 设计。

## 6. 替代与退出条件

出现以下情况时必须重新评审：

- Boot 或 Cloud 升级后自动 Observation 无法通过真实 CI；
- 采样、属性或日志包含敏感数据；
- 高基数标签导致指标或存储成本失控；
- Exporter 故障开始影响业务线程或消息确认；
- Java Agent 成为统一运维标准并能满足同等依赖、版本和安全治理要求。
