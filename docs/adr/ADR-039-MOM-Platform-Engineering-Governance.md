# ADR-039：MOM Platform Engineering Governance

- 状态：Accepted
- 日期：2026-08-01
- 关联需求：P1.6 Framework Governance Phase 8
- 关联决策：ADR-032～ADR-038

## 1. 背景

MOM Platform 已从单纯业务迭代进入平台工程阶段。Cache、Messaging/Outbox、Resilience、安全协议、数据访问和可观测性会被多个 bounded context 消费；如果继续按普通 `common` 代码管理，将出现重复基础设施、隐式 Breaking Change、业务模型跨 Context 泄漏和无法删除的 Legacy API。

## 2. 平台产品定位

`mom-framework` 是具有版本、契约、失败语义、可观测性和迁移路径的平台产品，不是公共代码垃圾箱。Framework 提供技术能力和稳定契约；IAM、System 及其他业务模块拥有业务模型并消费这些能力。

Framework 新抽象必须至少满足一项：

1. 已存在两个真实生产消费者；或
2. 明确属于平台基础能力，并有当前生产场景、失败语义和测试证据。

禁止为未来可能场景创建空接口、空 Provider、空 Module；禁止以 `common`、`support`、`extension` 隐藏无职责能力；禁止仅有一个简单实现时提前叠加 Factory/Strategy/Registry。

## 3. Breaking Change 与 Legacy 生命周期

公共 API 必须按“新增替代 API → Deprecated → 真实消费者迁移 → 全仓零调用 → 连续两个正式 Release 生产 Legacy Usage 为零 → Removal ADR → 后续 Major Cleanup”演进。P1.6 保留 `CacheType`、旧 `CacheKey`、`CachePolicy` 和旧 `CacheService` 方法；没有 CI、生产 Prometheus 证据及发布版本时不得删除。

## 4. Frozen Contract

本次 Freeze：

- typed Cache Region/Scope/Value/Entry API 与 Legacy Compatibility API；
- EventType 契约、EventEnvelope 线格式和 Outbox/Inbox 状态机；
- Resilience Profile 配置结构、实例命名、事务外远程调用不变量；
- Cache/Resilience 指标名称与配置前缀；
- Configuration Metadata 根索引和逐模块治理字段。

不 Freeze：

- Resilience 窗口、阈值、超时、Bulkhead 容量；
- 业务 Cache Region、业务 Event Enum 内容；
- 部署容量、采样率及业务实例覆盖值。

## 5. 安全、事件与事务不变量

- Authorization Decision、Permission Evaluation Result 和 Allow/Deny 判定默认禁止进入 CacheService；例外必须先有 Accepted Security ADR、撤销延迟、隔离、故障测试和 Kill Switch；
- Event Enum 归各 bounded context 所有，禁止 System/IAM 或其他 Context 互相引用；跨服务只共享 Envelope、EventType 与稳定 code；
- Outbox JDBC 保留在 `mom-outbox`，负责 Lease、CAS、Retry、DEAD、Inbox 与事务语义，不迁入 `mom-data`；
- Retry 默认关闭，POST 不自动 Retry，Fallback 不返回伪造成功；
- Feign/Resilience 远程调用不得位于活动数据库事务内，Adapter 使用 `Propagation.NEVER` 与运行时 Guard；
- revoked SID 是 fail-closed Security State，不是普通 Cache。

## 6. 基础设施依赖所有权

- System 生产源码不直接使用 Redis Template/Caffeine；IAM 不使用 Caffeine；
- 业务 Domain/Application 不依赖 Redis、Caffeine、JDBC Template、Feign 或 StreamBridge；
- StreamBridge 由 `mom-messaging` 持有；Outbox/Inbox JDBC 由 `mom-outbox` 持有；
- SAS 官方 JDBC Store 只允许 `IamAuthorizationServerProtocolConfiguration` 精确协议例外；
- 测试验收 JDBC 只存在测试源码；任何新增例外必须逐文件 ADR，禁止目录级白名单。

## 7. 抽象成立依据与当前消费者

| 能力 | 成立依据 |
|---|---|
| mom-cache typed API | Framework 自测与 System Catalog/Parameter/Dictionary/I18n 生产 Adapter |
| Event Contract | System 与既有 MDM/Integration 消息生产消费边界 |
| mom-outbox | System、MDM 等本地事务 Outbox/Inbox 与 Publisher |
| mom-resilience | System→IAM Feign 与后续显式命名 Profile 消费；能力基于官方 Spring Cloud CircuitBreaker |
| Configuration Metadata | Cache、Resilience、System 三个真实配置拥有者 |

本次没有创建 `mom-event`、`mom-data-access`、IAM Event Topic 或空 Outbox 表。

## 8. 平台适应度函数

持续执行：

- FrameworkGovernanceArchitectureTest：基础设施依赖、Context Event Enum、Legacy Cache、权限决策缓存、事务内 Feign/Resilience；
- SystemPlatformPomArchitectureTest / RuntimeSecurityArchitectureTest：模块依赖与 Redis 技术边界；
- ConfigurationMetadataGovernanceTest：根索引、字段完整性和 Resilience 非冻结；
- PlatformEngineeringGovernanceTest：ADR 决策链、Framework 模块所有权和 Legacy Removal 证据；
- mom-cache Redis IT、mom-outbox PostgreSQL IT、System/IAM Runtime Security 与中间件 Smoke Gate。

测试必须先于实现刻画约束；每个 Slice 精确 Commit，并在提交后核对 previous/new HEAD、changed scope 和 `codex-verify-changed`。

## 9. 风险与后续

- P1.6 只完成 Framework 稳定化和 IAM/System 首批治理，不代表所有业务 Context 已迁移；新消费者按本 ADR 渐进接入；
- Legacy API 仍存在是兼容性选择，不得把 Deprecated 误判为可立即删除；
- 本地无法代替连续两个正式 Release 的生产指标证据；Removal ADR 必须等待真实发布；
- 中间件兼容结论以独立 Redis/PostgreSQL/RocketMQ/Nacos CI/Smoke 为准，普通单元测试不替代真实基础设施验收。
