# WMS Inventory Core Phase 1

本阶段只实现实时余额、不可变交易及正负分录。Material、Warehouse、Location 的静态身份归 MDM；WMS 在本地余额保存这些 ID 和记账时的展示快照，不跨 Schema JOIN。记账调用方必须先核验 MDM 引用；本阶段没有普通用户库存增减 API，也没有完整期初建账流程。

## 状态与数量

- 质量状态：`PENDING`、`QUALIFIED`、`RESTRICTED`、`REJECTED`。
- 仓储状态：`NORMAL`、`FROZEN`。两类状态独立；同一 Material、Batch、SN、Location 可分别存在多种状态余额。
- 实物总量：非零 Balance 数量之和。
- 可用量：仅 `QUALIFIED + NORMAL` 的余额之和。
- 不可用量：总量减可用量；冻结量为 `FROZEN` 的余额之和，属于不可用量子集，不得再次加到总量。
- 归零余额保留权威维度，但默认不参与总览与明细。

ADR-006 是长期目标，其公式 `available = on_hand - reserved - locked - quarantine` 涵盖预占、独立锁定及隔离数量。Phase 1 尚无这些数量字段与作业，本阶段“可用”仅表示质量及仓储状态允许使用，不代表未来可分配库存；不修改历史 ADR。

## 记账事务与故障边界

`InventoryAccountingService.post` 是唯一公开记账入口，使用默认 `REQUIRED` 与 PostgreSQL `READ COMMITTED`。业务幂等键由交易表唯一约束抢占；相同键与相同请求内容重放返回原交易 ID，不同内容拒绝。余额的正数 Upsert 与负数条件 Update 依赖唯一约束及行锁；负数余额由 SQL 条件和 Check 双重拒绝。交易、余额、分录共用 WMS DataSource，任一分录失败整体回滚。交易与分录禁止 UPDATE/DELETE；只追加。

事务内没有 Feign、Redis、MQ、文件或设备调用；不使用 Outbox、Inbox 或 Seata。调用方必须在事务外完成跨服务静态引用校验，并在自身业务失败模型中处理 MDM 引用变化窗口。WMS 数据库不可用时查询失败，不能返回伪造的零库存。期初数据由测试或后续受控入口调用记账服务，不允许直接修改 Balance。

## 当前限制

余额中的物料、仓库与库位名称/编码是记账时快照，主数据更名不会自动刷新；Phase 1 没有主数据同步投影。没有预占、冻结操作、移动、库存历史查询或重算对账 API。后续业务接入记账服务前，需要明确其来源键、MDM 校验与权限边界。
