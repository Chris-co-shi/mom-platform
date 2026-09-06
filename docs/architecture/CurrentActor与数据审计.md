# CurrentActor 与数据审计基础

- 状态：**Current / Implemented**
- 生效日期：2026-09-03
- 权威基线：[P1.5 认证与授权设计基线](../security/P1.5-认证与授权设计基线.md)
- 安全架构：[安全架构](安全架构.md)

## 1. 当前责任链

当前实现只保留一条清晰责任链：

```text
Spring Security SecurityContext
        ↓
SecurityCurrentActorProvider
        ↓
CurrentActorProvider
        ↓
AuditActor
        ↓
MomMetaObjectHandler
        ↓
created_at / created_by / updated_at / updated_by
```

模块依赖固定为：

```text
mom-security → mom-core ← mom-data
```

`mom-data` 不依赖 `mom-security`，也不读取 Token、SecurityContext 或 HTTP Header。

## 2. Actor 模型

V1 `ActorType` 只包含：

```text
USER
SYSTEM
```

不再保留 `ADMIN` 这种会与 Role/Permission 概念混淆的 ActorType。

`AuditActor` 只包含：

```text
actorId
actorType
```

不包含：

- username；
- userType；
- clientId；
- sessionId；
- correlationId；
- Role；
- Permission；
- Factory / Party Scope。

Actor 只回答：

> 谁执行了这次数据写入，以及它是 USER 还是 SYSTEM。

## 3. CurrentActorProvider

`mom-core` 定义：

```text
CurrentActorProvider
```

这是 `mom-data` 与具体认证框架之间的依赖倒置边界。

业务认证如何取得当前用户，由 `mom-security` 适配；数据层只依赖抽象。

## 4. SecurityCurrentActorProvider

Servlet 用户请求默认通过 Spring Security 获取当前 Actor。

逻辑：

```text
SecurityContextHolder.getContext().getAuthentication()
        ↓
Authentication == null
或 !isAuthenticated()
或 AnonymousAuthenticationToken
        ↓
Optional.empty()
```

认证有效时：

```text
Authentication#getName()
        ↓
actorId
        ↓
AuditActor(actorId, USER)
```

如果 `getName()` 为空或空白，同样返回 empty。

## 5. 与 Opaque Token 的稳定契约

`MomOpaqueTokenIntrospector` 创建 Spring Security Principal 时必须保证：

```text
Authentication#getName() == MomTokenPrincipal.userId
```

因此用户请求的数据审计链最终是：

```text
MomTokenPrincipal.userId
        ↓
DefaultOAuth2AuthenticatedPrincipal.name
        ↓
BearerTokenAuthentication#getName()
        ↓
SecurityCurrentActorProvider
        ↓
AuditActor.actorId
```

CurrentActor 不需要知道 Token 是 JWT 还是 Opaque Token，也不依赖 Redis。

## 6. AutoConfiguration

`MomSecurityActorAutoConfiguration` 默认注册：

```text
SecurityCurrentActorProvider
        ↓
CurrentActorProvider Bean
```

条件：

- Spring Security `SecurityContextHolder` 在 classpath；
- 应用没有自己提供 `CurrentActorProvider`。

应用如果存在特殊 Actor 来源，可以显式提供自己的 `CurrentActorProvider`，framework 自动退让。

## 7. SYSTEM Actor

V1 保留 `SYSTEM` 类型用于未来：

- Scheduler；
- MQ Consumer；
- Outbox Publisher；
- 数据同步；
- 系统清理任务。

但当前 framework **不提供通用 `AuditContextExecutor`**，也不假设 ThreadLocal 自动传播。

因此在真正出现后台写入场景前，不提前建设复杂的 SYSTEM 上下文传播框架。

具体后台任务如何显式建立 SYSTEM Actor，应在对应业务场景落地时设计并测试。

禁止在找不到用户 Actor 时静默回退成 SYSTEM。

## 8. 实体分类

当前数据实体层级：

- `BaseIdEntity`：技术主键；
- `BaseCreatedEntity`：增加 `createdAt/createdBy`；
- `BaseAuditEntity`：增加 `updatedAt/updatedBy`；
- `BaseEntity`：增加 `version/deleted`。

特殊流水、快照、Outbox/Inbox 或其他具有独立语义的数据表，不强制继承完整 `BaseEntity`。

## 9. 自动填充

`MomMetaObjectHandler` 使用：

- `Clock`；
- `CurrentActorProvider`。

普通 INSERT：

```text
createdAt
createdBy
updatedAt
updatedBy
```

普通 UPDATE：

```text
updatedAt
updatedBy
```

统一使用 UTC `Instant`。

业务归属字段不得由 `MetaObjectHandler` 猜测或自动填充，例如：

```text
factory_id
organization_id
supplier_id
customer_id
user_id
```

这些字段必须由 Application / Domain 用例明确决定。

## 10. 更新路径

MyBatis-Plus 自动填充依赖真实 Entity。

原则：

| 路径 | V1 规则 |
|---|---|
| `insert(entity)` | 触发 INSERT 审计 |
| `updateById(entity)` | 触发 UPDATE 审计 |
| `update(entity, wrapper)` | Entity 非空时允许审计 |
| Wrapper-only Update | 不假设能自动获得完整审计 Actor |
| 自定义 SQL | 显式处理必要审计字段并测试 |

不要通过 ORM 技巧隐藏业务归属或安全判断。

## 11. 当前边界总结

```text
mom-core
├── ActorType(USER, SYSTEM)
├── AuditActor(actorId, actorType)
├── AuditActorMissingException
└── CurrentActorProvider

mom-security
├── SecurityCurrentActorProvider
└── MomSecurityActorAutoConfiguration

mom-data
├── MomMetaObjectHandler
└── MyBatis-Plus 数据基础设施
```

这条链保持协议中立：Token、Redis、HTTP 和 Spring Security 的具体实现都不会进入 `mom-core` 或 `mom-data`。
