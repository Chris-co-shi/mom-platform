# ADR-035：Framework Freeze 与平台适应度函数

- 状态：Accepted
- 日期：2026-08-01
- 决策人：MOM Platform 维护者
- 关联需求：P1.6 Framework Governance Phase 4
- 关联文档：[ADR-032](ADR-032-Cache-Region与Factory-Scope兼容迁移.md)、[ADR-033](ADR-033-Event与Outbox-Ownership.md)、[ADR-034](ADR-034-Resilience-Profile与事务边界.md)

## 1. 背景

Cache、Event/Outbox 与 Resilience 已形成可供业务迁移的稳定边界。若仅靠文档约束，后续业务迭代仍可能重新直接注入 RedisTemplate、Caffeine、JdbcTemplate、Feign 或 StreamBridge，或跨 Context 共享事件枚举，使 Framework 退化为可选工具包。

## 2. 问题

哪些能力在 P1.6 Freeze，哪些参数和业务模型不 Freeze，以及如何用可执行、精确例外的适应度函数阻止架构回退？

## 3. 候选方案

### 方案 A：只发布文档和 Code Review Checklist

优点：零测试成本。

缺点：不能持续阻止 AI/开发者复制基础设施；例外容易扩大。

### 方案 B：ArchUnit + 源码 import + Maven POM 语义扫描

优点：依赖方向、技术 import、事务注解和精确文件例外可在 CI 自动执行。

缺点：源码扫描不能替代运行时故障测试，重命名或语法演进时需维护解析规则。

### 方案 C：禁止所有业务 Server 基础设施依赖

优点：规则最简单。

缺点：错误禁止 SAS 官方 JDBC Store、Security Revocation Redis 等协议边界，也无法允许 Infrastructure Adapter 消费 Framework。

## 4. 决策

采用方案 B，并将 `FrameworkGovernanceArchitectureTest` 纳入 Reactor。规则只扫描生产源码 import/注解；测试 JDBC 不在范围内。例外必须是完整文件路径，不接受目录、包或 glob。

### Freeze

- typed Cache Region API 与 Legacy Compatibility API；
- EventType/EventEnvelope 线格式与 Event Enum Ownership；
- Outbox/Inbox Lease、CAS、Retry、DEAD 状态机；
- Resilience Profile 结构、Feign 实例命名和事务外执行不变量；
- Cache/Outbox/Inbox 指标名称与配置前缀。

### 不 Freeze

- Resilience timeout/窗口/阈值/Bulkhead 容量；
- 业务 Cache Region 与 TTL；
- 业务 Event Enum 内容与 Payload 版本；
- 部署副本、连接池容量、遥测采样率和环境覆盖值。

## 5. 抽象成立依据

- 适应度函数覆盖 System/IAM 两个真实生产 bounded context，以及全部业务 Server 的共同禁止依赖。
- Source Scanner 只补充 ArchUnit 无法精确表达的逐文件协议例外；已有 Maven/ArchUnit 继续负责模块和字节码关系。
- 没有创建运行时 Framework 抽象；测试规则本身是平台治理能力。
- 退出条件：临时 System Redis 例外在 ADR-037 迁移后必须删除；永久协议例外若协议替换则在新 ADR 中删除。

## 6. 适应度规则

生产源码必须满足：

- System Server 不直接 import RedisTemplate/StringRedisTemplate/Caffeine；
- IAM Server 不 import Caffeine；Revoked SID Redis 是 Security fail-closed 状态，不作为 Cache；
- 业务 Domain/Application 不依赖 Redis、Caffeine、JDBC Template、Feign 或 StreamBridge；
- 业务 Server 不直接使用 JdbcTemplate/JdbcClient/NamedParameterJdbcTemplate；
- StreamBridge 只存在于 `mom-messaging`；Framework JDBC Template 只存在于 `mom-outbox`；
- 业务 Event Enum 不跨 bounded context；唯一共享类型是 `mom-messaging.EventType`；
- 业务生产代码不调用 Legacy CacheType/CacheKey/CachePolicy；
- 含最终授权决策语义的类型不得与 CacheService 组合；
- Feign Adapter 同时具备 `Propagation.NEVER` 和 `ResilienceTransactionGuard`。

## 7. 精确例外

| 文件 | 类型 | 理由 | 测试 | 退出条件 |
|---|---|---|---|---|
| `mom-iam-server/.../IamAuthorizationServerProtocolConfiguration.java` | 永久协议例外 | Spring Authorization Server 官方 JDBC Store | IAM Protocol/PostgreSQL IT | SAS Store 被官方非 JDBC 实现替代并有 ADR |
| `mom-outbox/.../JdbcOutboxRepository.java` | Framework Ownership | Lease/SKIP LOCKED/CAS 状态机 | OutboxPostgresqlIT | Outbox Ownership 被新 ADR 替代 |
| `mom-outbox/.../InboxDeduplicator.java` | Framework Ownership | Inbox 与业务写同事务 | OutboxPostgresqlIT | Inbox Ownership 被新 ADR 替代 |
| `RedisSystemRuntimeCacheAdapter.java` | 临时迁移例外 | ADR-031 既有实现 | System Runtime tests | ADR-037 typed Cache Adapter 完成，Phase 6 删除例外 |
| `RedisSystemI18nRuntimeCacheAdapter.java` | 临时迁移例外 | ADR-031 既有实现 | System I18n tests | ADR-037 typed Cache Adapter 完成，Phase 6 删除例外 |

测试源码的 PostgreSQL JdbcTemplate 不进入生产源码扫描，不需要生产例外。禁止增加 `**/security/**`、`**/infrastructure/**` 等宽泛白名单。

## 8. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 源码扫描误报注释 | 只解析 import；语义词规则先剥离注释 |
| 临时例外永久保留 | ADR-037 明确删除，Final Review 断言例外集合为空 |
| 静态测试无法证明故障语义 | Redis/PostgreSQL/RocketMQ/Nacos 独立真实 Smoke |
| 参数被架构测试锁死 | 测试只检查结构和依赖，不断言 Resilience 数值 |

## 9. 验证方式

- `FrameworkGovernanceArchitectureTest`：生产 import、Event Enum、Legacy Cache、权限决策与事务 Guard。
- `SystemPlatformPomArchitectureTest`：System 只声明批准的 Framework/Client 依赖。
- 现有 `MavenModuleDependencyArchitectureTest`、`PersistenceArchitectureTest`、`PackageLayoutArchitectureTest`。
- `bash scripts/codex-verify-changed.sh` 在每个 Slice 提交后验证 changed scope。

## 10. 替代与回滚条件

新增基础设施协议、SAS Store 变化或 Framework Ownership 调整必须先有新 ADR，再精确修改规则。不得以测试阻碍迭代为由关闭整个门禁；只能增加有原因、测试与退出条件的逐文件例外。
