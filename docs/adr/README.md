# 架构决策记录（ADR）

ADR 用于记录重要架构决策的背景、候选方案、最终选择、后果、风险和替代条件。

## 1. 状态

- `Proposed`：已提出，尚未正式接受；
- `Accepted`：已经接受，当前有效；
- `Rejected`：评估后未采用；
- `Deprecated`：保留历史价值，但不建议继续使用；
- `Superseded`：已被新的 ADR 替代。

## 2. 管理规则

1. 每个 ADR 只记录一个主要决策；
2. 已接受 ADR 不直接抹去历史结论；决策变化时新增 ADR，并更新旧 ADR 状态或补充替代关系；
3. ADR 必须包含候选方案、决策理由、后果、风险和验证方式；
4. 实现与 ADR 不一致时，必须先判断实现错误或决策变化；
5. 新增重要依赖、跨域调用、数据归属或基础设施组件时必须评估 ADR。

## 3. ADR 清单

| 编号 | 决策 | 状态 |
|---|---|---|
| [ADR-001](ADR-001-jdk25-spring-boot4.md) | JDK 25 与 Spring Boot 4 技术基线 | Accepted |
| [ADR-002](ADR-002-仓库与模块边界.md) | 仓库与模块边界 | Accepted |
| [ADR-003](ADR-003-OAuth2与OIDC授权模型.md) | OAuth2.1 与 OIDC 授权模型 | Superseded by ADR-019 |
| [ADR-004](ADR-004-PostgreSQL按服务隔离Schema.md) | PostgreSQL 按服务隔离 Schema | Accepted |
| [ADR-005](ADR-005-Outbox与Inbox消息一致性.md) | Outbox 与 Inbox 消息一致性 | Accepted |
| [ADR-006](ADR-006-库存流水余额与预占模型.md) | 库存流水、余额与预占模型 | Accepted |
| [ADR-007](ADR-007-批次谱系不可变边模型.md) | 批次谱系不可变边模型 | Accepted |
| [ADR-008](ADR-008-PCS与WCS命令状态机协议.md) | PCS 与 WCS 命令状态机协议 | Accepted |
| [ADR-009](ADR-009-Seata使用边界.md) | Seata 使用边界 | Accepted |
| [ADR-010](ADR-010-开源复用与许可证规则.md) | 开源复用与许可证规则 | Accepted |
| [ADR-011](ADR-011-prototype-first.md) | Web 与 PDA 原型先行 | Accepted |
| [ADR-012](ADR-012-distributed-tracing.md) | 分布式链路追踪 | Accepted |
| [ADR-013](ADR-013-redis-rate-limit.md) | Redis 分布式限流 | Accepted |
| [ADR-014](ADR-014-单服务单数据源与Hikari连接池治理.md) | 单服务单数据源与 Hikari 连接池治理 | Accepted |
| [ADR-015](ADR-015-SpringCloudStream与RocketMQ消息边界.md) | Spring Cloud Stream 与 RocketMQ 消息边界 | Accepted |
| [ADR-016](ADR-016-SeataAT受控PoC与故障边界.md) | Seata AT 受控 PoC 与故障边界 | Accepted |
| [ADR-017](ADR-017-OpenTelemetry追踪与关联边界.md) | OpenTelemetry 追踪与关联边界 | Accepted |
| [ADR-018](ADR-018-PrometheusLokiGrafana可观测性闭环.md) | Prometheus、Loki 与 Grafana 可观测性闭环 | Accepted |
| [ADR-019](ADR-019-P1.5认证与授权闭环.md) | P1.5 认证与授权闭环 | Superseded by ADR-024 |
| [ADR-020](ADR-020-PostgreSQL物理Schema命名空间.md) | PostgreSQL 物理 Schema 命名空间 | Accepted |
| [ADR-021](ADR-021-运行时配置来源与Secret边界.md) | 运行时配置来源与 Secret 边界 | Accepted |
| [ADR-022](ADR-022-测试分层与CI质量门禁.md) | 测试分层与 CI 质量门禁 | Accepted |
| [ADR-023](ADR-023-Locale时区与用户偏好边界.md) | Locale、时区与用户偏好边界 | Accepted |
| [ADR-024](ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md) | PC JSON 与 Mobile PKCE/OIDC 双通道 | Superseded by ADR-040 for V1 auth runtime |
| [ADR-025](ADR-025-IAM-System-MDM-WMS-EAM数据所有权边界.md) | IAM、System、MDM、WMS、EAM 数据所有权边界 | Accepted |
| [ADR-026](ADR-026-MOM业务表禁止物理外键与关联完整性策略.md) | MOM 业务表禁止物理外键与关联完整性策略 | Accepted |
| [ADR-027](ADR-027-服务端包结构与基础设施适配器分层.md) | 服务端包结构与基础设施适配器分层 | Accepted as Level 2/3 option；默认强制解释由 ADR-042 替代 |
| [ADR-028](ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md) | MyBatis-Plus Repository 抽象与领域仓储边界 | Accepted as Level 2/3 option；默认强制解释由 ADR-042 替代 |
| [ADR-029](ADR-029-IAM-Admin分层与领域模型边界.md) | IAM Admin 分层与领域模型边界 | Historical for old IAM / Mini Auth 不采用 |
| [ADR-030](ADR-030-System应用目录导航发布与IAM权限引用边界.md) | System 应用目录、导航发布与 IAM 权限引用边界 | Accepted |
| [ADR-031](ADR-031-System运行时缓存变更通知与服务身份事务边界.md) | System Runtime Cache、变更通知、服务身份与事务边界 | Accepted |
| [ADR-032](ADR-032-Cache-Region与Factory-Scope兼容迁移.md) | Cache Region、Factory Scope 与兼容迁移 | Accepted |
| [ADR-033](ADR-033-Event与Outbox-Ownership.md) | Event 与 Outbox Ownership | Accepted |
| [ADR-034](ADR-034-Resilience-Profile与事务边界.md) | Resilience Profile 与事务边界 | Accepted |
| [ADR-035](ADR-035-Framework-Freeze与平台适应度函数.md) | Framework Freeze 与平台适应度函数 | Accepted |
| [ADR-036](ADR-036-IAM安全状态Cache与Event边界.md) | IAM 安全状态、Cache 与 Event 边界 | Historical where it conflicts with ADR-040 |
| [ADR-037](ADR-037-System消费Cache与Event-Framework.md) | System 消费 Cache/Event Framework | Accepted |
| [ADR-038](ADR-038-Configuration-Metadata-Governance.md) | Configuration Metadata Governance | Accepted |
| [ADR-039](ADR-039-MOM-Platform-Engineering-Governance.md) | MOM Platform Engineering Governance | Accepted |
| [ADR-040](ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) | Mini Auth 与 Redis Opaque Token 认证基线 | Accepted |
| [ADR-041](ADR-041-Mini-Auth简化三层包结构.md) | Mini Auth 简化三层包结构（历史 `service` 命名） | Superseded by ADR-042 |
| [ADR-042](ADR-042-MOM渐进式分层与对象模型.md) | MOM 渐进式分层、3+1 对象模型与按复杂度升级原则 | Accepted |

## 4. 当前认证决策

V1 当前认证运行时以 ADR-040 为权威：

```text
ADR-019 完整认证闭环
→ ADR-024 PC JSON + Mobile PKCE/OIDC
→ ADR-040 Mini Auth + Redis Opaque Token
```

当前 V1 明确采用：

```text
第一方账号密码认证
→ 高熵 Opaque Access Token
→ Redis Token Store
→ Servlet Resource Server
→ @PreAuthorize
```

不使用 JWT、Refresh Token、Session、Spring Authorization Server 或 OIDC。

Mini Auth 代码组织当前以 ADR-042 为准：

```text
controller → application → infrastructure
```

不默认创建 `domain`、Repository Port、Repository Adapter、Converter 或只为形式满足依赖倒置的接口。

## 5. 当前项目级分层决策

ADR-042 是新增业务代码的默认架构入口：

```text
Level 1
controller/web → application → infrastructure

Level 2
controller/web → application → domain + infrastructure

Level 3
controller/web → application/domain → port ← infrastructure adapter
```

核心原则：

- 简单业务不预付复杂架构成本；
- Domain 由真实状态机、不变量和复杂生命周期触发；
- Port/Adapter 由真实替换边界和外部系统隔离触发；
- 已稳定采用 Level 2/3 的历史模块不要求为了统一目录反向降级；
- 多表 JOIN 查询结果默认是 View/Row/Projection，不自动等于 DDD Aggregate；
- 新增对象默认采用 Request/Response、Entity、View、按需 Row/Projection 的 3+1 语义。

## 6. P1.6 历史权威决策链

以下链路保留 P1.6 历史治理和平台设计价值，其中与 V1 认证运行时冲突的安全结论以 ADR-040 为准；分层的默认强制解释以 ADR-042 为准：

```text
ADR-023 Locale/Timezone/Preference
→ ADR-024 PC JSON + Mobile PKCE/OIDC
→ ADR-025 数据所有权
→ ADR-026 无物理业务 FK
→ ADR-027 Package/Adapter 分层
→ ADR-028 MyBatis-Plus Repository
→ ADR-029 IAM Admin 分层
→ ADR-030 Catalog/Navigation/Permission Reference
→ ADR-031 Cache/Event/Service Identity/Transaction
→ ADR-032 Cache Region/Factory Scope/Compatibility
→ ADR-033 Event/Outbox Ownership
→ ADR-034 Resilience Profile/Transaction Boundary
→ ADR-035 Framework Freeze/Fitness Functions
→ ADR-036 IAM Security State/Cache/Event Boundary
→ ADR-037 System Cache/Event Framework Consumption
→ ADR-038 Configuration Metadata Governance
→ ADR-039 MOM Platform Engineering Governance
→ ADR-040 Mini Auth Runtime
→ ADR-041 Mini Auth 三层简化
→ ADR-042 渐进式分层与对象模型
```

## 7. 新建 ADR

复制 [ADR 模板](ADR-模板.md)，命名：

```text
ADR-NNN-中文决策标题.md
```

编号一旦使用不得复用。