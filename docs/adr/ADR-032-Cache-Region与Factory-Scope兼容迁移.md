# ADR-032：Cache Region、Factory Scope 与兼容迁移

- 状态：Accepted
- 日期：2026-08-01
- 决策人：MOM Platform 维护者
- 关联需求：P1.6 Framework Governance Phase 1
- 关联文档：[Redis Key、TTL 与失败策略规范](../engineering/standards/redis-key-ttl-failure-standard.md)、[MOM Cache 使用指南](../engineering/mom-cache-guide.md)

## 1. 背景

P1.6 已有 Caffeine/Redis Provider 和旧 `CacheType`、`CacheKey`、`CachePolicy`、`CacheService`，但旧模型没有 Environment、Factory Scope、精确类型恢复和可执行的兼容退出门禁。System 与 IAM 迁移尚未完成，一次删除旧 API 会迫使 Framework 与全部业务模块同时发布。

## 2. 问题

如何在不进行大爆炸重构的前提下，建立可隔离 Global/Factory、可安全恢复类型、可观测且可测试的 L1/L2 Cache 契约？

## 3. 候选方案

### 方案 A：直接删除旧 API 并全仓迁移

优点：最终模型一次到位。

缺点：破坏 Framework Freeze；扩大单次回滚范围；无法用生产指标证明旧入口已安全退出。

### 方案 B：新增 typed Region API，旧入口 Deprecated 并桥接

优点：Framework 与消费者可独立发布；旧调用仍经过同一 typed 实现；可用指标控制删除时机。

缺点：过渡期保留两套入口；需要维护精确兼容测试。

### 方案 C：继续扩展集中式 CacheType

优点：短期代码量最少。

缺点：Framework 持续拥有 IAM/System 业务枚举，无法表达 Factory Scope 和独立 Schema 演进。

## 4. 决策

采用方案 B：新增 `CacheRegion<T>`、`CacheScope`、`CacheValueType<T>`、`CacheEntryKey<T>` 与 typed `CacheService`；旧 `CacheType`、`CacheKey`、`CachePolicy` 和旧 Service 方法保留并标记 Deprecated，通过兼容桥进入 typed Core。

统一物理 Key：

```text
mom:{environment}:{scope}:{bounded-context}:cache:v{keyVersion}:{capability}:{subject}
```

`_global` 为保留 Scope；Factory ID 必须先由服务端授权与对象归属上下文验证。当前 System Catalog、Parameter、Dictionary、I18n 均属于 Global，具体 Region 由 System 拥有，不进入 Framework。

Redis 新信封固定包含 `formatVersion`、`valueType`、`schemaVersion`、`payload`。类型/版本不兼容或 JSON 损坏时删除精确 Key 并按 Miss 回源，禁止 `Object.class`、任意 FQCN、Java 原生序列化和 Map 降级。

## 5. 抽象成立依据

- `CacheProvider` 已有 Caffeine 与 Redis 两个真实生产 Adapter；Core 需要统一 L1→L2→回填顺序。
- typed `CacheService` 属于平台基础能力：System 的 Dictionary/I18n/Parameter/Catalog 是当前消费者，故障语义均为 Redis fail-open 到 PostgreSQL 权威数据。
- `CacheScope` 对制造平台的 Factory 数据隔离是平台不变量，而不是预设租户框架；它不负责解析 Header 或授权。
- 未引入 Factory/Strategy/Registry：Region 是不可变值，业务直接声明；Provider 列表由 Spring 装配。
- 退出条件：若 typed Region 无真实业务消费者，ADR-037 不得 Accepted，相关未消费业务 Region 必须移除；Framework 基础类型本身随 Cache 能力存续。

## 6. 安全与失败语义

- 最终 Authorization Decision、Permission Evaluation Result、Allow/Deny 结果禁止进入通用 CacheService。
- 权限配置或 Permission Code 是否为普通可重建投影需由业务单独判断，不能借此缓存最终授权结果。
- 任何例外必须先有 Accepted Security ADR，覆盖权威来源、失效传播、撤销最大延迟、Factory/Party 隔离、Redis 故障、越权测试和 Kill Switch。
- Redis timeout/error 返回 Miss 并记录指标；Loader 异常保持原样；不得返回损坏或不兼容旧值。
- Region 失效只清 L1；L2 依靠 Key 版本和 TTL 回收，禁止无界扫描。

## 7. Legacy 退出门禁

旧 API 只有同时满足以下条件才可进入 Removal ADR：

1. 全仓生产源码零调用；
2. 所有部署应用的 `increase(mom_cache_legacy_usage_total[release-window]) == 0`；
3. 连续两个正式 Release 周期均为零；
4. CI 结果、生产 Prometheus 查询或截图和发布版本进入 Removal ADR；
5. Removal ADR Accepted 后仍只在后续 Major Cleanup 删除。

缺少任一生产证据，旧 API 继续保留。

## 8. 指标与低基数约束

冻结 Meter 名称：`mom.cache.hit`、`mom.cache.miss`、`mom.cache.eviction`、`mom.cache.error`、`mom.cache.redis.timeout`、`mom.cache.legacy.usage`。只允许固定 Layer/Operation 标签，禁止 Key、Factory、用户、业务单号、Event ID 或 Trace ID 标签。

## 9. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| Factory ID 来源不可信导致越权 | CacheScope 只接收服务端已验证归属；业务 Adapter 不从 Header 直传 |
| 信封升级后旧值不可读 | 精确删除 + Miss 回源；Key/Schema 版本显式演进 |
| Redis 故障掩盖权威数据问题 | Loader 异常不吞；Redis timeout/error 独立指标告警 |
| 兼容 API 永久存在 | Legacy Counter + 两 Release 生产证据 + Removal ADR |

## 10. 验证方式

- 契约测试：旧 API/枚举保留、Deprecated、Global/Factory Key 隔离、Object/权限决策拒绝。
- 单元测试：Jackson 类型恢复、格式/Schema 不兼容、损坏数据、L1/L2 顺序与回填、Legacy 指标。
- Redis IT：真实 Redis TTL、精确损坏 Key 删除、容器暂停 timeout、Loader 回源、同一 Adapter 恢复。
- CI：独立 `redis_cache` Scope/Job 执行 `mom-cache -am verify`。

## 11. 替代与回滚条件

线格式、Key Scope 或权限缓存策略发生变化时必须新建 ADR；不得原地放宽本 ADR。若 typed 实现发生生产故障，可让业务暂时继续使用 Deprecated 入口，但旧入口仍走相同安全 Serializer，不得恢复 Object.class。

## 12. 参考资料

- Spring Boot 4.1 管理的 Jackson 3 ObjectMapper
- Spring Data Redis StringRedisTemplate
- Caffeine 3.x
- Micrometer 1.17.x
