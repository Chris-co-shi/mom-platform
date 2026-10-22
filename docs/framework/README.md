# Framework 真实模块与接入边界

本文按 [`mom-framework/pom.xml`](../../mom-framework/pom.xml) 的 **19 个 Maven 子模块**列出当前能力。状态依据本仓库源码、POM 和已发现调用方；POM 依赖只证明装配，不证明业务路径已采用。公开 Java 类型也不自动成为稳定业务契约。业务模块先查本表，再读对应源码和专题规范。

状态：**业务采用**＝存在生产源码调用；**基础设施采用**＝在 Gateway/Server 装配或基础设施中使用；**原语/探针**＝有实现但缺少成熟业务链；**依赖/骨架**＝没有自有运行时 Java API。以下“应”只有在现有 AGENTS、Accepted ADR 或工程规范已有依据时才是强制要求；其余接入建议须结合具体 Slice 审核。

| Maven 模块 | 职责、推荐入口 | 已确认使用与成熟度 | 关键边界 |
|---|---|---|---|
| [`mom-core`](mom-core.md) | `PageQuery`/`PageResult`、`CurrentActorProvider`、`AuditActor`、`IdGenerator`、`I18nMessageResolver`、关联 ID | 多服务使用的公共契约；基础设施采用 | `AuditContextExecutor` 源文件目前全为注释，不是 API；ActorType 只有 USER/SYSTEM |
| [`mom-data`](mom-data.md) | `PageAdapter`、`MomBaseMapper`、Entity 基类、审计填充与 MyBatis-Plus 配置 | Auth、MDM、WMS 等业务采用 | 分页转换复用 `PageAdapter`；自定义 SQL 自行处理审计 |
| [`mom-data-permission`](mom-data-permission.md) | 数据权限预留模块 | 仅 Maven 骨架 | 无运行时过滤器或可用数据范围 API；不可宣称已做行级隔离 |
| [`mom-security`](mom-security.md) | `MomTokenStore`、`MomOpaqueTokenIntrospector`、Resource Server/Actor 自动配置 | Auth 与业务 Server 基础设施采用 | 最终 Token 校验在 Resource Server；Token Store 故障失败关闭 |
| [`mom-webmvc`](mom-webmvc.md) | `Result<T>`、Correlation Filter、I18n/Code Rule HTTP Controller | 多 Server 基础设施采用 | `Result<T>` 只用于 HTTP 边界；业务 Application 不返回它 |
| [`mom-openfeign`](mom-openfeign.md) | Feign 请求拦截器自动配置 | 各 `*-client` 装配采用 | 只传播当前请求的 Bearer；不签发服务身份、不自动重试业务写 |
| [`mom-code-rule`](mom-code-rule.md) | `CodeGenerationApplication.generate(ruleCode, context, requestKey)`、规则管理、Handler/Registry/JDBC Store | MDM/WMS Owner 接入；WMS 期初和盘点有业务消费者 | 规则、流水和已发编码归 Owner 本地库；正式发号需与业务写处于同一本地事务 |
| [`mom-i18n`](mom-i18n.md) | `I18nManagementService`、`I18nRuntimeService`、Owner 本地 JDBC Store | Auth/System/MDM/WMS 装配，System 管 Locale | 翻译归各 Owner；运行时回退到 Owner 基准 Locale，再回退 Key |
| [`mom-cache`](mom-cache.md) | `CacheService`、`CacheRegion<T>`、`CacheEntryKey<T>` | 原语；未确认业务生产消费者 | 只放可重建读投影；Redis 故障按 Miss 回源，Loader 异常原样抛出 |
| [`mom-idempotency`](mom-idempotency.md) | `IdempotencyGuard.tryAcquire(scope, requestKey, ownerToken, ttl)` | Integration 默认关闭探针使用；无成熟业务消费者 | Redis SET NX 占位不保存结果，不与数据库事务原子化 |
| [`mom-messaging`](mom-messaging.md) | `EventEnvelope`、`EventTransport`、StreamBridge 适配 | 原语；未确认业务生产者/消费者 | 传输不等于事务交付，事件类型和敏感信息由服务治理 |
| [`mom-outbox`](mom-outbox.md) | `OutboxAppender`、`OutboxPublisher`、`InboxDeduplicator`、JDBC 仓储 | 有实现与集成测试，未确认业务事件链 | 业务写与 Outbox、消费写与 Inbox 各自同本地事务；至少一次，仍可能重复 |
| [`mom-resilience`](mom-resilience.md) | `MomResilienceNames`、`ResilienceTransactionGuard` | 原语；未确认业务 Server 调用 | Guard 只检查活动事务，不自动配置熔断/重试或挂起事务 |
| [`mom-metrics`](mom-metrics.md) | `MomMetricNames`、Registry 公共标签自动配置 | Gateway 限流与 Outbox/Inbox 基础设施采用 | 不创建 Registry/Exporter；业务高基数标签需按观测规范审查 |
| [`mom-tracing`](mom-tracing.md) | `CurrentTraceContext.snapshot()` | 多服务装配；未确认业务直接调用 | 无活动 Span/Tracer 时返回空快照，不创建 Span |
| [`mom-observation`](mom-observation.md) | Micrometer 依赖聚合 | 依赖模块，无自有 Java API | 不宣称统一业务埋点入口 |
| [`mom-logging`](mom-logging.md) | 日志依赖聚合 | Gateway 装配，无自有 Java API | 日志格式配置不保证 Trace 已导出 |
| [`mom-seata`](mom-seata.md) | Seata 受控依赖 | 未发现业务采用，默认关闭 | 仅按 [ADR-009](../adr/ADR-009-Seata使用边界.md) 的边界做受控验证 |
| [`mom-test`](mom-test.md) | 测试依赖聚合 | 多测试模块采用，无自有 Java API | 只供测试；不作为生产公共能力 |

## 使用方式

1. 在上表选择真实 Maven 模块，打开对应文档核查公开入口、消费者、装配条件、失败语义与限制；每篇文档都链接该模块源码。
2. 涉及具体业务 Owner、数据表、权限或事务时，再读表内指向的工程规范及目标服务源码；模块名和 Maven 依赖不能证明业务集成完成。
3. `mom-data-permission`、`mom-seata`、`mom-observation`、`mom-logging`、`mom-test` 当前没有自有运行时 Java API；其独立文档只界定现状和不适用范围。
