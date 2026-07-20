# CurrentActor 与数据审计基础

- 阶段：`P1.5 S01`
- 状态：**Current / Implemented**
- 权威基线：[P1.5 认证与授权设计基线](../security/P1.5-认证与授权设计基线.md)

## 1. 责任链

```text
显式 AuditContext / Spring Security
→ CurrentActorProvider
→ AuditActor
→ MomMetaObjectHandler
→ created_at / created_by / updated_at / updated_by
```

模块依赖固定为：

```text
mom-security → mom-core ← mom-data
```

`mom-data` 不依赖 `mom-security`，不读取 JWT、SecurityContext 或 HTTP Header。

## 2. Actor 模型

- `USER`：普通已认证业务用户，默认认证身份类型。
- `ADMIN`：调用方显式建立的管理操作上下文；不能仅因 `user_type=INTERNAL` 或拥有任意角色自动升级。
- `SYSTEM`：定时任务、MQ Consumer、Outbox、同步或清理任务，必须使用稳定编码显式建立。

`AuditActor` 只包含 `actorId`、`actorType`、`userType`、`clientId`、`sessionId`、`correlationId`，不包含 Role、Permission、Factory Scope 或 Party Scope。

缺少 Actor 时，`requireCurrentActor()` 抛出 `AuditActorMissingException`。禁止默认 SYSTEM、用户 ID 0 或匿名用户。

## 3. 显式 SYSTEM 与异步

```java
auditContextExecutor.runAsSystem(
    "mom-wms-outbox-publisher",
    () -> repository.save(...));
```

上下文使用普通 ThreadLocal 和严格 `try/finally`：嵌套后恢复外层 Actor，异常后清理，线程池复用不残留。`@Async`、CompletableFuture、线程池、虚拟线程、MQ、定时任务和手工线程均不自动传播；代表用户异步执行时必须显式传递 `AuditActor`。Reactor/WebFlux 未来使用 Reactor Context 扩展，本 Slice 不安装全局 Hook。

## 4. 实体分类

- `BaseIdEntity`：仅 String 技术主键。
- `BaseCreatedEntity`：主键 + `createdAt/createdBy`。
- `BaseAuditEntity`：再增加 `updatedAt/updatedBy`。
- `BaseEntity`：再增加 `version/deleted`，适用于普通可更新业务实体。

Outbox/Inbox、流水、快照、安全审计、OAuth 协议表和特殊状态表按自身语义建模，不强制继承完整 BaseEntity。

## 5. 自动填充

```yaml
mom:
  data:
    audit:
      enabled: true
      fail-on-missing-actor: true
```

INSERT 强制覆盖 `createdAt`、`createdBy`、`updatedAt`、`updatedBy`，同一次操作使用同一 UTC `Instant` 和 Actor。UPDATE 只覆盖 `updatedAt/updatedBy`，创建字段禁止写回。测试可替换 `Clock`。

MetaObjectHandler 不处理 `factory_id`、`supplier_id`、`customer_id`、`party_id`、`user_id`、`client_id`、`session_id`、代表主体、业务状态时间或 Token 时间。

## 6. 更新路径

| 路径 | 规则 |
|---|---|
| `mapper.insert(entity)` | 必须触发 INSERT 审计 |
| `mapper.insert(collection)` | 每个实体必须触发 INSERT 审计 |
| `updateById(entity)` | 触发 UPDATE 审计和乐观锁 |
| `update(entity, wrapper)` | Entity 必须非空，触发 UPDATE 审计 |
| `update(wrapper)` | `MomBaseMapper` 直接拒绝 |
| 自定义 XML / 注解 SQL | SQL 显式写 `updated_at/updated_by` 并测试 |
| JdbcTemplate / 原生 SQL | Repository 显式写审计字段并测试 |

MyBatis-Plus 实体自动填充依赖非空 Entity，不能宣称 Wrapper-only Update 会自动审计。

## 7. 乐观锁

自动配置复用现有 `MybatisPlusInterceptor` 链，并确保只存在一个 `OptimisticLockerInnerInterceptor`。新记录版本从 0 开始；更新成功后递增；过期版本更新返回 0 行。HTTP 409 映射留给后续 Slice。

## 8. P2 业务模块使用

1. 普通请求由后续 Resource Server 建立 SecurityContext。
2. 后台任务和消息消费者必须用稳定 SYSTEM Code 包裹数据库写入。
3. Application Service 负责 Factory、Party 和领域归属；ORM 不猜测。
4. 特殊 SQL 必须显式审计。
5. 测试通过 `runAsActor/runAsSystem` 固定 Actor，禁止关闭审计绕过。
