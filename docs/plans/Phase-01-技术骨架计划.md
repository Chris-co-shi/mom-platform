# Phase 01：技术骨架计划

## 1. 阶段状态

- 状态：**Completed**
- 完成日期：2026-07-19
- 完成报告：[Phase 01 完成报告](Phase-01-完成报告.md)

## 2. 阶段目标

建立可编译、可启动、可测试、可观测的 MOM Platform 基础工程，并完成 JDK 25 + Spring Boot 4 体系与关键中间件的兼容性验证。

本阶段不实现业务 CRUD。

## 3. 范围

### 3.1 已完成

- `mom-dependencies` 统一版本管理。
- `mom-framework` 基础模块接口和自动配置边界。
- `mom-gateway` 最小路由、认证上下文、Redis 限流。
- IAM 最小 OAuth2.1/OIDC 服务。
- MDM 和 Integration 最小健康检查服务。
- PostgreSQL、Redis、Nacos、RocketMQ、Seata 兼容性验证。
- Micrometer、OpenTelemetry、OTLP Collector 和 Tempo。
- Prometheus、Loki、Grafana 与 Alloy 基础接入。
- Testcontainers 和 ArchUnit 基础测试。

### 3.2 明确不做

- MES、WMS、QMS 业务表和 CRUD。
- 完整用户、角色和菜单管理页面。
- 完整数据权限 SQL 重写实现。
- 完整 PCS/WCS 模拟器。
- 生产级高可用中间件集群。
- Alertmanager 通知路由、长期指标/日志/Trace 保留与对象存储。

## 4. 已完成切片

### Slice 01：构建与依赖基线

完成：

- 锁定 JDK 25、Maven 3.9.9+。
- 锁定 Boot、Cloud、Cloud Alibaba 版本。
- 配置 Maven Enforcer、Compiler、Surefire 和 Failsafe。
- 禁止 Snapshot 和重复依赖版本。
- 增加依赖树和冲突检查。

验收：

```bash
mvn -B -ntp clean verify
```

### Slice 02：Framework 最小能力

完成：

- 定义统一错误模型和异常边界。
- 定义请求上下文、用户上下文和工厂上下文。
- 定义事件信封、幂等键和关联标识。
- 定义日志、指标、Trace 和测试模块边界。
- 使用 ArchUnit 验证模块依赖方向。

验收：

- Framework 不包含 MES、WMS、QMS 业务类型。
- `*-api` 不引用 Entity、Mapper 和 Repository。
- `*-server` 不直接依赖其他领域的 `*-server`。

### Slice 03：Gateway 与安全闭环

完成：

- Gateway 接入 Nacos 注册发现。
- 接入 OAuth2 Resource Server。
- 校验 JWT、Scope 和 Audience。
- 传递用户、Client、工厂和关联上下文。
- 实现统一 401、403、429 和 5xx 响应。

验收：

- 未认证请求被拒绝。
- 权限不足请求返回 403。
- 合法 Token 能访问后端健康接口。

### Slice 04：Redis 限流与幂等

完成：

- 实现 route、IP、user、client、factory 维度 KeyResolver。
- 支持 `replenishRate`、`burstCapacity`、`requestedTokens`。
- 区分接口等级和 Redis 故障策略。
- 输出限流响应和指标。
- 建立 Redis 原子幂等占位能力。

验收：

- 多个 Gateway 实例共享令牌桶。
- 超限返回 429。
- Redis 不可用时按接口等级执行预设策略。
- 重复幂等请求被稳定拒绝。

### Slice 05：PostgreSQL 与数据访问基线

完成：

- PostgreSQL 每服务独立 Schema。
- Flyway 初始化与不可变迁移规则。
- MyBatis-Plus、String ID、审计、乐观锁和逻辑删除边界。
- 单权威 DataSource 与单 HikariCP 连接池。
- 连接预算、TCP Keepalive、ApplicationName 与 UTC 会话治理。

验收：

- 应用上下文默认只创建一个 HikariDataSource。
- PostgreSQL 可通过 `application_name` 识别服务连接。
- Prometheus 能看到 JDBC 和 Hikari 连接池指标。
- PostgreSQL 中断时应用按预期 fail-closed。

### Slice 06：消息与分布式事务基础设施

完成：

- Outbox/Inbox 最小表结构和接口。
- 业务写入与 Outbox 共用本地事务。
- Spring Cloud Stream + RocketMQ 生产、消费、重试和 DLQ。
- Inbox 消费幂等与重复投递验证。
- Seata AT 受控短事务 PoC。

验收：

- 一个本地事务同时写技术业务表和 Outbox。
- Outbox 消息发布并由消费者 Inbox 去重。
- 同一消息重复投递只产生一次业务结果。
- Broker 中断恢复、消费重试和 DLQ 真实通过。
- Seata 两数据库提交、回滚、Undo Log 和 TC 中断真实通过。

### Slice 07：OpenTelemetry 链路追踪

完成：

- HTTP、Gateway、OpenFeign 自动埋点。
- Outbox 发布与 RocketMQ 消费上下文传播。
- 日志 MDC 写入 `trace_id` 和 `span_id`。
- OTel Collector 发送到 Tempo。
- 修复 Boot 4.1 OpenTelemetry 与综合依赖图中的 OkHttp/Okio 二进制冲突。

验收：

- 固定 W3C Trace 经 Gateway → Integration → MDM 保持同一 Trace。
- Tempo 能查询完整链路。
- 日志可以关联 Trace。
- RocketMQ Consumer 具有消费 Span。
- Collector 中断不改变业务响应。

### Slice 08：Prometheus、Loki 与 Grafana 闭环

完成：

- Prometheus 抓取 Gateway、Integration 和 MDM。
- Grafana Alloy 采集真实应用日志并发送到 Loki。
- Grafana Provisioning 三个数据源和平台总览 Dashboard。
- Tempo Trace-to-Logs、Trace-to-Metrics 与 Loki Trace 跳转配置。
- 最小 Prometheus 告警规则。

验收：

- Grafana 三个数据源健康。
- Grafana Datasource Proxy 能实际查询指标、日志和 Trace。
- `mom-platform-overview` Dashboard 自动创建。
- Loki 能查询包含固定 Trace ID 的日志，且 Trace ID 不作为 Stream Label。
- 停止 MDM 后 `MOMServiceDown` 告警真实进入 firing。

## 5. 测试计划执行结果

- 单元测试：核心值对象、上下文、限流策略、事件信封、数据与消息组件。
- 集成测试：PostgreSQL、Redis、RocketMQ、Nacos、Seata。
- 契约测试：错误响应、事件信封和上下文字段。
- 架构测试：模块依赖和禁止类型。
- 故障测试：Redis 中断、数据库中断、MQ 重复投递、Broker 中断、TC 中断、Collector 中断、服务宕机告警。
- 可观测性测试：Tempo、Prometheus、Loki、Grafana 与 Alloy 真实容器闭环。

## 6. 完成定义

Phase 01 已同时满足：

- `mvn verify` 通过。
- CI 在 JDK 25 下通过。
- 核心服务可启动并注册到 Nacos。
- Gateway 能认证、路由和限流。
- PostgreSQL、Redis、RocketMQ、Seata 集成测试通过。
- Grafana 能查看并查询 Trace、日志和指标。
- 告警规则经过真实服务故障验证。
- 文档和 ADR 与实现一致。

## 7. 已知边界

| 边界 | 后续处理 |
|---|---|
| 当前观测栈为单机 CI 基线 | 在 `mom-infra` 中设计 k3s 部署、持久卷、资源限制和升级策略 |
| 未配置 Alertmanager 通知路由 | 部署阶段补充通知渠道、静默、分组和值班策略 |
| 未配置生产长期保留和对象存储 | 根据本地集群资源与演示需求单独规划 |
| 当前 Dashboard 是平台最小总览 | Phase 02 起随业务垂直切片增加领域 Dashboard 与告警 |
| Seata 只完成受控 AT PoC | 不将其扩展为长制造流程默认事务方案 |

## 8. 下一阶段门禁

Phase 02 可以开始，但必须遵守：

1. 业务切片继续复用现有 Gateway、安全、数据、消息和可观测性边界；
2. 每个业务写入必须同时设计幂等、本地事务、Outbox、审计和故障恢复；
3. 新增业务指标只能使用低基数标签；
4. 每个垂直切片必须提供 Trace、日志、指标和错误场景验收；
5. 不得为了快速 CRUD 绕过领域边界、库存事实模型或质量状态约束。
