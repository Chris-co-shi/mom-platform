# Mini Auth 管理员初始化

- 状态：Current for local/dev baseline
- 生效日期：2026-09-03
- 当前事实来源：`fix/mini-auth`
- 模块：`mom-auth-platform/mom-auth-server`
- Migration：`db/migration/auth/V2__seed_platform_admin.sql`
- 数据模型：[Mini Auth 数据库与代码分层](../architecture/Mini-Auth数据库与代码分层.md)

## 1. 当前实现

Mini Auth V1 当前通过 Flyway V2 建立最小平台管理员：

```text
User: admin
Role: PLATFORM_ADMIN
Relation: admin → PLATFORM_ADMIN
```

该脚本只用于当前第一版开发和联调，让 Auth 登录、Token、Gateway、Resource Server 闭环能够快速验证。

## 2. 权限语义

`PLATFORM_ADMIN` 只是普通 Role Code，不存在角色名绕过权限检查。未来 Permission 必须显式写入 `auth_permission` 与 `auth_role_permission`。

## 3. 密码边界

当前 V2 Migration 中包含开发初始化密码摘要，因此当前管理员 seed 不得直接视为生产环境安全初始化方案。

约束：

- Flyway 中不得保存明文密码；
- API、日志、Trace、审计事件不得输出密码或 password hash；
- 当前开发初始密码在完成登录验证后应立即修改；
- 正式部署前必须重新评估管理员 bootstrap，优先使用部署 Secret / 一次性环境变量 / 受控初始化命令；
- 正式生产基线冻结后，应通过新的 Migration 或初始化机制消除公共开发凭据风险，不修改已执行历史 Migration。

## 4. 当前不建设 Recovery

Mini Auth V1 当前没有恢复旧 IAM 的 Bootstrap/Recovery、JWK、Refresh HMAC Pepper、Session Revocation 等机制。

忘记管理员密码、生产管理员恢复和首次部署 Secret 注入属于后续独立安全设计，不在当前业务 CRUD 之前提前恢复。

## 5. 当前验收

```text
Flyway 空库迁移成功
→ admin 存在
→ PLATFORM_ADMIN 存在
→ auth_user_role 关系存在
→ password_hash 不是明文
→ 后续 Login 能通过 PasswordEncoder 校验
```

生产部署前必须新增验收，确认不存在可公开复用的默认管理员凭据。
