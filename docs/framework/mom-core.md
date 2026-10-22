# mom-core：跨层基础契约

**状态：已被多服务采用。** 本模块不依赖 HTTP、Spring 或数据库，承载分页、关联标识、ID、审计主体及文案解析的最小契约。源码入口为 [`mom-core`](../../mom-framework/mom-core/)；具体使用前检查目标服务的实现 Bean。

- 分页：Controller 与 Application 传递 `PageQuery<XxxPageParams>(params, pageNo, pageSize)`；`params` 不可为 `null`，页码和页大小从 1 起，非法值抛 `PageQueryValidationException`。数据库层再通过 `mom-data` 的 `PageAdapter` 校验配置化上限。返回 `PageResult<T>`，转换记录用 `map` 保留分页元数据；HTTP 层再包装 `Result<T>`。Auth、MDM、WMS 已使用。
- 审计：`CurrentActorProvider`、`AuditActor`、`AuditContext` 是主体传递契约。`ActorType` 当前只有 `USER`/`SYSTEM`。`AuditContextExecutor.java` 的类体全部注释，不能作为可调用 API；缺少主体时的运行现状和目标规则见[审计规范](../engineering/standards/audit-concurrency-lifecycle-standard.md)。
- 其他：`IdGenerator` 是 ID 生成抽象；`I18nMessageResolver` 是本地化解析入口；`CorrelationContext` 仅传递排障标识。调用方不能把 Correlation ID 当业务 ID、权限或幂等凭证。

**使用边界：** 不为相同分页语义另建复制字段的模型；不将 `PageResult` 或审计主体类型变成 HTTP/数据库实现。`mom-core` 的接口本身不提供持久化、鉴权或翻译数据，需由 Owner 服务接入实现。
