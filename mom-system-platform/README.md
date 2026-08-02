# MOM System Platform

`mom-system-platform` 已完成 P1.6 S12～S18：技术骨架、非敏感类型化参数、受限通用字典、Dynamic I18n、用户偏好、Application Catalog，以及 Runtime Cache、变更通知、Outbox/Inbox、RocketMQ 和 IAM Permission Reference 生命周期闭环。

当前状态：

```text
S18：Completed with Deferred items
S19-A：Not Started
PR #33：Open / Draft / 未合并
```

## 1. 模块职责

| 模块 | 职责 |
|---|---|
| `mom-system-api` | 参数、字典、偏好和不可执行 Runtime Catalog 稳定契约 |
| `mom-system-client` | System 对外调用边界；当前不承载业务实现 |
| `mom-system-server` | System 领域规则、事务用例、PostgreSQL 权威持久化、Runtime Cache、事件、管理/Runtime API 与安全 |

依赖方向：

```text
caller → mom-system-client → mom-system-api
mom-system-server → mom-system-api
web → application → domain
infrastructure → application/domain ports
```

## 2. 当前业务能力

### 2.1 Parameter

- Scope：`GLOBAL / APPLICATION`；
-类型：`STRING / INTEGER / DECIMAL / BOOLEAN / JSON`；
-禁止 Secret、Credential、Token、Password 等敏感语义；
- Application 优先、GLOBAL 回退；
- Version 乐观并发；
- PostgreSQL 权威；
- Runtime 有效解析结果可缓存。

### 2.2 Dictionary

-非权威、受限通用字典；
-稳定 `dictionaryCode + itemCode`；
- Active List 与 Disabled Compatibility；
-禁止表达 IAM/MDM/WMS/EAM 权威对象、业务状态机、Tree、Metadata 或任意扩展属性；
- Runtime Active List / Resolve Result 可缓存。

### 2.3 Dynamic I18n

- `zh-CN / en-US`；
- Draft、双 Locale 不可变 Release、Publish、Rollback；
-完整 JSONB Snapshot、checksum、ETag/304；
- Resource disabled 是即时 Kill Switch；
- Runtime 单 Locale Release 可缓存；
- Web/Mobile 正式接入尚未完成。

### 2.4 User Preference

- Locale、displayTimezone、themeMode、density、pageSize；
-受限 Column/Sort/Filter View；
-只使用已验证 JWT `sub` 隔离当前用户；
-不进入 JWT，不参与 Authorization；
- **S18 明确不缓存 Preference**，不是遗漏。

### 2.5 Application Catalog

- Application、Navigation Draft、不可变 Release；
- `WEB / MOBILE` Channel；
- `GROUP / ROUTE` 节点；
-只保存 Route、Icon、I18n、Permission 稳定 Reference；
-不保存 Path、Component、Layout、JavaScript、HTML、动态 import 或远程模块 URL；
- Publish/Rollback 使用单调版本和完整 Snapshot；
- Runtime 根据当前 JWT Permission Authority 精确过滤；
- Application disabled 是即时 Kill Switch；
-发布 Snapshot 可缓存。

## 3. S18 IAM Permission Reference

IAM 提供：

```http
POST /api/iam/internal/permission-references/validate
```

响应状态：

- `ENABLED`；
- `DISABLED`；
- `UNKNOWN`。

System 使用：

```text
grant_type = client_credentials
scope = iam.permission-reference.read
client_id = mom-system-server
```

服务 Secret 只由环境 Secret 注入。System 不保存 Permission 定义、Role、Assignment、用户授权结果或 OAuth Client 安全配置。

## 4. Catalog 发布事务

```text
非事务候选构建
→ 事务外 IAM 权威校验
→ Transactional Commit Service
→ 事务内重新校验 Draft / Version / Permission Set
→ Release + Published Pointer + Outbox 原子提交
```

边界：

- Feign Adapter 使用 `Propagation.NEVER`；
- Redis、RocketMQ 和 IAM HTTP 不进入数据库事务；
-不使用 Seata；
-无 `@GlobalTransactional`；
-远程校验后 Draft 改变时返回 409；
- IAM 不可用时发布返回 503，不 fallback 为全部有效。

## 5. Runtime Cache

| 能力 | Cache | 权威与降级 |
|---|---|---|
| Catalog | 不可变 Release Snapshot | 先读 PostgreSQL Header；Redis 故障回源 |
| Dynamic I18n | 单 Locale Release | 先读 Resource Header；checksum/版本不符回源 |
| Parameter | 有效解析结果 | Redis 故障回源 PostgreSQL |
| Dictionary | Active List / Resolve Result | 先读 Dictionary Header；Redis 故障回源 |
| Preference | 不缓存 | PostgreSQL |
| IAM Permission Validation | 不缓存 | IAM 实时权威 |
| `/catalog/me` 过滤结果 | 不缓存 | 当前 JWT Authority |

PostgreSQL 故障时不只凭 Redis 返回 stale 数据。

## 6. 变更通知

事件：

- `system.catalog.published`；
- `system.catalog.status-changed`；
- `system.i18n.published`；
- `system.i18n.status-changed`；
- `system.parameter.changed`；
- `system.dictionary.changed`。

链路：

```text
业务写事务
→ 业务事实 + mom_outbox_event
→ 事务外 RocketMQ
→ System Consumer
→ mom_inbox_event 幂等
→ Redis Cache Evict
```

Payload 不包含参数值、翻译正文、字典 Label、User、Token 或 Secret。

## 7. 数据库

System Flyway 当前到 V9：

- V1：Parameter；
- V2：Dictionary；
- V3：Dynamic I18n；
- V4～V6：实体、无业务 FK 与 Snapshot 语义治理；
- V7：User Preference / View；
- V8：Application Catalog / Navigation / Release；
- V9：Outbox / Inbox。

约束：

-单服务单 DataSource；
-业务/跨 Schema 物理 FK = 0；
-历史 Migration 不修改；
- Outbox Claim Index 和 Inbox Identity Unique 已验证。

## 8. 故障语义

| 故障 | 行为 |
|---|---|
| IAM 不可用 | Publish/Rollback 503；对账失败不影响 Readiness |
| Redis 不可用 | Runtime 回源 PostgreSQL |
| PostgreSQL 不可用 | 不返回不可信 stale Cache |
| RocketMQ 不可用 | 业务与 Outbox 提交；Publisher RETRY |
| Broker 恢复 | Outbox 自动补发 |
| 重复事件 | Inbox 幂等 |
| 毒消息 | 重试耗尽进入 DLQ |
| Cache Evict 失败 | 不回滚数据库，由重试与 TTL 修复 |

## 9. 配置边界

关键环境变量：

```text
SYSTEM_RUNTIME_CACHE_ENABLED
MOM_ENVIRONMENT
SYSTEM_RUNTIME_EVENT_CONSUMER_ENABLED
SYSTEM_STREAM_FUNCTION_DEFINITION
SYSTEM_RUNTIME_EVENT_TOPIC
SYSTEM_RUNTIME_EVENT_CONSUMER_GROUP
ROCKETMQ_NAME_SERVER
OUTBOX_PUBLISHER_ENABLED
IAM_PERMISSION_REFERENCE_URL
IAM_PERMISSION_REFERENCE_OAUTH2_ENABLED
IAM_TOKEN_URI
IAM_SYSTEM_CLIENT_ID
IAM_SYSTEM_CLIENT_SECRET
```

生产 Secret 不得写入 `application.yml` 或普通 Nacos 配置。

## 10. 验证

```bash
bash scripts/codex-mvn-test.sh clean verify

bash .github/scripts/system-postgresql-smoke.sh

bash .github/scripts/system-rocketmq-runtime-event-smoke.sh
```

CI 还会验证：

-真实 `client_credentials`；
- IAM 内部 Permission API；
- System Feign OAuth2；
-启动对账；
- IAM 停机后 System Readiness；
-真实 PostgreSQL、Redis、RocketMQ、重试、Inbox 和 DLQ。

## 11. 当前未实现

- IAM Permission 正式启停/删除事件；
- mom-web / mom-mobile Route Registry 正式接入；
- Dynamic I18n 客户端正式接入；
- Default/Last Factory、Dashboard/Favorites；
- Mobile Logout 服务端撤销；
-正式 Client/Redirect/App Link；
- L4/L6 和真实部署环境最终验收。

上述事项进入 S19-A、后续独立客户端任务或未来 IAM 生命周期 Slice。

## 12. 权威文档

- [ADR-031](../docs/adr/ADR-031-System运行时缓存变更通知与服务身份事务边界.md)
- [S18 权威报告](../docs/engineering/P1.6-S18-System运行时缓存变更通知与IAM权限引用生命周期报告.md)
- [P1.6 实施进度](../docs/plans/P1.6-实施进度.md)
- [P1.6 治理计划](../docs/plans/P1.6-IAM与System平台治理计划.md)

## 13. 回滚

代码回滚使用普通 `git revert`。已执行 V9 后不得删除或修改历史 Migration；应评估保留 Outbox/Inbox 表、停用 Publisher/Consumer 和兼容已有事件数据。禁止 reset、rebase 或 force-push 改写长期阶段历史。