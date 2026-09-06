# IAM 内置管理员初始化与恢复（历史资料）

- 状态：Historical / Not applicable to Mini Auth V1
- 原模块：`mom-iam-platform/mom-iam-server`
- 当前替代文档：[Mini Auth 管理员初始化](Mini-Auth管理员初始化.md)

> 本文原本描述旧 IAM 的 Bootstrap / Recovery、JWK、Refresh HMAC Pepper、Session 撤销等机制。Mini Auth V1 已删除这些运行时能力，因此本文不再作为当前操作手册。

旧 IAM 曾使用 `IAM_BOOTSTRAP_*`、`IAM_ADMIN_RECOVERY_*`、JWK Key 和 Refresh HMAC Pepper 初始化或恢复 `admin`，并与旧 Session/Refresh/JWT 链绑定。这些环境变量、启动方式和 `mom-iam-server` 路径均不适用于当前 `mom-auth-platform/mom-auth-server`。

当前 Mini Auth 使用：

```text
mom_auth Schema
V2__seed_platform_admin.sql
admin → PLATFORM_ADMIN
```

当前只作为 local/dev 初始化基线。生产管理员初始化和恢复机制尚未冻结。

不得在 Mini Auth 中直接恢复旧 IAM Bootstrap Bean、JWK、Refresh Pepper、Session/Refresh 撤销或 `IAM_ADMIN_RECOVERY_*` 变量。未来如需生产管理员恢复，应基于当前 Opaque Token 与 `mom_auth` 模型重新设计。
