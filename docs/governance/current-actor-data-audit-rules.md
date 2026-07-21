# CurrentActor 与数据审计永久工程规则

1. `mom-core` 定义 Actor 抽象；`mom-security` 提供认证适配；`mom-data` 只消费接口，禁止依赖 `mom-security`。
2. 数据写入缺少 Actor 默认失败，禁止 SYSTEM、用户 0 或匿名回退。
3. SYSTEM Actor 必须使用稳定编码并通过 `AuditContextExecutor.runAsSystem` 显式建立。
4. ThreadLocal 不自动跨 `@Async`、线程池、虚拟线程、MQ 或 Reactor 传播；需要时显式传递 `AuditActor`。
5. 审计处理器只填充 created/updated 时间与 Actor，不填充 Factory、Party、User、Client、Session 或领域状态字段。
6. `updateById(entity)` 与 `update(entity, wrapper)` 是普通受审计更新路径；Wrapper-only Update 禁止。
7. 自定义 XML、注解 SQL、JdbcTemplate 和原生 SQL 必须显式写入更新审计字段并测试。
8. MyBatis-Plus 拦截器链只能有一个 `OptimisticLockerInnerInterceptor`。
9. 测试使用显式固定 Actor 和可替换 Clock，禁止关闭审计让写入测试通过。
