# Code Rule V1-B 真实业务 E2E 验收报告

日期：2026-10-21（Asia/Shanghai）。本轮只修改本报告及截图，不修改业务代码、规则、序列或历史记录。

## 环境与证据边界

- 浏览器：Codex 内置真实 Chromium，访问 `http://localhost:5173`；用户在该会话完成登录。前端为现有 Vite 服务。
- 后端：隔离启动 Auth `20001`、MDM `20200`、System `20300`、WMS `20400`、Gateway `20000`。Gateway 与 WMS 的本地静态服务发现指向本轮服务。原有 MDM `21200`、System `21300` 未改动。
- PostgreSQL：原库 `mom_platform` 只读；验收写入仅在 `mom_code_rule_v1b_e2e_run_20261021`。此库由原库快照恢复，由专用非超级用户拥有。另有一次未使用的快照库 `mom_code_rule_v1b_e2e_20261021`，无本轮业务写入。两库保留供复核。
- 原库 Flyway 至 V15，缺少 `wms_code_rule_issue`；隔离库启动 WMS 后 Flyway 至 V17 且成功。原库盘点单 1、盘点流水 1；验收后仍为 1、1。隔离库验收后为 2、2。
- 本轮启动时后端已有两处未提交的源码格式改动，未触碰。页面验收使用启动时现有的 WMS 可执行 JAR；随后定向 Maven `verify` 重新构建并运行集成测试。
- 验收后已停止本轮启动的五个后端服务；隔离数据库及截图保留，供复核。前端原有 Vite 服务未停止。

## 1. 实际调用链

### 期初单

页面 `OpeningInventoryListPage.vue` 的“创建草稿”点击调用 `createOpeningDocument(warehouseId)`，发送 `POST /wms/opening-documents`。`OpeningInventoryController.create` 调用 `OpeningInventoryApplication.create`，先在事务外查询并验证 MDM 仓库，再生成单据 ID，进入 `OpeningInventoryCommitService.create` 的 `@Transactional` 本地 PostgreSQL 事务。该事务初始化并锁定仓库库存状态、检查余额，经 `OpeningDocumentNumberService.allocate(documentId)` 固定使用 `wms.opening.document`，再经 `CodeGenerationApplication`、`DefaultCodeGenerationApplication`、`JdbcCodeRuleStore` 锁规则、推进事务型流水、插入正式发号记录，最后插入 `wms_opening_document`。发号、记录、仓库状态与单据写入同一 WMS 本地事务。页面打开列表、详情或关闭详情均不调用创建 API。

期初单的 Code Rule 请求键由服务端新生成的单据 ID 派生；创建 HTTP 请求没有客户端 `requestKey`。因此它保证单次事务内发号原子性、仓库唯一性，不能保证客户端超时重发能返回同一单据。仓库唯一约束可阻止重复业务单据，但无法把未知响应与本次请求精确关联。

### 盘点单

页面 `InventoryCountListPage.vue` 的“创建盘点”点击生成 UUID `requestKey`，发送 `POST /wms/inventory-counts`，请求体含 `requestKey`、`warehouseId`、空的 `locationIds` 和 `materialIds`。`InventoryCountController.create` 调用 `InventoryCountApplication.create`，先在事务外验证 MDM 仓库和范围，再进入 `InventoryCountCommitService.create` 的 `@Transactional` 本地事务。它按请求键加锁并查重、验证 WMS 仓库 `ACTIVE`，使用固定规则码 `wms.inventory.count` 发号，再插入 `wms_inventory_count_task`。Code Rule 流水、Issue 与任务写入同一 WMS PostgreSQL 事务。

同一请求键与同一业务参数返回原任务；同键改参或更换操作人拒绝。任务表 `request_key` 也有唯一约束。`GET /wms/inventory-counts/by-request-key/{requestKey}` 仅向原创建人返回已提交任务；空结果不能证明原请求未执行。

## 2. 场景结果

| 项目 | 结果 | 实际证据与限制 |
|---|---|---|
| 登录、WMS 路由及列表 | PASS | 浏览器进入两页，盘点列表初始 1 条、期初列表 3 条。 |
| 列表及详情读取不发号 | PASS | 读取前后期初流水 2、盘点流水 1；读取期初详情后仍为 2。 |
| A：通过页面新建期初单 | BLOCKED | 现有 3 个 MDM 仓库均已完成期初建账且 WMS 状态为 `ACTIVE`；按唯一仓库约束不对其重复创建。未新增 MDM 测试仓库。 |
| A：已有期初单详情、刷新编号稳定 | PASS | `OB-00000002` 在列表、详情及刷新后相同；这仅证明既有单据读取行为。 |
| B：通过页面新建盘点单 | PASS | 选择 `QA-EL-FINISHED-WH`，创建 `2112892321782513666`，编号 `IC-00000002`，状态 `DRAFT`。 |
| B：刷新及重新进入 | PASS | 详情刷新、返回列表并重新打开，编号均为 `IC-00000002`。 |
| B：同键同参重放、同键改参 | NOT RUN | 浏览器会话未暴露可安全用于受控 HTTP 的凭据，未直接重放本次真实请求。对应 PostgreSQL 集成测试已通过。 |
| C：盘点规则执行 | PASS | 当前 ENABLED 修订 1 配置 `IC-` + 8 位流水、步长 1、无日期段、`reset=NONE`；流水 1→2，实际 `IC-00000002`，长度 11 ≤ `task_no varchar(32)`。 |
| C：期初规则配置及字段适配 | PASS | ENABLED 修订 1 配置 `OB-` + 8 位流水、步长 1、无日期段、`reset=NONE`；既有 `OB-00000002` 与记录相符，长度 11 ≤ `document_no varchar(32)`。本轮新建执行未验。 |
| D：创建前业务参数校验失败不发号 | NOT RUN | 页面未提交非法业务参数。定向 PostgreSQL 集成测试覆盖仓库未启用与规则停用，但不能替代此浏览器场景。 |
| D：MDM 依赖失败不发号 | PASS | 首次页面盘点创建因隔离 WMS 找不到 MDM 实例，在引用验证阶段失败；任务、Issue 与流水均未增加。 |
| D：事务失败整体回滚 | PASS | 现有 PostgreSQL 集成测试覆盖期初单重复仓库写入失败、规则停用、盘点失败路径，27 项全部通过；未做浏览器故障注入。 |
| D：成功后关闭/重开不发号 | PASS | 盘点单详情离开后重开，流水仍为 2；期初既有详情读取流水仍为 2。期初新草稿未验。 |
| D：删除草稿后编号不复用 | NOT RUN | 为保留验收记录未删除本轮任务；期初无可用仓库。现有测试验证删除草稿后可重新建账，但本轮未直接执行编号不复用的页面路径。 |
| D：已提交但响应丢失 | NOT RUN | 未安全注入“提交成功但响应丢失”；不把首次 MDM 校验前失败等同于该情形。 |
| E：页面、业务表、Issue 与流水一致 | PASS | 新盘点单编号、ID、状态与数据库单行及单条 Issue 一致；发号修订 ID `8300000000000000014`、修订号 1，流水作用域空串、值 2。 |
| E：页面 = 原始 API 响应 = 数据库 | BLOCKED | 未留存浏览器 Network 的原始响应体；不能以详情页面呈现替代原始 API 证据。 |
| 浏览器错误与重复点击 | PASS | 正常创建后无浏览器 console error/warn；保存期间仓库选择及创建按钮禁用。未采集完整 Network HAR，故不宣称所有请求都没有 404/403/500。 |

## 3. 测试数据与截图

本轮唯一新建业务记录：隔离库盘点任务 `2112892321782513666`，`request_key=a24f9f3f-6701-4fe5-90bf-156412e8044e`，仓库 `2112409248611676161`，用途为 Code Rule V1-B 页面创建验收。请求的路径和字段由当前页面 API 代码确认；实际 Network 请求体、HTTP 状态及原始响应未抓包，不将源码推断写成抓包结果。

创建前盘点流水为 1、Issue 1、任务 1；成功后为 2、2、2。新任务 `task_no=IC-00000002`、`status=DRAFT`、`created_at=2026-10-21 13:02:39.833514+00`；Issue `code=IC-00000002`、`rule_id=8300000000000000013`、`revision_id=8300000000000000014`、`revision_no=1`、`created_at=2026-10-21 13:02:39.826342+00`。任务编号与 Issue 各仅一行。规则表和修订表均为当前 ENABLED 修订；`wms_code_rule_sequence` 对应作用域空串，`last_value=2`。业务表分别有 `UNIQUE(document_no)` 与 `UNIQUE(task_no)`，Issue 有 `UNIQUE(rule_id,code)`。

期初新建记录：无。已有 `OB-00000002` 对应期初单 `2112414534193864706`、Rule `8300000000000000001`、Revision `8300000000000000002`、修订号 1；期初流水一直为 2。旧 `OB-2112177340069957633` 是迁移保留的历史编号，不属于本轮编号跳变。

截图：[盘点创建前](evidence/code-rule-v1b-20261021/count-before.png)、[首次环境故障后的未知提示](evidence/code-rule-v1b-20261021/count-unknown.png)、[盘点创建成功详情](evidence/code-rule-v1b-20261021/count-created.png)、[期初列表](evidence/code-rule-v1b-20261021/opening-list.png)、[期初既有详情](evidence/code-rule-v1b-20261021/opening-detail.png)。

日志：首次盘点创建时 WMS 报 `FeignException$ServiceUnavailable: [503] ... Load balancer does not contain an instance for the service mom-mdm-server`。补齐隔离 WMS 的 MDM 静态发现后同一页面正常创建。Maven 日志位于 `.codex/runtime/logs/maven-20261021-210542-76394.log`；`InventoryCountPostgresqlIT` 18 项、`OpeningInventoryPostgresqlIT` 9 项，均 0 失败、0 错误、0 跳过，构建成功。

## 4. 问题清单

### P1：盘点未知结果的页面刷新恢复不可靠

- 现象：首次环境故障后，页面进入“写入结果未知”，创建按钮被阻断；点击核对仍保持未知。刷新页面后阻断与原 `requestKey` 一同消失。新点击会生成新的请求键。
- 复现：选择仓库 → 在隔离服务缺失 MDM 发现时点击创建 → 看到未知提示 → 刷新页面。此轮首次请求经数据库核对未执行到发号阶段；“已提交但响应丢失后重复创建”未注入验证。
- 根因：`InventoryCountListPage.vue` 将 `requestKey` 和 `unknown` 仅存于组件内存；成功后或刷新后不会持久保留原键。后端同键幂等本身存在，但页面丢键后无法按原请求恢复。
- 最小修复建议：在不改业务领域边界的前提下，为未知创建保留可恢复的原请求键与仓库参数，并在重新发起创建前先按原键核对；若无法确认结果，继续阻断并给出人工核对入口。不要自动换键重试。

### P2：隔离验收环境初始服务发现缺失

- 现象：首次页面盘点创建提示未知；WMS 日志中 Feign 查找 `mom-mdm-server` 返回 503，数据库无写入。
- 原因：本轮单独启动的 WMS 未配置 MDM 实例。补齐测试环境静态发现后已正常创建；没有证据表明业务代码本身存在该故障。
- 最小修复建议：验收启动清单明确 Gateway 与 WMS 到 MDM 的服务发现配置，并在页面测试前执行依赖健康检查。

### P2：原本地库尚未迁移到 V17

- 现象：原库 Flyway 最新 V15，缺少 Issue 表；直接对原库运行当前 WMS 会触发 Schema 迁移。
- 原因：当前本地共享库未部署本次 Flyway。隔离副本 V17 已成功执行。
- 最小修复建议：在受控发布流程中完成并验证 V17；不要通过手工建表绕过 Flyway。

## 5. 最终决策

1. 盘点单页面创建与正式发号 **PASS**；期初单本轮新建 **BLOCKED**（无合法未建账仓库），既有编号读取稳定，不能据此声称两种新建都通过。
2. 当前 ENABLED 规则修订实际决定盘点最终单号，已由规则 JSON、页面和 Issue/序列三方核对；期初仅验证既有记录与当前修订相符。
3. 盘点成功提交的单据/Issue/流水一致；失败前置校验未消耗流水。PostgreSQL 集成测试支持事务回滚，但浏览器故障注入未完成。
4. 本轮新盘点编号从 1 到 2，无重复、异常跳变或重置。期初流水未变；历史 ID 风格编号属于迁移数据。
5. 后端盘点同键幂等由代码与 PostgreSQL 集成测试支持，真实新单的受控 HTTP 重放未执行；前端未知结果跨页面刷新恢复不可靠。期初没有同等客户端请求键契约，不能把仓库唯一约束视为精确恢复。
6. 必须优先修复盘点未知结果刷新丢键的恢复路径；原库 V17 迁移及期初专用测试仓库是继续验收的前置条件。
7. **没有证据证明需要调整 Code Rule 架构**，也没有新增 Binding 或 Scenario Registry 的依据。

## 验证命令与结果

- 只读 `docker exec postgres17 psql ...`：核对原库/隔离库 Flyway、规则、修订、流水、Issue、单据及唯一约束；查询成功。
- `MAVEN_BIN=/tmp/apache-maven-3.9.9/bin/mvn bash scripts/codex-mvn-test.sh -pl mom-wms-platform/mom-wms-server -am -Dtest=NoSuchUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpeningInventoryPostgresqlIT,InventoryCountPostgresqlIT -Dfailsafe.failIfNoSpecifiedTests=false verify`：27 个真实 PostgreSQL 集成测试全部通过。系统自带 Maven 3.9.0 的首次执行因版本门禁失败，未计作测试结果。
- 未执行：完整浏览器 Network HAR、同键受控 HTTP 重放、期初新建、浏览器“提交成功但响应丢失”注入、删除后编号不复用页面验证。
