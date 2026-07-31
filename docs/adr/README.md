# 架构决策记录（ADR）

ADR 用于记录重要架构决策的背景、候选方案、最终选择、正负后果和替代条件。

## 1. 状态

- `Proposed`：已提出，尚未正式接受。
- `Accepted`：已经接受，当前有效。
- `Rejected`：评估后未采用。
- `Deprecated`：仍保留历史价值，但不建议继续使用。
- `Superseded`：已被新的 ADR 替代。

## 2. 管理规则

1. 每个 ADR 只记录一个主要决策。
2. 已接受的 ADR 不直接重写历史结论；决策变化时创建新的 ADR，并把旧 ADR 标记为 Superseded。
3. ADR 必须包含候选方案、决策理由、后果、风险和验证方式。
4. 实现与 ADR 不一致时，必须先确认是实现错误还是决策已变化。
5. 新增重要依赖、跨域调用、数据归属或基础设施组件时，应评估是否需要 ADR。

## 3. ADR 清单

| 编号 | 决策 | 状态 | 主要关联文档 |
|---|---|---|---|
| [ADR-001](ADR-001-jdk25-spring-boot4.md) | JDK 25 与 Spring Boot 4 技术基线 | Accepted | Phase 01 技术骨架计划 |
| [ADR-002](ADR-002-仓库与模块边界.md) | 仓库与模块边界 | Accepted | 领域边界 |
| [ADR-003](ADR-003-OAuth2与OIDC授权模型.md) | OAuth2.1 与 OIDC 授权模型 | Superseded by ADR-019 | P1.5 认证与授权设计基线 |
| [ADR-004](ADR-004-PostgreSQL按服务隔离Schema.md) | PostgreSQL 按服务隔离 Schema | Accepted | 数据架构 |
| [ADR-005](ADR-005-Outbox与Inbox消息一致性.md) | Outbox 与 Inbox 消息一致性 | Accepted | 集成架构 |
| [ADR-006](ADR-006-库存流水余额与预占模型.md) | 库存流水、余额与预占模型 | Accepted | 数据架构 |
| [ADR-007](ADR-007-批次谱系不可变边模型.md) | 批次谱系不可变边模型 | Accepted | 数据架构 |
| [ADR-008](ADR-008-PCS与WCS命令状态机协议.md) | PCS 与 WCS 命令状态机协议 | Accepted | 集成架构 |
| [ADR-009](ADR-009-Seata使用边界.md) | Seata 使用边界 | Accepted | 集成架构 |
| [ADR-010](ADR-010-开源复用与许可证规则.md) | 开源复用与许可证规则 | Accepted | 开源来源登记 |
| [ADR-011](ADR-011-prototype-first.md) | Web 与 PDA 原型先行 | Accepted | V1 垂直切片计划 |
| [ADR-012](ADR-012-distributed-tracing.md) | 分布式链路追踪 | Accepted | 可观测性架构 |
| [ADR-013](ADR-013-redis-rate-limit.md) | Redis 分布式限流 | Accepted | 安全架构 |
| [ADR-014](ADR-014-单服务单数据源与Hikari连接池治理.md) | 单服务单数据源与 Hikari 连接池治理 | Accepted | 数据架构、Outbox/Inbox |
| [ADR-015](ADR-015-SpringCloudStream与RocketMQ消息边界.md) | Spring Cloud Stream 与 RocketMQ 消息边界 | Accepted | 集成架构、Outbox/Inbox |
| [ADR-016](ADR-016-SeataAT受控PoC与故障边界.md) | Seata AT 受控 PoC 与故障边界 | Accepted | 集成架构、ADR-009 |
| [ADR-017](ADR-017-OpenTelemetry追踪与关联边界.md) | OpenTelemetry 追踪与关联边界 | Accepted | 可观测性架构、ADR-012 |
| [ADR-018](ADR-018-PrometheusLokiGrafana可观测性闭环.md) | Prometheus、Loki 与 Grafana 可观测性闭环 | Accepted | 可观测性架构、ADR-017 |
| [ADR-019](ADR-019-P1.5认证与授权闭环.md) | P1.5 认证与授权闭环 | Superseded by ADR-024 | P1.5 历史设计基线、ADR-024 |
| [ADR-020](ADR-020-PostgreSQL物理Schema命名空间.md) | PostgreSQL 物理 Schema 命名空间 | Accepted | ADR-004、数据架构、持久化规范 |
| [ADR-021](ADR-021-运行时配置来源与Secret边界.md) | 运行时配置来源与 Secret 边界 | Accepted | 部署架构、安全协议、配置规范 |
| [ADR-022](ADR-022-测试分层与CI质量门禁.md) | 测试分层与 CI 质量门禁 | Accepted | Maven 生命周期、Smoke、CI Scope |
| [ADR-023](ADR-023-Locale时区与用户偏好边界.md) | Locale、时区与用户偏好边界 | Accepted | 国际化、时间、量值和偏好规范 |
| [ADR-024](ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md) | PC JSON 与 Mobile PKCE/OIDC 双通道 | Accepted | P1.6 S06 审计、S07 决策、安全协议运行规范 |
| [ADR-025](ADR-025-IAM-System-MDM-WMS-EAM数据所有权边界.md) | IAM、System、MDM、WMS、EAM 数据所有权边界 | Accepted | 2026-07-29；方案 C 与七项决策已冻结 |
| [ADR-026](ADR-026-MOM业务表禁止物理外键与关联完整性策略.md) | MOM 业务表禁止物理外键与关联完整性策略 | Accepted | CRUD、多表关联与表结构规范 |
| [ADR-027](ADR-027-服务端包结构与基础设施适配器分层.md) | 服务端包结构与基础设施适配器分层 | Accepted | Package 与目录架构规范 |
| [ADR-028](ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md) | MyBatis-Plus Repository 抽象与领域仓储边界 | Accepted | 持久化规范、Repository Adapter 门禁 |

## 4. 新建 ADR

复制 [ADR 模板](ADR-模板.md)，使用以下命名格式：

```text
ADR-NNN-中文决策标题.md
```

编号一旦使用不得复用。
