# AI 事务设计与执行前置协议

本规范适用于所有人工开发者、AI 编码工具和自动化 Agent。任何新增或实质修改 Application Service、Repository 写操作、聚合发布/回滚、Feign/HTTP、Outbox/Inbox、MQ、Redis/Cache、异步任务、多数据源或 Seata 前，必须先完成事务设计并接受人工 Review。

## 1. 编码前事务矩阵

每个写用例必须说明：

| 项目 | 必须说明 |
|---|---|
| 用例入口 | Controller、Application Service、Consumer、Job 或其他入口 |
| 事务入口 | 哪个 public 方法开启事务 |
| 本地权威数据 | 本事务修改哪些表和聚合 |
| 原子提交范围 | 哪些写操作必须同时成功或回滚 |
| 非事务资源 | Redis、MQ、Feign、文件、设备或外部系统 |
| 传播行为 | REQUIRED、REQUIRES_NEW、NEVER 等 |
| 隔离与锁 | READ COMMITTED、行锁、CAS 或唯一约束 |
| 并发控制 | Version、状态条件、业务唯一键或数据库锁 |
| 幂等策略 | 业务幂等键、Inbox、状态机或唯一约束 |
| 回滚规则 | 哪些异常回滚及 HTTP 错误语义 |
| 提交后动作 | Cache Evict、事件发送、通知或审计 |
| 故障恢复 | 重试、Outbox、Inbox、对账、补偿和人工处理 |
| 测试证据 | 如何证明提交、回滚和外部调用位置正确 |

如果事务边界影响表结构、聚合所有权、跨服务一致性或生产故障语义，未经 Chris 明确批准不得编码。

## 2. 本地事务默认规则

默认模型：

```text
Controller
→ Application Service public method
→ Domain / Repository
→ 单权威 DataSource 本地事务
```

强制要求：

1. 业务事务默认位于 Application Service public 方法；
2. 默认传播 `REQUIRED`，默认隔离 `READ COMMITTED`；
3. Controller 不开启业务事务，Domain 不依赖 Spring Transaction API；
4. Repository 不得擅自扩大或切断 Application 事务；
5. 不得依赖 private 方法、同类 self-invocation 或未经过代理的方法作为事务入口；
6. 一个领域服务默认只有一个权威 DataSource 和一个事务管理器；
7. 聚合内原子数据由一个明确事务入口统一提交；
8. 不得吞掉 affected rows 为零、Version CAS 或唯一冲突后提交部分状态；
9. 不得为了测试通过把事务上移到 Controller、下沉到 Mapper/Repository 或给整个类添加宽泛事务；
10. `REQUIRES_NEW` 不得用于掩盖主事务失败。

## 3. 聚合事务边界

AI 必须先确认聚合边界，再设计事务。典型发布事务可以包含：

```text
聚合根发布状态
+ 不可变 Release/Snapshot
+ 同事务 Outbox Event
= 单 PostgreSQL 本地原子提交
```

Redis、RocketMQ、Feign/HTTP、其他服务数据库、文件、设备、人工和外部系统不得伪装成本地事务资源。

## 4. Feign 与同步远程调用

Feign、HTTP、设备、文件上传等网络操作默认禁止在活动数据库事务中执行。

存在同步权威校验时，默认结构为：

```text
非事务 Orchestrator
→ 事务外调用远程权威服务
→ 独立 Transactional Commit Service
→ 事务内重新读取并检查 Version、状态、Hash 或候选集合
→ 本地原子提交
```

要求：

1. Orchestrator 与 Transactional Commit Service 是两个独立 Spring Bean；
2. 禁止依赖 self-invocation；
3. 远程 Adapter 使用 `Propagation.NEVER` 或等价门禁；
4. Feign 必须有有限连接/读取超时；
5. 写请求默认不自动重试；
6. 远程失败不得 fallback 为虚假成功；
7. 远程校验后，本地提交前必须重新检查聚合版本和候选内容；
8. 必须明确跨服务校验与本地提交之间的最终一致性窗口；
9. 不得声称普通 Feign 查询和本地写入构成强一致事务。

## 5. Seata 使用门禁

出现 Feign、多个服务或多个数据库不代表必须使用 Seata。默认 `Seata = Disabled`。

只有满足全部条件才允许评估 Seata AT：

1. 至少两个真实数据库写分支；
2. 每个分支都是短同步数据库操作；
3. 数据库回滚与业务回滚语义一致；
4. 不存在设备、文件、人工、MQ 或外部现实副作用；
5. 事务时长符合当前基线；
6. 参与服务、数据库和 SQL 类型明确；
7. 已有 Accepted ADR 批准；
8. 有真实 PostgreSQL + Seata Server 故障测试；
9. XID 缺失、不一致或 TC 不可用时 Fail Closed；
10. 业务接受 TC、RM、Undo Log 的可用性和运维成本。

未经 Accepted ADR，禁止新增 `@GlobalTransactional`、`mom-seata` 生产依赖、`undo_log`、手工 XID 传播或 Seata DataSource Proxy。

只读远程校验、缓存更新、消息发布、长流程、人工审批、设备动作、文件交换、外部调用和等待回调不得使用 Seata。Seata 不能替代本地事务、Outbox/Inbox、幂等、状态机、对账、补偿或 DEAD/DLQ。

## 6. Outbox 与 Inbox

业务写入并可靠发事件：

```text
业务数据写入 + Outbox INSERT
= 同一 DataSource、事务管理器和本地事务
```

Broker 网络调用在事务提交后执行。消费者写入：

```text
Inbox INSERT + 消费业务写入
= 同一本地事务
```

Inbox 仅解决事件级重复，不能替代领域状态机、Version CAS、业务唯一键和条件更新。

## 7. Redis 与 Cache

Redis Cache 是可重建 Projection，不是权威数据。正确顺序：

```text
数据库权威写入 + Outbox
→ 本地事务提交
→ afterCommit best-effort Cache Evict
→ 变更事件
→ 多实例幂等失效
```

Cache 失败不得回滚已成功提交的数据库事实，也不得返回伪造成功。必须定义 TTL、Key 版本、回源/Fail Closed、指标和故障测试。

## 8. 传播、异常与回滚

- `REQUIRES_NEW` 仅用于确需独立提交的技术状态，并说明主事务失败后的残留；
- `NESTED` 仅在 JDBC Savepoint 与驱动语义验证后使用；
- `NEVER` 推荐用于禁止事务内执行的 Feign/HTTP Adapter；
- RuntimeException/Error 默认回滚；checked exception 回滚必须显式设计；
- 业务冲突映射 409，临时依赖故障映射 503/504，协议错误映射 502，未知错误回滚并映射 500；
- 禁止捕获异常后返回成功、提交部分状态或泄露 SQL、Token、Secret、表名和完整 Payload。

## 9. 强制测试证据

涉及事务的改动按场景至少覆盖：

1. 正常提交和业务异常整体回滚；
2. affected rows 为零、Version CAS、唯一冲突；
3. Outbox INSERT 失败时业务回滚，业务失败时 Outbox 不存在；
4. Feign 调用时不存在活动数据库事务；
5. Feign 超时期间不持有本地事务；
6. 远程校验后聚合变化时拒绝提交；
7. Cache Evict 失败不改变数据库事务结果；
8. Broker 不可用时业务与 Outbox按设计提交；
9. Inbox 重复不重复执行业务，消费失败时 Inbox 与业务一起回滚；
10. 禁止未经批准出现 `@GlobalTransactional` 或新增 `mom-seata`；
11. 事务入口通过真实 Spring Proxy 验证；
12. PostgreSQL Testcontainers 验证真实提交、回滚、锁和并发。

仅用 Mockito 验证方法调用次数不足以证明事务正确。

## 10. AI 验收报告

最终报告必须列出：事务入口、传播、事务内表、事务外资源、聚合原子边界、远程调用位置、Outbox/Inbox、Seata 是否使用及理由、并发幂等、故障恢复和测试证据。

禁止使用“已添加事务”“保证一致性”“使用 Seata 保证分布式事务”“增加重试提高可靠性”等模糊结论。无法确认事务边界时必须停止编码并进入人工 Review。