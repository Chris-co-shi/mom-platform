# WMS 当前业务入口

WMS 的库存记账、期初、冻结、同仓库库位移动和盘点属于 **WMS Owner 内部业务能力**，不是 Framework 或平台通用服务。当前范围见[实现地图](../当前实现地图.md)；事务、状态和字段以 WMS Application、Mapper、Migration、测试为准。

| 用例 | HTTP/应用入口 | 当前边界 |
|---|---|---|
| 实时库存与历史 | `InventoryController`、`InventoryQueryService`、`InventoryHistoryApplication` | 余额与交易事实在 `mom_wms`；精度按 Decimal String/BigDecimal |
| 期初建账 | `OpeningInventoryApplication`、`OpeningDocumentNumberService` | MDM 引用在本地事务外校验；正式编号、草稿与 Issue 在 WMS 本地事务内写入 |
| 冻结与库位移动 | `InventoryController`、`InventoryTransferCommitService` | 使用库存记账和条件检查；网络结果未知不能盲目重放新键 |
| 库存盘点 | `InventoryCountController`、`InventoryCountApplication` | 已有创建、范围、开始、实盘、确认、调整、取消及按请求键查询；并非全部 WMS 功能完成 |

库存记账以不可变交易/分录与余额为核心；仓库、库位、物料等静态定义由 MDM 拥有。WMS 不能跨 Schema 改写 MDM；新单据需校验当前可用的引用并保留必要快照。期初与盘点正式编号复用 Owner 本地的 [`CodeGenerationApplication`](../framework/README.md#已形成业务调用链的入口)，不自己生成第二套流水。

## 库存记账与作业约束

服务内统一写入口是 [`InventoryAccountingService.post(Posting)`](../../mom-wms-platform/mom-wms-server/src/main/java/io/github/chrisshi/mom/wms/application/InventoryAccountingService.java)。`Posting` 带稳定 requestKey、交易/来源/操作人和 1–100 条有符号 `BigDecimal` 分录；维度含物料、单位、仓库、库位及可选批次/SN、质量与仓储状态。数量最多 6 位小数。同键同内容返回原交易 ID，异内容拒绝；条件扣减防负库存，写入在本地事务中一起提交或回滚。期初、冻结、移动、盘点各自负责业务状态和引用检查，不能直接改余额 Mapper 来跳过记账。该 Application 是 WMS 内部入口，不是跨模块公开 API。

**期初：** `/opening-documents` 创建草稿、维护明细、过账后进入 `PENDING_ACTIVATION`，仓库仍需通过 `/warehouses/{warehouseId}/activate` 显式启用。每仓库只允许一期初单；草稿版本冲突、重复过账和 MDM 引用失败均需保持原业务语义。过账使用 `OPENING:{documentId}` 请求键，事务内写单据状态、交易和余额。

**仓储冻结：** `/inventory/freezes` 与 `/{freezeId}/unfreeze` 按具体冻结限制移动 NORMAL/FROZEN 数量并记录历史；解冻必须指向限制记录。它不等于 QMS 质量冻结或放行。请求键重放、剩余量条件扣减和冲突检测属于 WMS 本地事务。

**同仓库移动：** `POST /inventory/transfers`，结果未知时按 `/inventory/transfers/by-request-key/{requestKey}` 核对。目标库位须与来源不同且属于同仓库，只允许 ACTIVE 仓库、NORMAL 库存、未被盘点锁定的维度；SN 库存只能全量移动。事务内按请求、仓库、库位、SN 固定锁序并写成对分录。它不是跨仓调拨，也不创建物流执行单。

盘点按仓库锁定、快照与差异记账有独立状态和失败语义。前端创建时持久保存原请求键，响应丢失后用按键查询恢复原单；空查询不能证明原写未提交。具体已验收行为与限制见 [2026-10-21 E2E 证据](../testing/WMS-盘点恢复与期初-E2E-2026-10-21.md)。[Phase 1 库存口径](../wms/Phase1-库存口径与事务说明.md)仍是该早期 Slice 的记录，不能替代当前盘点源码。

跨仓库移动、预占、对账等未在此文档声明实现。新增 WMS 用例先确定本地状态、请求键、锁顺序、事务与未知结果恢复；不得把内部 Application 方法提升为全局公共 API。
