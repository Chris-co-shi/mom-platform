# 表设计记录：<bounded-context>/<table>

## 基本信息

- 数据所有权与 Schema：
- 表类型（普通/关系/事实/流水/快照/投影/协议/基础设施）：
- 生命周期与状态迁移：
- 停用、逻辑删除、物理删除、归档和保留期：
- 预计初始量、日增、峰值、最大集合和保留量：

## 字段与约束

| 字段 | 类型/长度/精度/scale | Null/默认值 | 语义/单位 | 敏感性 | 注释 |
|---|---|---|---|---|---|

- 技术主键与 Entity 基类选择：
- 业务键及 `uk_<table>_<semantic>`：
- 状态/范围/Version 的 `ck_<table>_<semantic>`：
- 引用列、权威来源、稳定 ID/Reference：
- 无物理外键完整性方案（创建/变更校验、删除保护、孤儿检查、对账）：

## 并发、事务与审计

- 本地事务边界与写入顺序：
- 唯一冲突、幂等、乐观/悲观锁、affected rows：
- Actor 与创建/修改/领域事实审计：
- Outbox/Inbox 是否与业务写同事务：

## 查询与索引

| 查询场景 | 过滤/JOIN | 排序/分页 | 预期行数 | 索引 `ix_<table>_<query_semantic>` | 验证 |
|---|---|---|---|---|---|

## Migration 风险与证据

- Flyway 版本与 Expand → Migrate → Contract 步骤：
- 大表锁、扫描、回填、磁盘和双版本风险：
- 回滚/补偿方案：
- PostgreSQL IT、孤儿查询、索引和 EXPLAIN 证据：
- 未能静态证明的 Review Candidate 及关闭人/条件：
