# MOM 多表关联与查询规范

- 状态：Accepted
- 生效范围：MOM 自主 bounded context
- 决策关联：[ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 关系分类

- 聚合内父子：生命周期由聚合根控制；写入顺序和删除/保留规则显式，禁止物理级联。
- 独立聚合引用：只保存稳定 ID/Reference；创建、变更和删除前由 Application 校验。
- 多对多：使用独立关系表，定义技术主键或复合键、业务唯一约束、两侧 ID、查询索引、创建审计、解绑语义、批量上限和重复添加幂等语义；不强制继承完整 `BaseEntity`。
- 历史快照：保存业务发生时不可变的必要字段及来源 ID/版本，不能反向成为权威。
- 事实关联：流水、Outbox/Inbox、谱系边等按不可变/追加模型设计，不套普通 CRUD 生命周期。
- 跨服务引用：禁止 JOIN、物理 FK、直读写、共享 Mapper、依赖对方 Server 或逐条同步远程调用。

## 2. 多表写入

由 Application Service 在明确本地事务中编排：验证引用与授权，确定主/子写入顺序，处理唯一冲突、幂等、乐观锁、affected rows、审计和部分失败回滚；需要发事件时业务写与 Outbox 同事务。Controller、Mapper XML、Trigger 或巨大 SQL 不得承载完整业务流程。Application 不能只是 Mapper 的机械转发，必须表达用例和领域规则。

禁止物理 FK 后，创建/修改校验、受控删除/停用/归档保护、Unique/Check、索引、事务、乐观锁、IT、孤儿诊断与定期对账共同构成完整性边界。

## 3. 聚合加载

领域行为和写用例可由 Repository 通过少量、有界查询加载聚合。必须限制子项数量或使用明确分页/分段策略，避免 N+1、无界集合和跨服务加载。聚合过大时重新审视边界，不以一次加载整表维持“聚合一致”。

## 4. 查询投影

管理列表、组合展示、报表、搜索和本地多表查询使用专用 Query Service、Query Mapper、Row/Projection。Projection 显式列出字段，不返回持久化 Entity，不进入写 Repository。查询 Mapper 位于明确的 `application.query`/`infrastructure.persistence.query` 等查询包，并说明为何 MyBatis-Plus 单表 DSL 不适合。

## 5. JOIN 与 SQL

自定义 JOIN 只允许同一 bounded context、服务和 Schema，必须：

1. 参数化且显式列名，禁止 `SELECT *`、`${}` 和跨 Schema限定名；
2. 动态排序使用服务端白名单；
3. 定义 page size、批量/IN 上限和稳定排序；
4. 说明 JOIN 基数、主表、过滤和索引映射；
5. 使用 PostgreSQL IT 验证结果、分页、空集合和重复行；
6. 对关键查询保存 `EXPLAIN (ANALYZE, BUFFERS)` 的评审结论，不把计划值写成永恒断言。

默认不引入 MyBatis-Plus-Join 或其他 JOIN 扩展；需要时必须另有 Accepted ADR。多表 SQL 的存在不授权把本模块单表 CRUD 迁回 XML。

## 6. 一对多分页

分页对象必须是主表/聚合摘要，禁止直接分页一对多 JOIN 结果行再解释为主对象页。只能选择：

1. 先分页主表 ID，再用有上限的批量查询加载子项；
2. 用子查询/CTE 先分页主表，再 JOIN；
3. 列表只返回摘要，详情端点加载子项。

禁止循环逐条查子表、无界 `IN`、无界结果集和内存全量重分页。批量查询必须分片并定义最大 ID 数；同步跨服务关联使用批量 Query API、本地只读 Projection 或必要快照，不能制造远程 N+1。

## 7. 索引与一致性

索引绑定真实过滤、JOIN、排序和唯一场景；组合索引按选择性和查询前缀设计，不为每个 `_id` 机械建单列索引。关系表至少评估双向查询组合索引。Projection 标记来源和投影时间，可重建且不得用于权限、库存、设备控制等权威决策。

测试覆盖基数、重复关系幂等、唯一冲突、引用不存在、删除保护、事务回滚、分页边界、稳定排序、孤儿检查、N+1 调用次数和索引存在。静态门禁只把难以证明的一对多分页、无界 IN/N+1、缺失索引列为精确 Review Candidate。
