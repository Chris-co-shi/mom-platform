# MOM System Platform

`mom-system-platform` 已完成 S12 技术骨架与 P1.6 S13 GLOBAL/APPLICATION 类型化非敏感参数。S14 字典及后续偏好、目录、菜单能力仍为 Not Started。

## 模块职责

| 模块 | S13 职责 |
|---|---|
| `mom-system-api` | `ParameterScopeType`、`ParameterValueType` 与 `ResolvedSystemParameter` 稳定只读契约 |
| `mom-system-client` | 仅保留调用边界；当前无真实调用方，不提前创建 Feign Client |
| `mom-system-server` | 参数领域规则、事务用例、`mom_system` 持久化、管理/解析 API 与 JWT 安全 |

依赖方向固定为：

```text
caller → mom-system-client → mom-system-api
mom-system-server → mom-system-api
web → application → domain
infrastructure → domain/application ports
```

## 参数能力

- Scope 仅允许 `GLOBAL`、`APPLICATION`；GLOBAL 使用空 `scopeCode`，APPLICATION 使用小写 kebab-case `applicationCode`。
- Value Type 仅允许 `STRING`、`INTEGER`、`DECIMAL`、`BOOLEAN`、`JSON`；跨服务继续返回类型与规范字符串。
- 同一 Key 的 GLOBAL 与 APPLICATION 必须保持相同 Value Type；同 Key 写事务由 PostgreSQL 事务级锁串行化。
- enabled APPLICATION 优先；禁用或不存在时回退 enabled GLOBAL；均不存在返回 404。
- 创建、更新与启停使用本地事务；更新与启停都必须携带 Version，冲突返回 409。
- 不提供 DELETE；不实现历史版本表。

## 安全与数据边界

- `mom_system.system_parameter` 是参数唯一写入权威，不访问 IAM/MDM 或其他 Schema，不建立跨 Schema FK/JOIN。
- `system:parameter:read` 保护管理查询与有效值解析；`system:parameter:write` 保护创建、更新和启停。这些 Code 仅引用 IAM Permission，不在 System 保存定义或分配。
- Key 按 Segment 拒绝 password、secret、token、credential、private-key、client-secret、access-key、api-key 等明显敏感语义；System Parameter 不允许保存 Secret 或 Credential。
- `mom-security` 传递的 Redis 仅用于现有 revoked sid 检查；Parameter Domain 不依赖 Redis，PostgreSQL 仍是唯一参数权威，S13 不实现参数缓存。
- 不依赖 `mom-iam-server` 或其他领域 Server，不引入 MQ、Outbox、Inbox 或 Seata。

## 精确门禁

POM XML 白名单只允许 System API、WebMVC、Security、Data、Tracing、Metrics、Nacos、Lombok 与测试基础设施；`mom-iam-server` 和 `mom-mdm-api` 负例必须失败。ArchUnit 验证 Domain 无 Spring/MyBatis、Application 无 Mapper/Entity、Web 无 Domain/Infrastructure、持久化类型只在 Infrastructure，并继续禁止 Dictionary、Preference、Catalog、Menu、Navigation 及 IAM 对象。

## 当前未实现

- Dictionary、Dictionary Item；
- User Preference、Locale/Timezone/Theme 与视图设置；
- Application Catalog、Menu、Navigation；
- Dynamic I18n、Audit Projection；
- Secret 管理、配置中心替代、跨服务推送；
- Redis 参数缓存、MQ 参数广播；
- IAM 数据迁移或 Permission 存储。

## 本地验证

需要 JDK 25 与 Maven 3.9.9 或更高版本：

```bash
bash scripts/codex-mvn-test.sh \
  -pl mom-system-platform/mom-system-server \
  -am clean verify

bash scripts/codex-mvn-test.sh \
  -pl mom-architecture-tests \
  -am test

BASE_REF=c8ffa6a68d93f3471bc2d85bdab4aa80950dbd0a \
  bash scripts/codex-verify-changed.sh

bash scripts/codex-mvn-test.sh clean verify
```

## 回滚

需要整体撤销时，在独立 Review 后对 S13 本地集中提交执行普通 `git revert`，同时回退参数代码、`mom_system` V1 Migration、门禁与阶段文档；已执行的数据库回滚必须另行评估数据保留，禁止修改历史 Migration 或使用 reset/rebase 改写阶段历史。
