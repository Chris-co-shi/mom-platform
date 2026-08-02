# 多表查询设计：<query-name>

- 查询用途与调用方：
- 涉及表、所有者和 Schema：
- 是否同一 bounded context/服务/Schema（否即禁止 JOIN）：
- 主表与 JOIN 基数（1:1/1:N/N:M）：
- 查询条件与必填范围：
- 服务端排序白名单与稳定次级排序：
- 分页对象和方式（主表 ID/CTE/摘要+详情）：
- 返回 Row/Projection（不得返回 Entity）：
- 批量/IN/page size 上限：
- 预期数据量、选择性和最大结果：

## SQL 必要性

- MyBatis-Plus 单表 DSL 不适合的具体原因：
- 显式列、参数化、无 `${}`、无 `SELECT *` 证明：
- 跨服务数据采用批量 API/投影/快照的方式：

## 性能与一致性

- N+1/循环 Mapper 风险及调用次数断言：
- 索引与过滤/JOIN/排序映射：
- Projection 新鲜度、权威回查和失败策略：

## 证据

- PostgreSQL IT（基数、空值、重复行、分页边界、稳定排序）：
- `EXPLAIN (ANALYZE, BUFFERS)` 结论与数据规模：
- 一对多分页正确性：
- Review Candidate、责任人和退出条件：
