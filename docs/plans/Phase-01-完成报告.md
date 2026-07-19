# Phase 01：技术骨架完成报告

- 阶段状态：**Completed — Foundation Only**
- 完成日期：2026-07-19
- 基线：JDK 25、Spring Boot 4.1.0、Spring Cloud 2025.1.2、Spring Cloud Alibaba 2025.1.0.0
- 后续阶段：[P1.5：认证与授权闭环](P1.5-认证与授权闭环计划.md)

## 1. 修正后的结论

Phase 01 已完成 MOM Platform 的构建、模块、服务治理、数据访问、消息、受控分布式事务和可观测性基础技术骨架。

Phase 01 **没有**完成完整 IAM 或认证授权安全闭环。当前尚不能把最小安全依赖、占位应用或上下文类型描述为正式 Authorization Server、Gateway Resource Server、业务服务最终授权、用户授权 Session 或 Web/Mobile 登录闭环。

P1.5 将在该基础之上完成认证与授权闭环。

## 2. 已交付能力

### 2.1 工程与架构

- Maven Reactor 与统一依赖管理。
- JDK 25 编译、单元测试、集成测试和 Enforcer。
- Framework、API、Client、Server 依赖方向。
- ArchUnit 架构边界。
- Java 中文 Javadoc 与第三方依赖治理规则。

### 2.2 服务与治理骨架

- Gateway WebFlux 路由与 Nacos 注册发现。
- IAM、MDM、Integration 等最小启动应用。
- Spring Security / OAuth2 Resource Server 依赖基线。
- 请求、用户、Client、工厂和关联上下文的基础类型/传播骨架。
- 统一错误响应、Redis 分布式限流和幂等占位。

> 上述安全相关内容是后续实现基础，不等于生产级认证授权闭环。

### 2.3 数据与事务

- PostgreSQL 每服务独立 Schema。
- Flyway 迁移规则。
- MyBatis-Plus 与 String ID 实体基线。
- 单服务单 DataSource、单 HikariCP。
- UTC 会话、TCP Keepalive、ApplicationName 与连接预算。
- Seata AT 两数据库受控短事务 PoC。

### 2.4 可靠消息

- 业务写入与 Outbox 同一本地事务。
- 租约式 Outbox 领取与 CAS。
- Spring Cloud Stream + RocketMQ。
- Inbox 消费幂等。
- 重复投递、Broker 中断恢复、消费重试和 DLQ。

### 2.5 可观测性

- Micrometer Observation/Tracing。
- Spring Boot OpenTelemetry Starter。
- Gateway、HTTP、OpenFeign、Outbox 与 RocketMQ Consumer Trace。
- OpenTelemetry Collector → Tempo。
- Actuator → Prometheus。
- Alloy → Loki。
- Grafana 三数据源 Provisioning、平台总览和告警基线。

## 3. 真实验证矩阵

| 工作流 | 验证内容 |
|---|---|
| CI | JDK 25 `clean verify`、架构边界、Nacos、Redis、PostgreSQL、Flyway、Hikari |
| Messaging CI | Outbox、RocketMQ、Inbox、重复投递、Broker 恢复、重试、DLQ |
| Seata CI | 两数据库全局提交、参与者失败、远端成功后回滚、Undo Log、TC 中断 |
| Observability CI | W3C Trace、Gateway、OpenFeign、MDM、Collector、Tempo、Collector 中断 |
| Observability Stack CI | Prometheus、Alloy、Loki、Grafana、代理查询和真实服务宕机告警 |

## 4. 已验证故障场景

- Redis 不可用。
- PostgreSQL 不可用。
- RocketMQ Broker 中断与恢复。
- 同一消息重复投递。
- 消费者持续失败进入 DLQ。
- Seata TC 中断。
- OpenTelemetry Collector 中断。
- MDM 服务停止后 Prometheus 告警 firing。

这些验证不包含 P1.5 的 Refresh 重放、Session 撤销、权限越权、跨 Factory/Party、Client/user_type 隔离或 Web/Mobile 登录恢复。

## 5. P1.5 前尚未完成的安全能力

- Spring Authorization Server、OIDC 和 IAM 登录页面。
- Authorization Code + PKCE S256。
- `mom-admin-web`、`mom-supplier-web`、`mom-customer-web`、`mom-mobile-pda` 四个 Public Client。
- `INTERNAL`、`SUPPLIER`、`CUSTOMER` 用户类型与应用访问矩阵。
- 用户、Role、Permission、Factory Scope、Party Binding、Mobile Access。
- JWT Claims、Gateway/业务服务 Resource Server。
- Opaque Refresh Token、HMAC-SHA-256 摘要、Rotation、重放检测和用户授权 Session。
- Redis revoked sid 与安全 Fail Closed。
- `/api/iam/me`、IAM 管理 API、安全审计和 Session 管理。
- CurrentActor 与 MyBatis-Plus 审计自动填充。
- Web/Mobile Auth Runtime 和安全 E2E。

## 6. 关键架构边界

Phase 01 继续有效的结论：

- 本地事务与 Outbox/Inbox 是跨服务一致性的默认方案。
- Seata 仅允许受控短同步事务，不进入长制造流程。
- 普通领域服务只有一个权威 DataSource。
- Trace ID 不作为业务主键、权限主体或幂等键。
- Prometheus 与 Loki 只索引低基数标签。
- 观测后端故障不得改变业务事务或消息语义。

认证授权的新权威结论见 [P1.5 认证与授权设计基线](../security/P1.5-认证与授权设计基线.md)。

## 7. 后续建议

先按 P1.5 S00～S12 完成认证授权闭环，再将依赖用户、权限、Factory/Party 隔离的 Phase 02 业务切片标记为可端到端安全验收。

P1.5 不改变 Phase 01 已完成的基础设施成果，但纠正了“完整 IAM/安全闭环已经完成”的不准确表述。
