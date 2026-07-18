# ADR-013：Redis 分布式限流

- 状态：Accepted
- 日期：2026-07-18
- 关联需求：REQ-OPS-003、NFR-SEC-006
- 关联文档：[安全架构](../architecture/安全架构.md)

## 1. 背景

Gateway 将以多实例方式部署，需要在多个实例之间共享限流状态。同时，外部 ERP、供应商、客户、PDA、设备和高成本追溯接口具有不同的限流维度和故障策略。

## 2. 候选方案

- 本地内存令牌桶：实现简单，但多实例配额不一致，Pod 重启丢失状态。
- Sentinel：治理能力丰富，但会增加独立规则和运行体系。
- Bucket4j + Redis：灵活，但需要更多自有集成代码。
- Spring Cloud Gateway RedisRateLimiter：与 Gateway 集成直接，适合 V1 多实例令牌桶验证。

## 3. 决策

- Gateway 使用 Redis 令牌桶实现入口分布式限流。
- Integration Hub 对出站连接器提供第二层限流。
- 支持 `replenishRate`、`burstCapacity` 和 `requestedTokens`。
- 限流维度至少支持 route、IP、user、client、factory、supplier、customer 和 device。
- 高成本追溯、批量导出和 ERP 批量导入可消耗多个 Token。

Key 形式建议：

```text
rl:{environment}:{routeId}:{subjectType}:{subjectId}
```

## 4. 故障策略

按接口等级区分：

- 登录、验证码：优先保护系统，采用保守策略。
- 外部 API：Redis 不可用时可拒绝或降低配额。
- 生产和 PDA 操作：不得简单全部失败，应使用受控本地应急桶或明确降级窗口。
- 设备关键回执：优先保证结果接收，但限制异常重试风暴。
- 报表和追溯：可严格失败并提示稍后重试。

## 5. 动态策略

PostgreSQL 保存策略定义和审计信息；Redis 保存运行时令牌状态。策略至少包含：路由、Key 类型、补充速率、突发容量、请求成本、故障模式、版本和启用状态。

## 6. 指标

至少输出：

- `mom_rate_limit_allowed_total`
- `mom_rate_limit_rejected_total`
- `mom_rate_limit_redis_error_total`
- `mom_rate_limit_fallback_total`

Label 仅使用 route、policy、subject_type 和 result 等低基数字段。

## 7. 后果

正向：多 Gateway 实例共享配额，可展示令牌桶、成本型限流和 Redis 故障降级。

负向：Redis 成为入口治理依赖，需要设计连接失败、超时和降级，且策略维度过多可能产生大量 Key。

## 8. 验证方式

- 单实例和三实例共享限流。
- 突发和持续 QPS 测试。
- 用户、Client 和设备之间配额隔离。
- 高成本接口扣除多个 Token。
- Redis 故障和恢复。
- 429 响应、响应头、指标和 Trace 验证。

## 9. 替代条件

当需要更完整的熔断、热点参数、集群流控或控制台治理时，可以通过新 ADR 评估 Sentinel；当前 Redis 令牌桶的策略模型和验收场景应保持可迁移。