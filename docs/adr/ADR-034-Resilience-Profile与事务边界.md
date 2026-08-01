# ADR-034：Resilience Profile 与事务边界

- 状态：Accepted
- 日期：2026-08-01
- 决策人：MOM Platform 维护者
- 关联需求：P1.6 Framework Governance Phase 3
- 关联文档：[出站 HTTP Client 规范](../engineering/standards/outbound-http-client-standard.md)、[事务一致性规范](../engineering/standards/transaction-consistency-standard.md)

## 1. 背景

System→IAM 已有有限 Feign connect/read timeout、Client Credentials 和 `Propagation.NEVER`，但没有统一 CircuitBreaker 与实例命名。MES 查询、设备命令和 System Query 的延迟、容量与失败语义不同，不能把某组 timeout/窗口参数冻结为全部业务的 Framework Contract。

## 2. 问题

如何统一 CircuitBreaker、显式幂等 Retry、Semaphore Bulkhead、TimeLimiter、Fallback 失败契约与 Micrometer 指标，同时防止远程等待占用数据库事务连接？

## 3. 候选方案

### 方案 A：Framework 写死全部默认参数并用自定义 Executor 包装

优点：调用形式统一。

缺点：重复 Resilience4j/Spring Cloud 能力；一个查询参数会误用于 MES/设备命令；新增无真实消费者的 Factory/Strategy。

### 方案 B：Spring Cloud CircuitBreaker + Resilience4j 官方配置，Framework 冻结命名与事务不变量

优点：OpenFeign 原生接入；实例→命名 Profile→default 的覆盖链由官方配置实现；指标自动接 Micrometer；自定义代码最少。

缺点：业务需要维护每个实例的 Profile 配置，并理解 Spring Cloud 与 Resilience4j 属性。

### 方案 C：Feign 只保留 timeout

优点：简单。

缺点：依赖持续故障时每次请求都等待 timeout，无法快速拒绝或隔离并发。

## 4. 决策

采用方案 B。新增 `mom-resilience`，引入非响应式 `spring-cloud-starter-circuitbreaker-resilience4j` 与 `resilience4j-micrometer`。模块只冻结：

- Profile 名称结构：业务实例 → 命名 Profile → `default`；
- Feign CircuitBreaker 实例名：`ClientSimpleName_methodName`；
- Retry 默认关闭且仅显式幂等操作可开启；
- Semaphore Bulkhead 为默认 Bulkhead 类型；
- Resilience 远程调用不得在活动数据库事务中执行；
- 没有安全 Fallback 时抛出依赖失败，禁止伪造成功。

不冻结 timeout、窗口、阈值、Open 等待、Bulkhead 容量等参数值。仓库中的数值只是可由环境覆盖的运行建议。

## 5. 抽象成立依据

- Spring Cloud OpenFeign CircuitBreaker 是平台基础能力，当前真实消费者是 System→IAM Permission Reference，后续消费者必须先定义业务失败语义。
- `MomResilienceNames` 被 OpenFeign 自动配置和 System 实例配置共同使用，解决低基数名称漂移；没有自建 Registry。
- `ResilienceTransactionGuard` 与 `Propagation.NEVER` 共同保护数据库连接，当前 System Feign Adapter 是真实消费者。
- 未新增自定义 Retry/Factory/Fallback 接口：官方能力已满足；当前没有可返回业务值的安全 Fallback，因而不为未来场景创建空接口。

## 6. Profile 与覆盖规则

```text
业务实例配置
→ base-config: system-query 等命名 Profile
→ default Profile / Resilience4j reasonable default
```

- `default` 和 `system-query` 的仓库值都可由环境变量覆盖。
- MES、设备命令等必须依据真实场景新建 Profile，不得复用未经故障验证的 System Query 参数。
- Feign connect/read timeout 继续由 `spring.cloud.openfeign.client.config` 管理；TimeLimiter 不能替代客户端 socket timeout。
- 参数值不属于 Framework Freeze 或架构测试断言目标。

## 7. Retry 与 Fallback

- OpenFeign 默认 `Retryer.NEVER_RETRY` 保持不变。
- POST 首版不自动 Retry，即使本端点是查询协议；未来只有具备明确幂等证据、次数上限和故障测试时才可单独开启。
- Spring Cloud `NoFallbackAvailableException` 的 Cause 映射到业务依赖错误：连接失败、timeout、无实例、Open Circuit 和 Bulkhead Full → Dependency Unavailable；HTTP 4xx → Protocol Error。
- 不配置返回值 Fallback，不返回“全部 Permission 有效”、空成功对象或过期权威数据。

## 8. 事务边界

禁止：

```text
@Transactional → Feign → CircuitBreaker / Retry / TimeLimiter
```

Feign Infrastructure Adapter 使用 `Propagation.NEVER`，并调用 `ResilienceTransactionGuard`。同步权威校验采用：

```text
非事务 Orchestrator → 远程调用 → 独立 Transactional Commit Service → 本地重校验
```

CircuitBreaker、Retry、Bulkhead 和 TimeLimiter 不得用于掩盖事务内远程等待。

## 9. System→IAM 决策

- `spring.cloud.openfeign.circuitbreaker.enabled=true`；
- 实例 `IamPermissionReferenceClient_validate` 使用可覆盖的 `system-query`；
- 保留有限 connect/read timeout；
- POST 不 Retry；
- 4xx 保留协议错误；基础设施故障映射依赖不可用；
- 不提供伪造成功 Fallback；
- IAM、Redis、PostgreSQL 和 Nacos 自身不由业务 Resilience Executor 包装。

## 10. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 参数被误认为不可变平台标准 | Metadata/ADR 标记 notFrozen，架构测试不断言数值 |
| CircuitBreaker 名称随版本变化 | 自定义稳定 NameResolver + 单元测试 |
| 事务内远程等待耗尽连接池 | Propagation.NEVER + Guard + Spring Proxy 测试 |
| POST Retry 重复副作用 | 默认 Retry 关闭，不配置 IAM Retry 实例 |
| Fallback 掩盖 IAM 故障 | 无返回值 Fallback，统一依赖错误模型 |

## 11. 验证方式

- `MomResilienceNamesTest`：Profile/实例命名稳定且低基数。
- `ResilienceTransactionGuardTest`：活动事务 fail-fast。
- OpenFeign 自动配置测试：NameResolver 使用 Client 类型与方法名。
- System Spring Proxy 测试：Feign 执行时无活动事务，事务内调用在到达 Client 前失败，Open Circuit 映射不可用。
- 现有 `system-iam-client-credentials-smoke.sh`：真实 IAM Token、Feign 校验和 IAM 故障时 System Readiness 独立。
- Nacos Discovery Smoke 继续验证无实例/恢复；Resilience 指标由 Micrometer Registry 导出。

## 12. 替代与回滚条件

若 Spring Cloud OpenFeign CircuitBreaker 命名或 Resilience4j 配置模型在升级中变化，创建新 ADR 并做真实故障测试。可临时关闭单个业务实例 CircuitBreaker，但不得放宽事务内调用禁令或以 Fallback 伪造成功。
