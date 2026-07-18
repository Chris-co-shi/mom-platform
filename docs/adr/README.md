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
| [ADR-003](ADR-003-OAuth2与OIDC授权模型.md) | OAuth2.1 与 OIDC 授权模型 | Accepted | 安全架构 |
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

## 4. 新建 ADR

复制 [ADR 模板](ADR-模板.md)，使用以下命名格式：

```text
ADR-NNN-中文决策标题.md
```

编号一旦使用不得复用。
