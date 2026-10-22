# mom-idempotency：Redis 请求占位

**状态：技术原语；Integration 只有默认关闭的探针，尚无成熟业务消费者。** `IdempotencyGuard.tryAcquire(scope, requestKey, ownerToken, ttl)` 返回 `IdempotencyAcquireResult`，底层为 Redis 原子 SET-NX-TTL。源码入口为 [`mom-idempotency`](../../mom-framework/mom-idempotency/)。

- 参数：`scope` 是稳定业务动作作用域；`requestKey` 保持原始字节语义，不 Trim、不直接写入 Redis Key；`ownerToken` 标识本次持有者；TTL 必须大于零并覆盖重试窗口。
- 返回/故障：`ACQUIRED` 表示取得占位，`DUPLICATE` 表示已存在；仅显式 fail-open 且 Redis 故障时可返回 `BYPASSED`。默认 fail-closed 抛 `IdempotencyUnavailableException`；参数非法抛 `IllegalArgumentException`。自动配置依赖 `StringRedisTemplate` 和 `mom.idempotency.redis.*`。
- 限制：不保存首次业务结果、不和数据库事务原子化、不自动释放占位，也不保证重复请求得到同一响应。Integration 探针的状态码不是业务接口模板。

业务入口仍须定义主体/操作/负载身份、数据库唯一约束、失败恢复及重放语义；见[API 幂等规范](../engineering/standards/api-evolution-idempotency-standard.md)。
