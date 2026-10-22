# mom-outbox：本地 Outbox/Inbox 原语

**状态：有实现和集成测试，未确认成熟业务事件链。** 模块提供 `OutboxAppender`、`OutboxPublisher`、`InboxDeduplicator` 与 JDBC 仓储。源码入口为 [`mom-outbox`](../../mom-framework/mom-outbox/)；事件内容和消费业务仍由所属 Owner 定义。

- 生产：业务 Application 的 `@Transactional` 方法先写业务状态，再调用 `OutboxAppender.append(event)` 写同一 DataSource。无活动本地事务时抛 `IllegalStateException`；数据库失败使业务事务回滚。
- 发布：显式 `mom.outbox.publisher.enabled=true` 且存在 `EventTransport` 才装配定时 Publisher。Publisher 领取记录后在数据库短事务外发送，再更新状态；Broker 接受而本地更新前崩溃仍会重发，故为至少一次交付。
- 消费：`InboxDeduplicator.executeOnce(event, consumerName, action)` 在消费方本地事务中插入 Inbox 并执行业务动作；首次成功返回 `true`，重复返回 `false`，业务/数据库异常透传且回滚，供 Binder 重试。不同 `consumerName` 的幂等空间独立。
- 前置条件：生产与消费各有本地表及 Flyway Migration、事务管理器；发布需配置 binding、调度、重试和 DEAD 处置。不能仅凭 Bean 存在推断 Broker 验收通过。

Inbox 不替代领域状态机、条件更新或唯一约束。完整边界见[事务一致性规范](../engineering/standards/transaction-consistency-standard.md)。
