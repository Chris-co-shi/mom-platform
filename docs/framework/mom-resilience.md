# mom-resilience：韧性命名与事务保护原语

**状态：已实现原语，未确认业务 Server 使用。** 本模块引入 Circuit Breaker 相关依赖，自己的运行时 API 只有 `MomResilienceNames.feignInstance(clientType, methodName)` 与 `ResilienceTransactionGuard.requireNoActiveTransaction(operation)`。源码入口为 [`mom-resilience`](../../mom-framework/mom-resilience/)。

- `feignInstance` 生成稳定实例名；静态 Profile 常量包括 `default` 与 `system-query`。它不自动给业务方法配置熔断器。
- `requireNoActiveTransaction` 在检测到活动 Spring 事务时抛异常，防止在不适合的事务内执行指定韧性操作；它不会挂起或恢复事务。
- 当前没有证据表明业务 Server 已采用具体重试/熔断 Profile。调用方必须先定义超时、可重试条件、副作用与失败策略，再按[出站 HTTP 规范](../engineering/standards/outbound-http-client-standard.md)和[事务规范](../engineering/standards/transaction-consistency-standard.md)接入。
