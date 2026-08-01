# ADR-033：Event 与 Outbox Ownership

- 状态：Accepted
- 日期：2026-08-01
- 决策人：MOM Platform 维护者
- 关联需求：P1.6 Framework Governance Phase 2
- 关联文档：[ADR-005](ADR-005-Outbox与Inbox消息一致性.md)、[ADR-015](ADR-015-SpringCloudStream与RocketMQ消息边界.md)、[ADR-031](ADR-031-System运行时缓存变更通知与服务身份事务边界.md)

## 1. 背景

MOM 已有 `mom-messaging` 的 Broker 无关 EventEnvelope/Transport、`mom-outbox` 的 JDBC Lease/CAS/Retry/DEAD/Inbox，以及 `mom-data` 的 DataSource 与事务基线。System 当前用六个 String 常量表达事件类型。治理需要明确所有权，避免新建重复的 `mom-event`、`mom-data-access` 或让业务枚举跨 Context 传播。

## 2. 问题

Event Contract、业务 EventType、Broker Adapter、Outbox 状态机和 JDBC 访问分别应由谁拥有，如何在保持现有数据库与 Topic 线格式不变的同时冻结边界？

## 3. 候选方案

### 方案 A：把 Outbox JDBC 移入 mom-data

优点：所有 JDBC 类型集中。

缺点：Lease、SKIP LOCKED、CAS、Retry、DEAD 和 Inbox 不是通用 DAO；移动会模糊消息一致性状态机并制造门面。

### 方案 B：保持现有模块并增加最小 EventType 契约

优点：职责与运行故障面一致；业务枚举归 bounded context；不改变 EventEnvelope、表或 Topic。

缺点：业务消费者必须显式把字符串映射到本地枚举。

### 方案 C：在 Framework 建立统一业务 Event Enum Registry

优点：看似能集中发现事件。

缺点：造成 System/IAM 双向编译依赖，Framework 持有业务发布节奏，并为尚不存在的消费者提前抽象。

## 4. 决策

采用方案 B，并冻结以下 Ownership：

- `mom-messaging`：`EventEnvelope`、最小 `EventType#code()`、Broker Port、Spring Cloud Stream Adapter；
- `mom-outbox`：Outbox/Inbox、Lease、`FOR UPDATE SKIP LOCKED`、CAS、Retry、DEAD、JDBC Adapter；
- `mom-data`：唯一 DataSource、本地事务和通用数据访问基线；
- bounded context：自己的业务 Event Enum、Payload 与字符串映射。

Outbox JDBC 保留在 `mom-outbox`，不迁入 `mom-data`；不新增 `mom-event` 或 `mom-data-access` 门面。System 使用 `SystemEventType`，不使用含义模糊的 `SystemRuntimeEventType`。只有出现真实跨服务消费者时 IAM 才可新增 `IamEventType`。

## 5. 抽象成立依据

- EventEnvelope 已被 MDM 技术事件与 System Runtime Event 两个真实生产 Slice 使用，属于平台线格式。
- EventType 只解决“本地业务枚举生成稳定字符串”这一平台不变量，没有 Factory/Registry/Strategy；System 六个现有事件是当前场景。
- Outbox Publisher/JDBC Adapter 已被 MDM 和 System 两个生产服务消费；其 Lease/CAS/DEAD 状态机不能用普通 DAO 替代。
- 未创建 IAM Event、空 Topic 或空 Outbox 表；没有真实消费者时退出条件是“不新增”。

## 6. Event Enum 边界

- System、IAM 及其他 bounded context 不得互相 import/reference `*EventType` 枚举。
- 跨服务只共享 EventEnvelope、EventType 契约和稳定字符串 Code。
- Consumer 根据字符串 Code 解析自己的本地枚举；未知 Code fail-closed 并进入 Broker 重试/DLQ，不静默当成功。
- Event ID 首次写 Outbox 时生成，全部发布重试复用同一个 ID 和 Payload。

System 冻结本地枚举名称：

```text
SYSTEM_CATALOG_PUBLISHED
SYSTEM_CATALOG_STATUS_CHANGED
SYSTEM_PARAMETER_CHANGED
SYSTEM_DICTIONARY_CHANGED
SYSTEM_I18N_PUBLISHED
SYSTEM_I18N_STATUS_CHANGED
```

现有字符串 Code、Topic、数据库列和 System V9 Migration 保持不变。

## 7. 事务、重试与失败语义

- 业务写与 Outbox INSERT 使用同一个 PostgreSQL 本地事务。
- Claim 使用短事务、租约与 CAS；Broker 网络调用发生在 Claim 提交并释放连接/行锁之后。
- Binder Producer 内部重试关闭，由 Outbox RETRY/DEAD 统一控制；发送成功但 SENT CAS 失败允许重复发布。
- Consumer 业务写与 Inbox 记录使用同一本地事务；业务失败整体回滚，重复投递依靠 Inbox 唯一约束。
- Outbox DEAD 与 RocketMQ Consumer DLQ 是两个独立故障面。
- `last_error` 只保存稳定异常类型，不持久化异常消息、Token、Header 或 Payload。

## 8. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 跨 Context 直接引用业务枚举 | Framework Freeze ArchUnit 逐 Context 扫描 |
| 发送成功、状态更新失败产生重复 | Inbox + 领域状态机/唯一约束，CAS conflict 指标 |
| 多层消息重试放大 | Binder maxAttempts=1，Outbox 持久化 Retry |
| 错误信息泄露 | last_error 只保留异常类型，日志不写 Payload |
| JDBC 被误判为通用数据访问 | 只允许 `mom-outbox` 精确 Adapter 使用，架构门禁禁止复制 |

## 9. 验证方式

- `EventTypeContractTest`：本地枚举只写稳定字符串，Framework 接口只有 `code()`。
- `SystemEventTypeTest`：System 六个名称与现有 Code 固定。
- `OutboxPostgresqlIT`：活动事务 append、Lease、SKIP LOCKED、CAS、RETRY、Inbox 回滚与 Duplicate。
- `OutboxPublisherMetricsTest`：发送成功但 CAS 失败、Retry/DEAD 结果和错误脱敏。
- `system-rocketmq-runtime-event-smoke.sh`：真实 Broker 正常/重复、Broker 中断 RETRY/恢复、Poison DLQ、Redis 故障回源。

## 10. 替代与回滚条件

EventEnvelope 线格式、Outbox/Inbox 状态机或 Broker ACK 语义变化必须创建新 ADR。业务枚举内容按各 Context 的版本化契约演进，不修改本 ADR 的 Ownership。回滚新增 EventType 时仍保留字符串线格式，不回滚 JDBC 状态机。

## 11. 参考资料

- PostgreSQL `FOR UPDATE SKIP LOCKED`
- Spring TransactionSynchronizationManager / TransactionTemplate
- Spring Cloud Stream 与 RocketMQ Binder
- Micrometer Observation/Tracing
