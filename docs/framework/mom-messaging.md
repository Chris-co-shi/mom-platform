# mom-messaging：事件传输原语

**状态：有实现，未确认成熟业务生产者/消费者。** 模块定义 `EventEnvelope`、`EventType`、`EventTransport`、消息 Header 和基于 Spring Cloud Stream `StreamBridge` 的传输适配。源码入口为 [`mom-messaging`](../../mom-framework/mom-messaging/)。

- 推荐分工：业务事件先由所属 Owner 定义；传输层调用 `EventTransport`，可靠交付另用 `mom-outbox`。仅有 `EventEnvelope` 和 `StreamBridge` 不代表事件已与业务数据库提交原子化。
- 装配：`MomMessagingAutoConfiguration` 在 `StreamBridge` 类存在时提供传输 Bean；实际 binding、Broker 与部署连通性要在目标服务核查。
- 失败与限制：传输可能失败或重复；事件消费者需处理重复、乱序和所属领域状态。敏感数据不得进入 Payload，事件类型不能仅靠 Framework public 类宣布为跨服务稳定契约。

事务与重试边界见[事务一致性规范](../engineering/standards/transaction-consistency-standard.md)。
