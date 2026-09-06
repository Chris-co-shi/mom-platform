# MOM 多表关联与查询规范

- 状态：Accepted
- 生效范围：MOM 自主 bounded context
- 当前架构决策：[ADR-042](../../adr/ADR-042-MOM渐进式分层与对象模型.md)
- 关联决策：[ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 先区分“关系”“聚合”和“查询结果”

MOM 不再把“多表 JOIN 返回对象”默认称为 Aggregate。

DDD Aggregate 表达的是**业务一致性和事务边界**：哪些对象必须共同维护不变量、谁是 Aggregate Root、哪些状态变化必须通过 Root 完成。

而以下场景通常只是 Read Model：

- 管理列表；
- 详情组合展示；
- User + Role + Permission 展开；
- 报表和统计；
- 搜索结果；
- 多表分页结果。

这些结果应优先使用 `View`、`Row` 或 `Projection`，而不是 `*Aggregate`。

## 2. 关系分类

- 聚合内父子：只有确实存在共同生命周期和一致性边界时才按 Aggregate 处理；写入顺序和删除/保留规则显式，禁止物理级联；
- 独立实体/聚合引用：只保存稳定 ID/Reference；创建、变更和删除前由 Application 校验；
- 多对多：使用独立关系表，定义技术主键或复合键、业务唯一约束、两侧 ID、查询索引、创建审计、解绑语义、批量上限和重复添加幂等语义；不强制继承完整 `BaseEntity`；
- 历史快照：保存业务发生时不可变的必要字段及来源 ID/版本，不能反向成为当前权威；
- 事实关联：流水、Outbox/Inbox、谱系边等按不可变/追加模型设计，不套普通 CRUD 生命周期；
- 跨服务引用：禁止 JOIN、物理 FK、直读写、共享 Mapper、依赖对方 Server 或逐条同步远程调用。

## 3. 多表写入

多表写由 Application 在明确本地事务中编排：

- 验证引用与授权；
- 确定主/子或关系写入顺序；
- 处理唯一冲突；
- 处理幂等、乐观锁和 affected rows；
- 维护审计；
- 处理部分失败回滚；
- 需要发事件时业务写与 Outbox 同事务。

Level 1 Application 可以直接协调 Mapper。只有真实 Domain/Repository 边界已经建立时，才要求通过对应 Port/Repository。

Controller、Mapper XML、Trigger 或巨大 SQL 不得承载完整业务流程。

禁止物理 FK 后，创建/修改校验、受控删除/停用/归档保护、Unique/Check、索引、事务、乐观锁、IT、孤儿诊断与必要对账共同构成完整性边界。

## 4. 三种查询路径

### 4.1 简单单表查询

MyBatis-Plus 可以清晰表达时：

```text
Application → Mapper → Entity → View/结果
```

不创建 QueryMapper、Row、Projection 或 Repository 占位层。

### 4.2 有界本地多表查询

当真实 JOIN、统计、搜索、组合分页或查询复用使普通单表 DSL 不再清晰时：

```text
Application
    ↓
QueryMapper
    ↓
Row / Projection
    ↓
View
```

- QueryMapper 位于 `infrastructure.query` 或复杂持久化结构中的 `infrastructure.persistence.query`；
- Row/Projection 显式列出 SQL 返回字段；
- View 位于 Application 语义边界；
- 不返回持久化 Entity 伪装多表结果；
- 不进入写 Mapper/Repository。

如果 SQL 返回结构已经是一个稳定且无基础设施信息的查询模型，可以减少无意义的一对一转换；但不得让数据库列名、ORM Page、Mapper 技术类型泄漏为公开 API 契约。

### 4.3 Domain Aggregate 加载

只有写行为需要维护真实领域不变量时，才通过 Repository 或有界查询加载 Domain Aggregate。

必须限制子项数量或使用明确分页/分段策略，避免 N+1、无界集合和跨服务加载。聚合过大时重新审视边界，不以“一次加载整表”维持伪一致性。

## 5. Row / Projection / View 的权威语义

`Row` / `Projection` 在本规范中首先表示“查询形状”，并不天然等于最终一致、异步物化或非权威数据。

必须区分：

1. **实时查询投影**：直接从本 bounded context 的权威表读取，可用于该用例的实时业务判断，但必须遵守事务和并发语义；
2. **物化 Read Model / 快照 / 缓存投影**：可能存在延迟，只能在已明确允许最终一致性的场景使用。

因此不能简单规定“Projection 一律不得用于权限、库存或设备控制”。真正禁止的是：

> **不得把存在复制延迟、异步构建或可重建但非权威的 Read Model，当成强一致业务事实。**

例如 Auth 在同一权威 Schema 内实时查询 User → Role → Permission 后构造 Token Principal，可以使用专用 QueryMapper/Row；但异步同步出来的权限快照若没有明确一致性协议，则不能替代认证权威状态。

## 6. JOIN 与 SQL

自定义 JOIN 只允许同一 bounded context、服务和 Schema，必须：

1. 参数化且显式列名，禁止 `SELECT *`、`${}` 和跨 Schema 限定名；
2. 动态排序使用服务端白名单；
3. 定义 page size、批量/IN 上限和稳定排序；
4. 说明 JOIN 基数、主表、过滤和索引映射；
5. 使用 PostgreSQL IT 验证结果、分页、空集合和重复行；
6. 对关键高频/大数据查询保存 `EXPLAIN (ANALYZE, BUFFERS)` 的评审结论，不把单次计划值写成永恒断言。

默认不引入 MyBatis-Plus-Join 或其他 JOIN 扩展；需要时必须另有 Accepted ADR。多表 SQL 的存在不授权把本模块普通单表 CRUD 全部迁回 XML。

## 7. 一对多分页

分页对象必须是主表或业务摘要，禁止直接分页一对多 JOIN 展开行后再解释为主对象页。

允许：

1. 先分页主表 ID，再用有上限的批量查询加载子项；
2. 用子查询/CTE 先分页主表，再 JOIN；
3. 列表只返回摘要，详情端点再加载子项。

禁止：

- 循环逐条查子表；
- 无界 `IN`；
- 无界结果集；
- 内存全量重分页；
- 同步跨服务远程 N+1。

批量查询必须定义最大 ID 数并按需分片；同步跨服务关联优先使用批量 Query API、必要本地只读快照或明确的组合服务。

## 8. QueryMapper 的引入标准

创建 `*QueryMapper` 前必须能说明普通 Mapper 为什么不够。合理原因包括：

- 三表及以上 JOIN；
- 一对多安全分页；
- 聚合统计；
- 多字段组合搜索；
- 数据库特有能力；
- 同一复杂查询有多个调用方；
- 查询性能需要独立 SQL 和索引优化。

以下理由不充分：

- “读写分离看起来更规范”；
- “以后可能会 JOIN”；
- “每个 Application 都应该有 Query Service”；
- “为了符合 CQRS”。

MOM 只借用读写模型可不同的思想，不默认引入 Command Bus、Query Bus、Event Store、独立读库或异步 Projection Pipeline。

## 9. 索引与一致性

索引绑定真实过滤、JOIN、排序和唯一场景；组合索引按查询前缀、选择性和排序需求设计，不为每个 `_id` 机械创建单列索引。关系表至少评估双向查询索引。

测试根据实际复杂度覆盖：

- JOIN 基数；
- 重复关系幂等；
- 唯一冲突；
- 引用不存在；
- 删除保护；
- 事务回滚；
- 分页边界；
- 稳定排序；
- N+1 调用次数；
- 关键索引存在和执行计划。

静态门禁只识别可证明的结构错误；SQL 基数、索引有效性和性能仍必须通过 Review 与 PostgreSQL 证据判断。