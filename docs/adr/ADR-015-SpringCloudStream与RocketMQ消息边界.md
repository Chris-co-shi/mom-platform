# ADR-015：Spring Cloud Stream 与 RocketMQ 消息边界

- 状态：Accepted
- 日期：2026-07-18
- 决策人：项目维护者
- 关联需求：Phase 01 数据与消息基础设施
- 关联文档：[ADR-005](ADR-005-Outbox与Inbox消息一致性.md)、[ADR-014](ADR-014-单服务单数据源与Hikari连接池治理.md)、[集成架构](../architecture/集成架构.md)

## 1. 背景

MOM Platform 需要在生产、库存、质量、Integration、PCS、WCS 等服务之间可靠传递版本化领域事件。当前技术基线为 Spring Boot 4.1、Spring Cloud 2025.1、Spring Cloud Alibaba 2025.1 和 RocketMQ 5.x。

直接使用 RocketMQ Client 可以获得完整底层能力，但会让业务服务依赖 Producer、Consumer、Listener、Header 转换和生命周期细节。Spring Cloud Stream 提供函数式消息模型、Binding 和 Binder 抽象，Spring Cloud Alibaba 官方提供 RocketMQ Binder，并与当前 Boot 4 技术基线对齐。

与此同时，Binder 不能解决数据库业务写入与消息发送之间的原子性，也不能替代消费业务幂等。因此必须明确 Stream、RocketMQ、Outbox 和 Inbox 的职责边界。

## 2. 问题

MOM 是否使用 Spring Cloud Stream 作为 RocketMQ 应用适配层，以及哪些可靠性能力允许交给 Stream/Binder，哪些能力必须由 MOM 自己控制？

## 3. 候选方案

### 方案 A：领域服务直接使用 RocketMQ Client

优点：

- 可以直接访问全部 RocketMQ Producer、Consumer、顺序消息、事务消息和管理参数。
- 消息发送结果和底层异常更直接。

缺点：

- 每个服务重复处理生命周期、序列化、Header、消费线程和配置。
- 领域应用层与 RocketMQ API 强耦合，后续测试和替换成本高。
- 容易把 Broker API 扩散到业务事务代码。

### 方案 B：使用 Spring Cloud Stream + RocketMQ Binder，可靠状态由 MOM 显式实现

优点：

- 使用函数式 Consumer 和 StreamBridge 统一生产、消费编程模型。
- Topic、消费者组、内容类型和 Binder 参数通过配置管理。
- 事件契约和 Outbox/Inbox 不依赖 RocketMQ 类型。
- 可以在单元测试中替换 EventTransport，而不启动 Broker。

缺点：

- RocketMQ 特有能力仍需要扩展 Binding 配置或自定义适配器。
- Binder 抽象可能掩盖发送、确认和重试的真实语义，必须通过真实 Broker 测试验证。
- Spring Cloud Stream 与 Spring Cloud Alibaba 版本必须严格对齐。

### 方案 C：只依赖 RocketMQ 事务消息，不实现 Outbox/Inbox

优点：

- 代码表面上减少 Outbox 表和发布任务。

缺点：

- 本地事务回查与业务状态耦合，无法替代所有跨服务长流程恢复。
- 消费重复、确认失败和业务幂等仍然存在。
- 领域状态与消息状态的审计、补偿和人工处理能力不足。

## 4. 决策

采用方案 B：

1. MOM 使用 Spring Cloud Stream 的函数式模型和 Spring Cloud Alibaba RocketMQ Binder 作为默认 RocketMQ 应用适配层。
2. `mom-messaging` 定义 Broker 无关的事件信封、Header 和 `EventTransport` 端口。
3. 默认生产适配器使用 `StreamBridge`，服务必须使用预先配置的稳定 Binding 名称，不允许根据外部输入无限创建动态 Binding。
4. Topic、生产者组、消费者组、NameServer、超时、顺序消费、重试和 DLQ 参数由服务配置显式声明。
5. 事件正文使用明确 JSON，不使用 Java 原生序列化。
6. 业务写入和 Outbox INSERT 使用同一 DataSource、事务管理器和本地事务；Binder 不参与该事务。
7. Outbox 发布器在短事务中领取和写入租约，提交后才调用 StreamBridge；Broker 网络调用不得持有数据库行锁。
8. StreamBridge 返回成功只表示传输被 Binding/Broker 接受，不表示消费者业务已完成。
9. RocketMQ 提供至少一次传输、重新消费和 DLQ；Inbox 唯一约束、领域状态机和条件更新负责业务幂等。
10. 消费者业务写入与 Inbox INSERT/完成状态使用同一本地事务；业务异常必须回滚 Inbox，使 Broker 可以重新投递。
11. Spring Cloud Stream 通用 `maxAttempts` 在当前 RocketMQ 消费基线设为 1，避免框架内存重试与 RocketMQ Broker 重新消费叠加形成不可见重试倍数。
12. Outbox 自身持久化 RETRY/DEAD 状态与 RocketMQ 消费 DLQ 是两个不同故障面：前者处理生产发送失败，后者处理消费者持续失败。

## 5. 决策理由

MOM 的核心需求不是简单“把消息发出去”，而是让数据库事实、事件身份、传输结果和消费结果都可恢复、可审计。Spring Cloud Stream 适合统一 Broker 适配和函数式消费，但本地事务与业务幂等必须保留在平台自己的显式模型中。

通过 EventTransport 隔离，Outbox 发布器不依赖 RocketMQ Client；通过真实 RocketMQ Smoke Test，避免把 Binder 能够编译误判为可靠性已经成立。

## 6. 正向后果

- 领域事件契约不引用 RocketMQ API。
- 服务生产和消费配置方式统一。
- Outbox、Inbox、Broker 重试和 DLQ 的职责清晰。
- 可以在测试中使用内存 EventTransport，并在 CI 中使用真实 RocketMQ。
- 后续若增加 Kafka Binder，事件契约和本地一致性模型可以复用。
- Spring Cloud Stream Actuator bindings 端点可用于检查 Binding 状态。

## 7. 负向后果与技术债

- 需要持续跟踪 Spring Cloud Stream、Spring Cloud Alibaba 和 RocketMQ Client 的版本兼容矩阵。
- RocketMQ 特有 Header、Tag、顺序消息和延时消息需要受控扩展，不能假设 Binder 完全屏蔽差异。
- 当前事件负载以 JSON 文本保存，后续需要增加 Schema Registry 或契约兼容检查。
- Outbox Publisher 的调度、清理、指标和人工重放仍需后续完善。
- DLQ 自动告警、查询和重放界面不在本切片范围内。

## 8. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| Binder 返回成功但应用在状态更新前崩溃 | 消费者使用 Inbox 幂等，Outbox 可重复发布同一 eventId |
| Broker 不可用导致数据库事务阻塞 | 业务事务只写 Outbox；网络发送由事务外发布器执行 |
| 多实例重复领取同一 Outbox | PostgreSQL `FOR UPDATE SKIP LOCKED`、租约所有者和 CAS 状态更新 |
| 内存重试与 Broker 重试叠加 | Stream Consumer `maxAttempts=1`，重试交给 RocketMQ 重新消费 |
| 消费成功但确认失败产生重复投递 | Inbox 唯一约束和领域幂等保证结果不重复 |
| Poison Message 无限循环 | 配置 RocketMQ 最大重新消费次数并进入 `%DLQ%<consumer-group>` |
| 动态 Binding 无限制增长 | 生产端只允许使用配置中的稳定 Binding 名称 |
| 事件负载泄露敏感信息 | Payload、日志和 last_error 禁止保存 Token、密钥和未脱敏数据 |

## 9. 实施约束

- `*-api` 和领域模型不得依赖 `StreamBridge`、RocketMQ Client 或 Binder 类型。
- `mom-messaging` 可以依赖 Spring Cloud Stream，但事件信封必须保持 Broker 无关。
- `mom-outbox` 不依赖 RocketMQ Client，只依赖 `EventTransport`。
- Producer 使用同步发送结果决定是否尝试标记 SENT；Binder 内部发送重试默认关闭，由 Outbox 持久化重试统一控制。
- Consumer 必须声明稳定 group；禁止匿名生产消费组用于正式业务。
- 事件 ID 在 Outbox 首次写入时生成，发布重试不得重新生成。
- 未知且当前消费者不关心的事件应被明确忽略或通过订阅过滤排除，不能全部送入 DLQ。
- Poison Message、重复投递、Broker 中断、发送成功后状态更新失败必须进入自动化测试。
- Broker 地址、Topic 和 Group 必须通过环境变量覆盖，禁止写死生产地址。

## 10. 验证方式

- 单元测试：事件信封校验、退避计算和传输失败状态。
- PostgreSQL 集成测试：业务写入与 Outbox 同事务提交/回滚。
- PostgreSQL 集成测试：Inbox 重复三次只执行一次业务动作，业务失败时 Inbox 回滚。
- 并发测试：两个发布实例不能同时持有同一未过期租约。
- RocketMQ Smoke Test：MDM 经 StreamBridge 发布，Integration 函数式 Consumer 接收。
- 故障测试：Broker 停止期间业务事务仍提交 Outbox，Broker 恢复后事件最终发送。
- 重复测试：同一 eventId 再发布只保留一条 Inbox 和一个业务结果。
- DLQ 测试：持续失败事件超过重新消费次数后进入消费者组 DLQ。
- 回归测试：无数据库或无 RocketMQ 的默认关闭启动路径继续通过。

## 11. 替代与回滚条件

出现以下情况时创建新的 ADR：

- RocketMQ Binder 无法继续支持目标 Spring Boot / Spring Cloud 版本。
- 需要使用 Binder 未暴露且无法安全扩展的 RocketMQ 关键能力。
- 采用 Kafka、Pulsar 或 CDC 平台作为新的默认事件传输。
- 引入统一 Schema Registry、CloudEvents 或企业事件总线规范。
- 生产数据证明应用 Outbox 发布器无法满足吞吐，需要 CDC Outbox Relay。

## 12. 参考资料

- Spring Cloud Stream Reference：`https://docs.spring.io/spring-cloud-stream/reference/`
- Spring Cloud Stream StreamBridge：`https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/producing-and-consuming-messages.html`
- Spring Cloud Alibaba RocketMQ Binder：`https://sca.aliyun.com/en/docs/2025.x/user-guide/rocketmq/advanced-guide/`
- Spring Cloud Alibaba RocketMQ Starter：`https://github.com/alibaba/spring-cloud-alibaba/tree/2025.x/spring-cloud-alibaba-starters/spring-cloud-starter-stream-rocketmq`
- Apache RocketMQ：`https://rocketmq.apache.org/`
