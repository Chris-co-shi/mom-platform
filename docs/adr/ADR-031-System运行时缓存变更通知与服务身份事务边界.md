# ADR-031：System 运行时缓存、变更通知、服务身份与事务边界

- 状态：Accepted
- 日期：2026-08-01
- 决策人：Chris
- 关联 Slice：P1.6 S18
- 关联 ADR：ADR-009、ADR-016、ADR-026～ADR-030

## 1. 背景

S17 已完成 Application Catalog、Navigation Draft、不可变发布快照、IAM Permission Code Reference 和 JWT Authority Runtime 过滤。S18 需要补齐：

- Catalog 发布期 IAM Permission 权威校验；
- Catalog/I18n/Parameter/Dictionary Runtime Cache；
- 可靠变更通知和多实例 Cache 失效；
- Redis、IAM、RocketMQ 故障降级；
- Permission Reference 定期对账；
- 跨服务真实验证。

这些能力不能把 IAM、Redis、RocketMQ 或 Feign 错误纳入 System PostgreSQL 本地事务，也不能因出现 Feign 就默认使用 Seata。

## 2. 事务决策

### 2.1 不使用 Seata

System → IAM 是只读 Permission Code 权威校验，不存在 IAM 数据库写分支。S18 禁止新增：

- `@GlobalTransactional`；
- System `mom-seata` 生产依赖；
- XID 传播；
- `undo_log`；
- Seata DataSource Proxy。

Seata 无法消除“校验成功后 Permission 随即变化”的最终一致性窗口，也不能替代 Runtime Fail Closed、对账和未来状态事件。

### 2.2 Feign 位于本地事务之外

Catalog Publish/Rollback 使用：

```text
非事务 Publish Orchestrator
→ 构建候选 Snapshot 与 Permission Set
→ 事务外 IAM 批量校验
→ 独立 Transactional Commit Service
→ 事务内重新读取并校验 Version/Checksum/Permission Set
→ Release + 发布指针 + Outbox 原子提交
```

IAM HTTP Adapter 使用 `Propagation.NEVER` 或等价门禁，防止活动数据库事务中执行远程调用。Feign 配置有限连接/读取超时和无自动写重试，不得 fallback 为“全部有效”。

### 2.3 System 聚合原子边界

单 PostgreSQL 本地事务包含：

```text
SystemApplication 发布指针与 Version
+ SystemCatalogRelease
+ mom_outbox_event
```

Redis Cache、RocketMQ 发送、IAM Feign、其他服务数据库不属于本地事务。

任何 Release、Outbox 或发布指针写入失败，三者必须整体回滚。

## 3. IAM Permission Reference 契约

IAM 提供服务间批量只读 API：

```http
POST /api/iam/internal/permission-references/validate
```

请求最多 1000 个规范化 Permission Code，响应仅返回：

- `ENABLED`；
- `DISABLED`；
- `UNKNOWN`。

不得返回 Permission ID、Role、Assignment、用户或数据库审计字段。

调用方使用 OAuth2 `client_credentials` 和精确 Scope：

```text
iam.permission-reference.read
```

服务 Client Secret 只来自环境 Secret，禁止进入 Git、普通 Nacos 配置、日志或错误响应。

## 4. Cache 决策

PostgreSQL 继续是唯一权威。S18 缓存：

- Catalog 不可变发布 Snapshot；
- Dynamic I18n 不可变 Locale Release；
- Parameter 有效解析结果；
- Dictionary Active List / Resolve Result。

S18 不缓存：

- 最终 `/catalog/me` Authority 过滤响应；
- IAM Permission Validation 结果；
- User Preference。

Cache Key 必须带环境、能力、契约版本和稳定业务 Code；Value 使用显式版本 JSON，不使用 Java 原生序列化；所有 Key 有 TTL 和抖动。

Catalog/I18n Runtime 仍先读取 PostgreSQL 权威头信息以保证 Application/Resource 禁用立即生效。Redis 不可用、内容损坏或 checksum 不一致时回源 PostgreSQL。PostgreSQL 不可用时返回 503，不静默返回旧 Cache。

## 5. 变更通知

使用现有 `EventEnvelope`、Outbox、Spring Cloud Stream 和 RocketMQ。

事件：

- `system.catalog.published`；
- `system.catalog.status-changed`；
- `system.i18n.published`；
- `system.i18n.status-changed`；
- `system.parameter.changed`；
- `system.dictionary.changed`。

Payload 只包含稳定 Code、版本、checksum、状态和 changeKind，不包含参数值、翻译正文、字典 Label、用户 ID、Token、Secret 或数据库 Entity。

业务变更与 Outbox INSERT 同一本地事务；Broker 网络发送在事务外。提交后本实例 best-effort Evict，Outbox 事件通过 System 自消费和 Inbox 幂等完成多实例失效。TTL 是最终修复边界，不替代通知。

## 6. Permission 生命周期

Publish/Rollback 必须同步权威批量校验。System 每 10 分钟对已发布 Permission Reference 进行只读对账：

- UNKNOWN/DISABLED 只记录低基数指标和脱敏告警；
- 不自动修改、取消发布或复制 IAM 状态；
- Runtime 继续按当前 JWT Authority Fail Closed。

IAM 当前没有正式 Permission 启停/删除生产用例，因此 `iam.permission.changed` 事件精确 Deferred，不能伪造无真实生产者的事件。

## 7. 数据模型

System Flyway V9 创建：

- `mom_outbox_event`；
- `mom_inbox_event`。

两表与 System 业务表共用唯一 DataSource 和事务管理器，不建立物理外键，不强制继承普通业务 `BaseEntity`，由 `mom-outbox` Framework 使用。

## 8. Package 与依赖

IAM：

```text
api contract
client Feign
application.permissionreference
web.internal.permissionreference
infrastructure.persistence.query
security service client registration
```

System：

```text
application.catalog orchestrator/commit service/port
application.runtime ports
infrastructure.http.iam
infrastructure.cache.redis
infrastructure.messaging
configuration
```

System 只能依赖 `mom-iam-client`，禁止依赖 `mom-iam-server`。Application 不依赖 Feign、RedisTemplate 或 StreamBridge；Domain 不依赖网络、缓存、消息或 Spring Transaction API。

## 9. 错误与降级

- Permission UNKNOWN/DISABLED：409 `invalid_permission_reference`；
- IAM 超时/不可用：503 `permission_authority_unavailable`；
- IAM 协议非法：502 `permission_authority_protocol_error`；
- Redis 故障：回源 PostgreSQL；
- PostgreSQL 故障：503，不返回不可信 stale Cache；
- RocketMQ 不可用：业务与 Outbox 正常提交，Publisher RETRY/DEAD；
-重复消息：Inbox 去重；
- Cache Evict 失败：不反向改变数据库事务结果。

## 10. 测试门禁

必须证明：

- Feign 调用时不存在活动 Spring 数据库事务；
- Feign 超时不持有 System 本地事务；
-远程校验后 Draft 变化返回 409 且无 Release/Outbox；
- Release、发布指针、Outbox 任一失败时整体回滚；
- Redis 清理失败不回滚数据库；
- Broker 不可用时业务与 Outbox提交；
- Inbox 重复和失败语义正确；
- System 不出现 `@GlobalTransactional` 或 `mom-seata`；
-真实 PostgreSQL、Redis、RocketMQ、IAM/System 服务身份和跨服务调用验证通过。

## 11. Deferred

S18 精确 Deferred：IAM Permission 正式生命周期变更事件。其退出条件是 IAM 存在真实 Permission 启停/删除用例及相应 Outbox 生产者。

mom-web/mom-mobile Route Registry 和正式客户端接入仍进入后续独立任务/S19-A。
