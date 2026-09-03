# IAM 内置管理员一次性恢复（历史资料）

- 状态：Historical / Not applicable to Mini Auth V1
- 原模块：`mom-iam-platform/mom-iam-server`
- 当前替代文档：[Mini Auth 管理员初始化](Mini-Auth管理员初始化.md)

> 旧 IAM 的一次性 Recovery 依赖 Session、Refresh Token、JWK、安全审计和专用恢复 Bean。Mini Auth V1 已经删除这些能力，因此本流程不得用于当前 `mom-auth-platform`。

旧方案曾使用：

```text
IAM_ADMIN_RECOVERY_ENABLED
IAM_ADMIN_RECOVERY_PASSWORD
IAM_ADMIN_RECOVERY_CONFIRMATION
IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE
```

并在恢复后撤销旧 Session / Refresh Token、写入安全审计。这些行为均属于旧 IAM。

当前 Mini Auth 只建立开发初始化账号：

```text
admin → PLATFORM_ADMIN
```

生产密码恢复、一次性管理员重置和首次部署 Secret 注入尚未进入当前实现。

禁止把旧 `IAM_ADMIN_RECOVERY_*` 变量重新接入新 Auth、为了恢复旧运维能力重新引入 Session/Refresh/JWK、直接修改已经执行过的 Flyway 历史 Migration，或将当前开发 seed 当成生产 Recovery 机制。

未来如有生产管理员恢复需求，应基于 `mom_auth` 当前表结构、PasswordEncoder 和 Redis Opaque Token 模型单独设计并通过新的 ADR/安全验收。
