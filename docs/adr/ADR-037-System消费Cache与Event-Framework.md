# ADR-037：System 消费 Cache 与 Event Framework

- 状态：Accepted
- 日期：2026-08-01
- 关联需求：P1.6 Framework Governance Phase 6
- 关联决策：[ADR-032](ADR-032-Cache-Region与Factory-Scope兼容迁移.md)、[ADR-033](ADR-033-Event与Outbox-Ownership.md)、[ADR-035](ADR-035-Framework-Freeze与平台适应度函数.md)

## 1. 背景

System 已有 Catalog、Parameter、Dictionary、I18n 的 Cache Port，但两个 Infrastructure Adapter 直接使用 `StringRedisTemplate`、自建 JSON 编解码和 Redis Set 索引。运行时事件 Producer/Consumer 仍通过 Adapter 的公共 String 常量耦合。Framework Freeze 后，这些实现必须迁移为真实消费者，同时保持 Application Port、数据库线格式、Topic 与 V9 Migration 不变。

## 2. 决策

- Application 继续只依赖 `SystemRuntimeCachePort` / `SystemI18nRuntimeCachePort`；
- Infrastructure Cache Adapter 改为 typed `CacheService`，System 生产源码不再 import Redis Template、Caffeine 或 Cache Serializer；
- Catalog、Parameter、Dictionary、I18n 都使用 `CacheScope.global()`，因为当前权威数据无 Factory 归属；
- 每个 Key 都包含 PostgreSQL 权威版本/checksum；Catalog 完整 Release 行、Parameter/Dictionary Header、I18n Header 均先于 Cache 读取；
- Cache Miss 或 Redis 故障回源 PostgreSQL，禁止返回未经过当前 Header 验证的旧值；
- 失效事件清理有界 L1 Region，版本化 L2 Key 由 TTL 回收，不维护 Redis 索引、不执行 SCAN；
- Producer 使用 `SystemEventType` 生成稳定 code，Consumer 先把 code 映射回本地枚举；未知 code Fail Closed；
- `EventEnvelope`、字符串 code、Topic、Outbox/Inbox 表与 V9 Migration 保持不变。

## 3. 抽象成立依据

- typed `CacheService` 已有 Framework 测试消费者，System 的四类运行时 Projection 是第二组真实生产消费者；
- `SystemEventType` 对应六个已有 Producer/Consumer 事件，不包含未来占位值；
- System Cache Region 由业务 Adapter 持有，未上移到 Framework，也未增加 Factory/Registry/Strategy 层；
- Catalog Cache Port 现在由 Runtime Application 真实调用，不保留无消费者 Region。

## 4. 一致性与失败语义

- PostgreSQL 是权威来源；Cache 从不决定 enabled、当前发布指针或有效版本；
- Catalog 在 Cache 前校验数据库 Snapshot JSON checksum，命中后再次核对 Snapshot 元数据；
- Cache 基础设施异常由 mom-cache 转为 Miss；业务 Loader/数据库异常原样向上；
- Runtime 方法禁止活动事务，避免持有数据库连接等待 Redis；
- Event Evict 不删除 L2 历史版本，新 Header 生成的新 Key 保证旧版本不可命中；
- Outbox 与业务写仍共享 System 本地 PostgreSQL 事务，Broker 不进入事务。

## 5. 安全边界

- Cache 保存可重建目录/配置/字典/I18n Projection，不保存 Authorization Decision 或 Permission Evaluation Result；
- Catalog Snapshot 中的 Permission Code 是展示过滤输入，不是最终 Allow/Deny 决策；每次请求仍使用当前 authorities 在进程内过滤；
- System 不引用 IAM Event Enum；跨 Context 只共享 Event Envelope 与稳定 code。

## 6. 验证

- 架构门禁：System 无 Redis/Caffeine import，POM 只直接依赖 `mom-cache`；
- Adapter 单元测试：Global/versioned Key、L1 Region 失效、I18n Header 匹配；
- Catalog 用例测试：先读取权威 Release，Cache Hit 不重复解码；
- mom-cache 单元测试/Redis IT：L1 Hit、L2 回填、Redis 中断回源与恢复；
- System PostgreSQL 与 Outbox/RocketMQ 验收沿用稳定线格式，不修改 V9。

## 7. 退出与演进条件

若未来 Catalog/Parameter/Dictionary/I18n 归属 Factory，必须先变更权威数据模型和授权归属验证，再迁移为 `CacheScope.factory(factoryId)`；不得从 Header 直接构造 Scope。若需要按 Subject 主动删除 L2，必须证明有界索引方案与故障恢复语义，禁止恢复无界扫描。
