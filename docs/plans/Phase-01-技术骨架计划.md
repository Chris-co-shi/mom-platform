# Phase 01：技术骨架计划

## 1. 阶段目标

建立可编译、可启动、可测试、可观测的 MOM Platform 基础工程，并完成 JDK 25 + Spring Boot 4 体系与关键中间件的兼容性验证。

本阶段不实现业务 CRUD。

## 2. 范围

### 2.1 必须完成

- `mom-dependencies` 统一版本管理。
- `mom-framework` 基础模块接口和自动配置边界。
- `mom-gateway` 最小路由、认证上下文、Redis 限流。
- IAM 最小 OAuth2.1/OIDC 服务。
- MDM 和 Integration 最小健康检查服务。
- PostgreSQL、Redis、Nacos、RocketMQ、Seata 兼容性验证。
- Micrometer、OpenTelemetry、OTLP Collector 和 Tempo。
- Prometheus、Loki、Grafana 基础接入。
- Testcontainers 和 ArchUnit 基础测试。

### 2.2 明确不做

- MES、WMS、QMS 业务表和 CRUD。
- 完整用户、角色和菜单管理页面。
- 完整数据权限 SQL 重写实现。
- 完整 PCS/WCS 模拟器。
- 生产级高可用中间件集群。

## 3. 实施顺序

### Slice 01：构建与依赖基线

任务：

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

任务：

- 定义统一错误模型和异常边界。
- 定义请求上下文、用户上下文和工厂上下文。
- 定义事件信封、幂等键和关联标识。
- 定义日志、指标、Trace 和测试模块边界。
- 使用 ArchUnit 验证模块依赖方向。

验收：

- Framework 不包含 MES、WMS、QMS 业务类型。
- `*-api` 不引用 Entity、Mapper 和 Repository。
- `*-server` 不能直接依赖其他领域的 `*-server`。

### Slice 03：Gateway 与安全闭环

任务：

- Gateway 接入 Nacos 注册发现。
- 接入 OAuth2 Resource Server。
- 校验 JWT、Scope 和 Audience。
- 传递用户、Client、工厂和关联上下文。
- 实现统一 401、403、429 和 5xx 响应。

验收：

- 未认证请求被拒绝。
- 权限不足请求返回 403。
- 合法 Token 能访问后端健康接口。

### Slice 04：Redis 限流

任务：

- 实现 route、IP、user、client、factory 维度 KeyResolver。
- 支持 `replenishRate`、`burstCapacity`、`requestedTokens`。
- 区分公开安全接口、外部接口、生产操作和高成本查询的故障策略。
- 输出限流指标和响应头。

验收：

- 三个 Gateway 实例共享令牌桶。
- 超限返回 429。
- Redis 不可用时按接口等级执行预设策略。

### Slice 05：链路追踪

任务：

- HTTP、Gateway、Feign 自动埋点。
- RocketMQ 生产和消费传播上下文。
- 日志 MDC 写入 `trace_id` 和 `span_id`。
- 业务事件携带 `correlation_id`、`workflow_id` 和 `event_id`。
- OTel Collector 发送到 Tempo。

验收：

- Grafana 能查看 Gateway 到后端服务的 Trace。
- 日志可以关联 Trace。
- RocketMQ 消费形成新的 Span 并保留关联关系。

### Slice 06：数据和消息基础设施

任务：

- PostgreSQL 每服务独立 Schema。
- 普通领域服务采用单权威 DataSource 和单 HikariCP 连接池。
- 固化连接池默认容量、连接获取超时、TCP Keepalive、ApplicationName 与 UTC 会话。
- 按服务最大副本数建立 PostgreSQL 连接预算，保留滚动升级和运维容量。
- Flyway 初始化脚本。
- Outbox/Inbox 最小表结构和接口。
- 业务表、Outbox 和 Inbox 共用所属服务 DataSource 与本地事务。
- RocketMQ 最小生产、消费、重试和死信验证。
- Seata AT 兼容性 PoC，不扩展到长流程。

验收：

- 应用上下文默认只创建一个 HikariDataSource。
- PostgreSQL 可通过 `application_name` 识别服务连接。
- Prometheus 能看到 JDBC 和 Hikari 连接池指标。
- 一个本地事务同时写业务测试表和 Outbox。
- Outbox 消息发布并由消费者 Inbox 去重。
- 同一消息重复投递三次只处理一次。

## 4. 测试计划

- 单元测试：核心值对象、上下文、限流策略和事件信封。
- 集成测试：PostgreSQL、Redis、RocketMQ、Nacos。
- 契约测试：错误响应、事件信封和上下文字段。
- 架构测试：模块依赖和禁止类型。
- 故障测试：Redis 中断、数据库中断、MQ 重复投递、应用重启。

## 5. 完成定义

Phase 01 完成必须同时满足：

- `mvn verify` 通过。
- CI 在 JDK 25 下通过。
- 核心服务可启动并注册到 Nacos。
- Gateway 能认证、路由和限流。
- PostgreSQL、Redis、RocketMQ 集成测试通过。
- Grafana 能看到 Trace、日志和指标。
- 文档和 ADR 与实现一致。

## 6. 风险

| 风险 | 应对 |
|---|---|
| Boot 4 与某个 Starter 不兼容 | 优先替换或自行配置 Starter，不回退到 Boot 3 |
| 中间件同时接入导致排障困难 | 按 Slice 单独完成兼容性验证 |
| Framework 过早膨胀 | 只实现首个垂直切片需要的公共能力 |
| 链路追踪标签失控 | 只允许低基数指标标签，业务 ID 放日志和 Span 属性 |
| Redis 故障导致生产操作全部失败 | 按接口等级定义 fail-open、fail-closed 或本地应急桶 |
| Pod 扩容和滚动升级耗尽 PostgreSQL 连接 | 按副本数计算连接预算，默认每实例最大 5，并保留运维和发布余量 |
