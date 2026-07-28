# MOM 事务与跨服务一致性规范

## 1. 默认选择树

1. 单服务数据库写入：单权威 DataSource + Spring 本地事务。
2. 写库并可靠发事件：业务写入 + Outbox INSERT 同一本地事务，提交后发布。
3. 消费事件并写库：Inbox INSERT + 业务写入同一本地事务，重复由 Inbox 与领域状态机共同承受。
4. 跨服务长流程、设备、人工或外部系统：状态机、事件、幂等、对账和人工补偿。
5. 仅 ADR-009/016 明确允许的短同步、参与者和数据库边界确定的场景才可评估 Seata AT。

不得把网络重试、Redis 锁或 Seata 当成默认分布式一致性方案。

## 2. Spring 本地事务

- 业务事务默认放在 Application Service 公共方法；Controller 不开启业务事务，Domain 不依赖 Spring
  Transaction API。
- Spring 声明式事务通常通过代理拦截外部调用；private 方法和同类 self-invocation 不应被依赖为事务入口。
- 默认传播 `REQUIRED`、数据库默认隔离 `READ COMMITTED`。提高隔离级别必须有并发证据和测试。
- `readOnly=true` 是优化提示，不是授权或写保护边界；异步线程不继承调用线程事务。
- Repository 可使用短技术事务，但不得静默扩大或切断业务原子边界。

### 2.1 回滚与传播

- 明确异常分类：业务冲突映射 409；临时依赖故障映射 503/504；未预期错误回滚并映射 500。
- 默认 RuntimeException/Error 回滚。需要 checked exception 回滚或特定异常不回滚时必须显式配置并测试。
- 禁止吞异常后提交部分状态，或捕获异常后返回业务成功。
- `REQUIRES_NEW` 只用于确需独立提交的技术状态，并记录主事务失败后的残留后果；不得用来掩盖主事务失败。
- `NESTED` 仅在 JDBC Savepoint 语义和驱动支持明确时使用。

## 3. 事务内外部动作

普通数据库事务内禁止直接调用 RocketMQ、长时间 HTTP/Feign、设备或人工等待、sleep、无界重试、
大文件上传和长轮询。外部动作在提交后执行；可靠发布通过 Outbox。若业务必须同步调用外部依赖，先缩短
数据库临界区并明确失败补偿，不得持锁等待网络。

## 4. Outbox、Inbox 与重试

- Event ID 在首次写入 Outbox 时生成，所有发布重试复用同一 ID 和负载。
- Publisher 领取与状态更新使用短事务；Broker 网络调用在事务外。
- 发送成功但状态更新失败允许重复发布；Consumer 使用稳定组、Inbox 唯一约束和领域条件更新承受重复。
- Outbox DEAD 与 Broker DLQ 是不同故障面，分别监控、告警和处置。
- Payload、错误摘要和日志不得包含秘密或未脱敏敏感信息。
- Inbox 唯一约束只解决事件级重复，不能替代库存、工单、质量或权限状态机。

## 5. Seata 边界

- 默认关闭；不因模块存在而扩大使用。
- 仅限短同步事务，当前 AT 基线不超过 10 秒、默认不超过两个数据库分支；放宽需要新 ADR 和故障测试。
- 每个 RM 仍使用显式 Spring 本地事务，业务表与 `undo_log` 共用唯一 DataSource。
- XID 缺失、不一致或 TC 不可用时 fail closed；不得降级为普通本地写入或伪造成功。
- 全局事务内禁止 RocketMQ、设备动作、人工等待、长轮询和不可逆外部副作用。
- Seata 不能替代 Outbox/Inbox、幂等、DEAD/DLQ、状态机和对账。

## 6. Spring Authorization Server JDBC Store

`JdbcRegisteredClientRepository`、`JdbcOAuth2AuthorizationService` 和
`JdbcOAuth2AuthorizationConsentService` 是 IAM 内的标准协议持久化边界：

- 保留官方 JDBC Store，不改写为 MyBatis-Plus Repository；
- 官方表位于 IAM Schema，其他服务不得查询；
- 不强加 `BaseEntity`、通用逻辑删除、MOM 审计列、乐观锁或领域外键；
- 协议表迁移跟随当前 SAS 官方 Schema 与项目兼容要求；修改只能在后续 IAM Slice 完成协议级测试后进行。

## 7. 官方事实来源

- [Spring 声明式事务注解](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [Spring 事务传播](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [Spring 回滚规则](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)
- [PostgreSQL 17 Transaction Isolation](https://www.postgresql.org/docs/17/transaction-iso.html)
- [Spring Authorization Server 核心组件](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html)

