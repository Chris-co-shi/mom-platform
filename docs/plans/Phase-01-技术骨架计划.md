# Phase 01：技术骨架计划

## 1. 阶段状态

- 状态：**Completed — Foundation Only**
- 完成日期：2026-07-19
- 完成报告：[Phase 01 完成报告](Phase-01-完成报告.md)
- 后续安全阶段：[P1.5 认证与授权闭环](P1.5-认证与授权闭环计划.md)

> Phase 01 完成的是基础技术骨架、兼容性验证和最小运行闭环，不代表完整 IAM、Authorization Server、Gateway/业务服务安全闭环或 Web/Mobile 登录已经实现。

## 2. 阶段目标

建立可编译、可启动、可测试、可观测的 MOM Platform 基础工程，并完成 JDK 25 + Spring Boot 4 体系与关键中间件的兼容性验证。

本阶段不实现业务 CRUD，也不实现生产级认证授权闭环。

## 3. 已完成范围

- `mom-dependencies` 统一版本管理。
- `mom-framework` 基础模块接口和自动配置边界。
- `mom-gateway` 最小路由、请求上下文、Redis 限流和可观测性骨架。
- `mom-iam-platform` 模块分层与最小启动骨架。
- `mom-security` Spring Security / OAuth2 Resource Server 依赖基线。
- MDM 和 Integration 最小健康检查服务。
- PostgreSQL、Redis、Nacos、RocketMQ、Seata 兼容性验证。
- Micrometer、OpenTelemetry、OTLP Collector 和 Tempo。
- Prometheus、Loki、Grafana 与 Alloy 基础接入。
- Testcontainers 和 ArchUnit 基础测试。

## 4. 明确未完成

以下能力不属于 Phase 01 已交付安全能力，统一进入 P1.5：

- 正式 Spring Authorization Server 配置与 IAM 登录页面。
- Authorization Code + PKCE、OIDC 和四个 Public Client。
- 用户、Role、Permission、Factory Scope、Party Binding 正式模型与 DDL。
- Gateway JWT/Issuer/Audience/revoked sid 完整校验。
- 业务服务 Resource Server 与最终 Permission/Scope 授权。
- Access Token、Opaque Refresh Token、ID Token 和 Claims 正式契约实现。
- 用户授权 Session、Refresh Rotation、重放检测和撤销。
- `/api/iam/me`、IAM 管理 API 和安全审计。
- Web/Mobile 登录运行时与安全 E2E。
- CurrentActor 与 MyBatis-Plus 审计字段自动填充正式实现。

MES、WMS、QMS 业务表、CRUD、完整 PCS/WCS 模拟器和生产级高可用中间件集群也不在 Phase 01 范围内。

## 5. 已完成切片

### Slice 01：构建与依赖基线

完成 JDK 25、Maven 3.9.9+、Boot/Cloud/Cloud Alibaba 版本锁定，以及 Enforcer、Compiler、Surefire、Failsafe、Release Dependency 和 Reactor 收敛门禁。

### Slice 02：Framework 最小能力

完成统一错误模型、请求上下文、用户/工厂上下文值对象、事件信封、幂等键、日志/指标/Trace/Test 模块边界和 ArchUnit 依赖规则。

> 用户和工厂上下文在本阶段只代表基础类型与传播骨架，不等于 P1.5 的可信 JWT Claims、CurrentActor 或最终授权。

### Slice 03：Gateway 与安全依赖骨架

完成 Gateway 路由、Nacos、统一响应和安全相关依赖/测试骨架。

未完成正式闭环：

- 未完成 Authorization Server。
- 未完成 Gateway 的完整 JWT、Issuer、Audience、Client、revoked sid 处理。
- 未完成业务服务 Resource Server 和最终授权。
- 未完成端到端用户登录。

原“Gateway 与安全闭环”命名不准确，自 P1.5 S00 起按“Gateway 与安全依赖骨架”理解。

### Slice 04：Redis 限流与幂等

完成 route、IP、user、client、factory 等 Key Resolver 基线、令牌桶参数、故障策略、响应指标和 Redis 原子幂等占位能力。

### Slice 05：PostgreSQL 与数据访问基线

完成每服务独立 Schema、Flyway 规则、MyBatis-Plus/String ID 基线、单权威 DataSource、HikariCP、连接预算、TCP Keepalive、ApplicationName 和 UTC 会话治理。

> CurrentActor、`MetaObjectHandler`、显式 SYSTEM Actor 和 P1.5 审计自动填充在 P1.5 S01 实现。

### Slice 06：消息与分布式事务基础设施

完成 Outbox/Inbox、本地事务、Spring Cloud Stream + RocketMQ、重试/DLQ、消费幂等和 Seata AT 受控短事务 PoC。

### Slice 07：OpenTelemetry 链路追踪

完成 HTTP、Gateway、OpenFeign、Outbox 和 RocketMQ Consumer Trace、MDC、Collector/Tempo 与故障隔离。

### Slice 08：Prometheus、Loki 与 Grafana 闭环

完成 Prometheus、Alloy、Loki、Grafana Provisioning、平台总览、Trace 跳转和最小告警规则。

## 6. 验证结果

Phase 01 已验证：

- JDK 25 下 `mvn verify`。
- Maven Reactor、模块依赖和架构边界。
- PostgreSQL、Redis、RocketMQ、Nacos、Seata 兼容性。
- Gateway/Integration/MDM 最小启动和调用链。
- Tempo、Prometheus、Loki、Grafana、Alloy 观测闭环。
- Redis、数据库、Broker、Seata TC、Collector 和服务宕机等故障场景。

这些验证不能替代 P1.5 的登录、Token、Session、权限和数据范围 E2E。

## 7. 修正后的完成定义

Phase 01 完成表示：

- 基础工程可编译、可启动、可测试、可观测。
- 核心中间件和基础设施兼容性经过验证。
- Gateway、IAM、MDM、Integration 具有可继续实现的模块与启动骨架。
- 安全依赖和边界已有初始基础，但认证授权闭环尚未实现。

## 8. 后续门禁

1. 在进入依赖真实用户身份、权限或外部主体隔离的业务闭环前，先完成 P1.5。
2. P1.5 的 Client、user_type、Token、Session、RBAC、Factory/Party Scope 和职责边界以权威设计基线为准。
3. 业务写入继续遵守幂等、本地事务、Outbox、审计和故障恢复。
4. 不得把 Phase 01 的基础上下文或依赖骨架当作生产安全边界。
