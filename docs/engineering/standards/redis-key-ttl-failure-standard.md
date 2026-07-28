# Redis Key、TTL、序列化、原子性与故障工程规范

- 状态：Current
- 生效 Slice：P1.6 S03
- 关联：[ADR-013](../../adr/ADR-013-redis-rate-limit.md)、[HTTP 幂等与演进规范](api-evolution-idempotency-standard.md)

## 1. 数据边界

Redis 只保存可重建或具有明确生命周期的临时运行状态，不是库存、工单、质量、权限配置或主数据的权威库。数据库约束和领域状态机仍是正确性最终防线。

## 2. Key

新 MOM 自管 Key 使用：`mom:{environment}:{bounded-context}:{capability}:{version}:...`。必须包含环境、领域、能力和版本；主体使用不可逆摘要或非敏感稳定标识。

密码、Token、Refresh Token、Authorization Code、Cookie、邮箱、手机号、姓名、身份证件、大 Payload 和设备秘密不得进入 Key。原始 Idempotency-Key 不 Trim、不改大小写、不做 Unicode 归一化、不截断，只以 UTF-8 原始字符参与不可逆摘要。

以下是兼容现状，S03 不重命名：

- IAM/Gateway `mom:iam:revoked:sid:`；
- Spring Cloud Gateway RedisRateLimiter 的框架 Key；
- 现有幂等技术探针 `mom:{environment}:{application}:idempotency:{scope}:{sha256}`。

新命名规范不构成旧 Key 的即时迁移授权；迁移必须有双读/双写或兼容窗口和清理证据。

## 3. TTL

Cache、revoked sid、Idempotency、Lock、Rate Limit、登录尝试、临时状态、PKCE/Nonce/State 服务端状态均必须有正 TTL。TTL 与业务生命周期一致，可加抖动防止同时过期；不得用永久 Key 或零/负 TTL 掩盖设计缺失。

revoked sid 至少覆盖已签发 Access Token 剩余寿命；HTTP 幂等 TTL 覆盖客户端/Gateway/网络的最大重试窗口；锁 TTL 不能替代所有权校验。

## 4. Serializer

Key 使用 String；Value 使用 String、数字、字节或具有版本/兼容策略的明确 JSON。禁止 Java 原生序列化、默认 Object 序列化和携带任意 Java 类名。反序列化失败必须可观测，不能静默生成伪数据。

## 5. 原子性与命令

并发保证使用单条 Redis 命令、受版本控制的 Lua Script 或明确事务能力；禁止 `GET → Java 判断 → SET` 作为原子保证。Lua 必须有参数上限和边界测试，不做无界扫描、不返回秘密。

业务代码禁止 `KEYS`、`FLUSHALL`、`FLUSHDB`、无界 `SCAN`、请求线程大批删除和 `MONITOR`。删除使用前缀、批次、上限、指标，并确保状态可恢复或重建。

## 6. 故障语义矩阵

| 能力 | Redis 故障策略 | 当前证据/边界 |
|---|---|---|
| revoked sid | Fail Closed | IAM 写入与 Gateway 检查均抛错/503；健康路径绕过检查 |
| 登录/验证码限流 | 保守拒绝或受控保护 | 尚无独立实现；不得默认放行 |
| 普通缓存 | 回源或明确降级 | 需逐 Cache 定义，不能伪数据 |
| 高成本查询限流 | 可 429/503 | 需区分正常拒绝与基础设施故障 |
| 生产/PDA 限流 | ADR-013 受控应急策略 | 当前 Gateway 自定义 limiter 为 Fail Closed；应急切换需运维决策 |
| HTTP 幂等 | 按操作风险决定；不能假装取得执行权 | `RedisIdempotencyGuard` 默认 Fail Closed，显式 Fail Open 返回 `BYPASSED` 而非 `ACQUIRED` |
| 分布式锁 | 无法确认所有权时不得进入临界区 | Redis 只降低竞争，不是最终正确性 |
| 技术探针 | 明确失败，不影响普通启动 | 默认关闭 |
| Session/Refresh 撤销 | 保持现有安全 Fail Closed | S03 不改语义 |

## 7. 低阶幂等原语

`RedisIdempotencyGuard` 只提供原子 `SET NX + TTL` 占位，不保存请求摘要/响应、不实现调用主体与 Client 绑定、状态恢复或冲突响应，因此不是完整 HTTP 幂等平台。

Key Factory 对 namespace/scope 片段可做受控规范化，但原始请求 Key 必须按原样摘要。空值、纯空白和超过 1024 个 Java 字符的值拒绝；`"key"`、`" key "`、大小写差异和不同 Unicode 序列是不同身份。错误和输出不包含原始 Key。

## 8. 当前实现结论

- revoked sid Key 和 Gateway RateLimiter Key 保持兼容。
- `RedisIdempotencyGuard` 已使用单条 `setIfAbsent(key,value,ttl)`，不修改命令/TTL/故障策略。
- Integration 幂等探针默认关闭且是唯一非测试调用方；S03 可移除 Factory 摘要前 `trim()`，无需迁移长期状态。

## 9. 官方依据

- [Spring Data Redis RedisTemplate](https://docs.spring.io/spring-data/redis/reference/redis/template.html)
- [Spring Data Redis Scripting](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
- [Spring Cloud Gateway RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)
