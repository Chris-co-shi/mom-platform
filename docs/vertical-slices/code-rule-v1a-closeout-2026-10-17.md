# 编码规则 V1-A 收口与 System 前端集成验收

验收日期：2026-10-17。实现事实入口仍为两仓库的当前实现地图；本文记录本次范围、验证和风险，不作为其他模块状态的依据。

## 本次边界

- 服务启动配置决定唯一 Owner、固定表名前缀及管理读写权限；请求不能指定 Owner、Schema、表名或 SQL。
- MDM 是当前唯一生产接入 Owner，保留 `mdm_code_rule`、`mdm_code_rule_revision`、`mdm:code-rule:read/write`。
- System 只提供前端导航和 UI 文案；不持久化规则、不代理 MDM 业务。
- 不实现正式编号引擎、计数器、Redis 自增、WMS/MES 生产表或新基础设施；历史 Migration 与生命周期语义不变。

## IMPLEMENTATION REVIEW

1. **实现行为**：公共 Owner 安全校验、Owner 独立管理权限；正式页面提供真实 Catalog、规则分页/筛选、草稿创建和完整 JSON 保存、详情恢复、历史修订、发布、停用与复制修订。
2. **核心文件**：`CodeRuleOwner`、`CodeRuleAutoConfiguration`、`JdbcCodeRuleStore`、`CodeRuleManagementAccess`、`CodeRuleController`；前端 `code-rule-api.ts`、`use-code-rule-management.ts`、`CodeRuleManagementPage.vue`。
3. **实际调用链**：System 页面 → Owner 接入清单 → 现有 httpClient → Gateway `/mdm/code-rules` → MDM Resource Server → 公共 Controller → Application → Owner JDBC Store。
4. **数据归属**：规则和修订 JSONB 写入 MDM 自有表；System V25 只新增 31 条页面文案和 62 条中英文译文。测试 Owner 的 Schema/表只存在于隔离 Testcontainers 测试中。
5. **事务边界**：现有 Application 的本地 `@Transactional` 保持不变。Store 装配检查本地事务管理器与注入 DataSource 为同一实例；不支持跨 Owner 事务。
6. **核心约束**：Owner 只接受 1～44 位小写 ASCII 标识符，固定后缀后不超过 PostgreSQL 63 字节；权限必须显式配置且属于当前 Owner，身份或配置缺失拒绝管理访问。Catalog 仍要求 Resource Server 认证，不变成匿名接口。
7. **并发与失败**：规则级 `ruleVersion`、规则行锁、唯一草稿/已发布约束沿用后端。前端不自动重试写入；409、403、未知结果及写响应损坏阻止再次提交，显式重读才恢复。保存期间卸载交互设计器，避免提交响应覆盖键盘新输入。读取取消旧请求并用序号防止旧响应覆盖新状态。
8. **历史与编辑**：已发布/停用定义只读；当前 Catalog 不识别的 Handler/Provider、目录失败或空目录时保留完整定义供查看，不删改未知片段。切换规则、Owner、关闭或离开页面时，未保存内容必须明确丢弃。
9. **本次未解决**：正式编号生成；未来 WMS/MES 业务；跨服务一致性和消息机制；生产部署的静态站点导航回退。
10. **建议用户重点 Review**：Owner 与权限配置、DataSource/事务同源断言、写入未知结果的核对流程、Designer 克隆边界、发布/修订所用 revisionId 与 ruleVersion。
11. **最容易出问题处**：前端接入清单与服务启动配置不一致；历史 Provider 退出；创建超时后暂时查不到结果；页面与 API 同前缀的生产托管规则。

## DESIGN ISSUE 与修正

- 原公共模块限制唯一 `mdm` Owner、Controller 固定 MDM 权限：按本次设计改为可信启动配置和权限 Access Bean。
- 浏览器直接访问 `/system/code-rules` 被 Vite 的 `/system` API 代理拦截：仅 HTML GET 导航返回 SPA，JSON/SSE 保持原 Gateway 路径。原代理路径断言全部保留。
- 真实拖拽暴露设计器契约缺陷：拖拽默认克隆 Catalog 元数据，画布在 `add` 事件转换前读到缺少 `id/params` 的对象并报错。按用户允许的“真实契约缺陷”例外最小修改：源 Catalog 在 clone 阶段生成完整片段，add 只选中片段，不重新创建 ID。增加“model 更新先于 add”回归，保留开发验收页及所有原测试。
- System 测试固定 Flyway V24，本次更新为 V25；旧 V1 测试补齐与现有 Store 集成测试相同的主配置加载，避免测试 YAML 遮蔽真实 Owner 装配。

## 自动化验证

- Maven 使用本机已有 3.9.11；默认 3.9.0 被 Enforcer 拦截后改用合规运行时，未降低门禁。
- 定向 Maven：`CodeRuleAutoConfigurationTest`、`JdbcCodeRuleStoreOwnerTest`、`JdbcCodeRuleStorePaginationTest`、`CodeRuleManagementAccessTest`、`CodeRuleWebMvcAutoConfigurationTest`、`MdmCodeRulePostgresqlIT`、`SystemI18nStorePostgresqlIT`、`SystemV1PostgresqlIT` 均通过且未跳过。
- MDM 13 项真实 PostgreSQL/Redis 测试包含 JSONB、修订、版本冲突、并发发布、事务回滚、独立 Owner DataSource/表/目录和真实方法安全。
- System 11 项 Store 集成测试包含空库双语 Bundle 及 V24→V25 升级，逐行对比原定义与译文（含管理员自定义版本/审计）未变；另有 6 项 V1 集成测试通过。
- `BASE_REF=HEAD MAVEN_BIN=<本机 Maven 3.9.11> bash scripts/codex-verify-changed.sh`：工程基线与 106 项变更范围单元测试通过，比较包含工作区和新增文件。
- `pnpm verify`：lint、typecheck、40 个测试文件/150 项测试和生产构建全部通过，包含原开发验收页与原代理契约测试。
- 两仓库 `git diff --check` 通过。无生产依赖变更，无文件删除，无 Git 提交/推送。

## 独立审查

独立代理完成两仓当前 diff 的只读审查，未发现可复现 Critical/Important。审查检查了安装的 vue-draggable-plus 0.6.1 源码，确认源 clone 先创建完整对象、目标更新 model 后 add 提供 clonedData；审查未代替真实浏览器验收或测试。

## 运行验收

- 本机 Docker PostgreSQL 17、Redis 可用；本次构建的 MDM、System、Gateway 分别使用 21200、21300、21000 端口，前端使用 localhost:5173。Auth 使用既有本地 20001 实例，保留 Resource Server 认证。
- 本地 System 经 Flyway 已应用 V25；没有直接手工写业务 Schema。
- 真实浏览器使用人工登录的现有账号；不创建测试授权、不重置密码。
- 真实浏览器闭环已完成：创建草稿 → 从真实 Catalog 拖拽固定字符、日期、业务字段、流水号 → 保存 → 刷新页面并重新查询 → 发布第 1 修订 → 创建第 2 修订 → 修改前缀并保存发布。另验证了显式停用后重新发布。
- 刷新恢复、复制修订均比较完整 JSON：segment ID、顺序、type、params 保持一致；第 2 修订只将固定前缀从 `V1A-` 改为 `V1B-`，第 1 修订历史定义未变。已发布详情只读，无可编辑设计器。
- 验证了未保存关闭提示及取消后保留编辑内容、Owner 选项仅 MDM、状态筛选只返回符合状态的修订、历史查询返回两个修订。
- 数据库只读复核：规则 `v1a.browser.20261017`，ruleId `2111467585173131266`，ruleVersion `7`；第 1 修订 `2111467585173131267` 为 DISABLED，第 2 修订 `2111467907673165826` 为 ENABLED。验收数据保留供人工复查。
- 页面截图保存在 `.codex/runtime/screenshots/code-rule-v1a-browser-20261017.jpg`。浏览器已恢复简体中文及默认视口，保留结果标签页。
- 初次浏览器验收发现两项轻微交互问题（后续修正见下节）：①语言切换后状态选择框保留原语言标签，展开选项、重新选择后正常；②点击历史修订会按规则编码查询全部修订，但筛选框仍保留此前状态值，可能让用户误以为结果仍受该状态限制。
- 403、409、网络超时/未知写入结果、目录失败与缺失 Provider 等异常仍以此前自动化测试为证据，本轮未在真实浏览器进行故障注入，不宣称已完成这些异常的真实端到端验收。

## 页面可读性修正（验收反馈）

- 本轮范围仅前三项：可读详情、历史浏览、搜索布局。用户确认“规则名称/用途”留待单独设计；没有猜测用途或扩展业务 API/数据模型。
- 详情使用 Catalog 中的片段、参数和选项名称，按已保存顺序展示；不补默认参数、不生成编号、不改写 definition。未知片段/Provider 选项继续展示原值并提示；技术身份、ruleVersion 和完整 JSON 默认折叠。
- 列表移除主要视图中的内部数据版本列，补充编码组成摘要；历史模式有独立说明和返回入口，并隐藏不参与历史请求的普通筛选。搜索区独占一行，对齐可见字段标签与控件，状态选择随 Locale 重建以更新显示标签。
- 新 V26 仅增加 System 的 7 个 Key、14 条中英译文，总计编码规则文案 38 个 Key/76 条译文；不修改 V25 或管理员自定义内容。本地验收 System 已通过 Flyway 应用 V26。
- 验证：页面先红后绿，完整 `pnpm verify` 为 40 文件/152 项测试通过（日志 `/tmp/mom-v1a-ux-final-verify.log`）。System PostgreSQL Store 12 项、System V1 6 项测试通过，覆盖空库及 V24/V25 升级、原记录和自定义译文逐行保留；日志 `.codex/runtime/logs/maven-20261017-225754-23894.log`。变更范围后端验证脚本通过，日志 `/tmp/mom-v1a-ux-backend-verify.log`。
- 真实浏览器检查了已发布详情、历史模式及返回、中文/英文状态标签、亮色/暗色；390/768/1024/1280/1440/1920/2560 采样未出现页面整体横向溢出，窄屏表格在自己的容器滚动。未重新执行业务发布写入；原验收规则继续保留。
- 截图：`.codex/runtime/screenshots/code-rule-readable-detail-20261017.jpg`、`code-rule-readable-history-20261017.jpg`、`code-rule-readable-search-20261017.jpg`。
- 本轮修改：前端 `CodeRuleManagementPage.vue`、`CodeRuleManagementPage.test.ts`、`code-rule-management.css`、`src/locales/zh-CN.ts`、当前实现地图；后端 System 两个集成测试、测试 application.yml 和本文。新增 `CodeRuleDefinitionView.vue` 与 `V26__seed_readable_code_rule_views.sql`，无删除文件。

## 使用场景与说明扩展（第四项）

用户后续确认增加使用场景与文案描述，本节取代前一轮“第四项另行设计”的待办。按此前提出的规则级元信息方案实施；没有新增业务绑定、名称字典或编号生成用例。

### IMPLEMENTATION REVIEW

- **实现与数据归属**：`usageScenario`（新建/保存草稿必填，最长 120 字符）和 `description`（选填，最长 1000 字符）持久化在 Owner 的规则身份行，各修订共用。MDM V131 新增 nullable 字段，旧数据、JSONB、审计和版本原样保留。用户内容是纯文本业务数据，不进入 System 翻译库。
- **调用链**：页面 → 原 `/mdm/code-rules` 创建或 `PUT /mdm/code-rules/{revisionId}` → Owner 方法权限 → Application → 现有 JDBC Store。创建仍同一事务写身份和首个草稿；草稿保存仍先锁规则并核对 `ruleVersion`，再在同一事务保存说明、definition 并推进版本/审计。任何失败整体回滚。
- **历史语义**：只有草稿保存可以修改说明；发布或停用状态不能直接修改。说明是规则级共享标签，修改后各历史修订显示当前规则说明；修订 definition 保持各自历史内容，复制修订不丢失规则说明。
- **前端**：列表主标题为使用场景，下方显示编码和说明；创建/草稿编辑提供两个字段，详情和历史可查看。旧规则显示“未填写使用场景”；元信息变化纳入未保存保护、Owner 重置、写入互斥和冲突/未知结果保护。
- **DESIGN ISSUE / 契约变化（YES，可实现）**：创建和草稿保存新增必填 `usageScenario`，客户端须同步升级；不是无条件兼容的新增返回字段。创建请求现在为 `{ruleCode,usageScenario,description?,definition}`；草稿保存为 `{usageScenario,description?,definition,ruleVersion}`。其他路径、revisionId 语义、权限、启停与版本规则不变。部署时先应用 Owner Migration/后端，再发布前端；其他直接调用方补齐使用场景。
- **文案复用**：说明标签复用实库核对过的 `system.common.description`（中英文“说明/Description”）；System V27 只增加使用场景、旧数据提示和长度校验的 3 个 Key/6 条译文，总计编码规则界面 41 个 Key/82 条译文。既有 Migration 和自定义译文不改。
- **测试**：MDM 17 项真实 PostgreSQL/Redis 集成测试通过，增加 HTTP 字段保存、边界校验、规则族共享、版本冲突、不可变发布、模拟定义失败后说明/版本回滚，以及 V130→V131 数据保留；日志 `.codex/runtime/logs/maven-20261017-231438-28481.log`。System 13 项 Store 和 6 项 V1 集成测试通过，覆盖 V24/V25/V26 升级、空库、原定义/自定义译文保留；日志 `.codex/runtime/logs/maven-20261017-231545-28923.log`。无跳过。
- **最终门禁**：`pnpm verify` 40 文件/155 项测试、lint/typecheck/build 通过（`/tmp/mom-metadata-web-verify.log`）；`BASE_REF=HEAD MAVEN_BIN=<3.9.11> bash scripts/codex-verify-changed.sh` 通过（`/tmp/mom-metadata-backend-verify.log`）；两仓 `git diff --check` 通过。
- **真实浏览器**：创建 `v1a.metadata.20261017`，填入“物料主数据编码”和说明，配置固定文本/流水片段后保存；仅修改说明时关闭触发未保存保护，取消后保存成功。刷新后列表及详情完整恢复两个字段。数据库只读复核 ruleId `2111476877649444866`、revisionId `2111476877649444867`、ruleVersion `1`、DRAFT。保留该验收草稿；未改动之前的 `v1a.browser.20261017`。本地 MDM/System 已分别应用 V131/V27。
- **Review 重点与风险**：规则级说明与修订定义的差异；必填契约升级；事务中元信息/定义/版本的共同回滚；历史空值提示。当前没有增加按用途的搜索接口、场景业务绑定、独立元信息编辑 API或正式编号生成。

本轮修改：公共 `CodeRuleView`、Application 接口/实现、Store 接口/JDBC、Controller；MDM 集成测试；System 两个集成测试和测试 application.yml；前端 API、model、页面及既有对应测试、CSS、Key 契约和两仓当前实现地图、本文。新增 MDM `V131__add_code_rule_usage_metadata.sql`、System `V27__seed_code_rule_usage_metadata_messages.sql`；无删除文件。

## DESIGN PRESSURE TEST（不继续扩展实现）

| 分类 | 场景 | 当前判断 |
|---|---|---|
| A 当前 V1 必须解决 | Owner 请求注入、跨 Owner 管理权限、Store 与事务错用数据源 | 启动标识符/权限校验和同源断言覆盖；最终授权仍在 Resource Server/方法安全 |
| A 当前 V1 必须解决 | 双击保存、版本过期、旧读取覆盖新 Owner、提交响应覆盖新输入 | 提交互斥、规则级版本、请求序号和保存期间不可编辑覆盖 |
| A 当前 V1 必须解决 | 网络中断、响应损坏、创建结果未知 | 阻止重放并显式核对；创建查不到时继续阻断，不能凭空判定失败 |
| A 当前 V1 必须解决 | Handler/Provider 退出、已发布历史不可编辑 | 历史 JSON 保留，只读提示；发布由后端重新验证当前能力 |
| B 当前可接受但未来需解决 | 多工厂/组织、单位、批次、供应商与 SAP 编码归属 | 当前只管理定义；真正接入编号用例前由用户决定上下文和数据所有权 |
| B 当前可接受但未来需解决 | 规则和修订数据增长 | 当前服务端分页；按真实数据和查询模式评估，不预建无依据索引 |
| B 当前可接受但未来需解决 | 高可用重启、历史兼容、生产数据库账号权限 | 定义持久化和本地事务已有保障；部署时继续限制 Owner Schema 权限，未来插件升级需兼容策略 |
| C 当前属于过度设计 | Counter、Redis 自增、跨服务 Saga/Outbox、动态脚本、通用状态机 | 不进入本次 V1-A |

## 文件清单

### mom-platform

修改：

- `docs/当前实现地图.md`
- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/autoconfigure/CodeRuleAutoConfiguration.java`
- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/CodeRuleView.java`
- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/application/CodeRuleApplication.java`
- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/application/DefaultCodeRuleApplication.java`
- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/store/CodeRuleStore.java`
- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/store/JdbcCodeRuleStore.java`
- `mom-framework/mom-code-rule/src/test/java/io/github/chrisshi/mom/coderule/autoconfigure/CodeRuleAutoConfigurationTest.java`
- `mom-framework/mom-webmvc/src/main/java/io/github/chrisshi/mom/webmvc/autoconfigure/CodeRuleWebMvcAutoConfiguration.java`
- `mom-framework/mom-webmvc/src/main/java/io/github/chrisshi/mom/webmvc/coderule/CodeRuleController.java`
- `mom-mdm-platform/mom-mdm-server/src/main/resources/application.yml`
- `mom-mdm-platform/mom-mdm-server/src/test/java/io/github/chrisshi/mom/mdm/MdmCodeRulePostgresqlIT.java`
- `mom-system-platform/mom-system-server/src/test/java/io/github/chrisshi/mom/system/SystemI18nStorePostgresqlIT.java`
- `mom-system-platform/mom-system-server/src/test/java/io/github/chrisshi/mom/system/SystemV1PostgresqlIT.java`
- `mom-system-platform/mom-system-server/src/test/resources/application.yml`

新增：

- `mom-framework/mom-code-rule/src/main/java/io/github/chrisshi/mom/coderule/CodeRuleOwner.java`
- `mom-framework/mom-code-rule/src/test/java/io/github/chrisshi/mom/coderule/store/JdbcCodeRuleStoreOwnerTest.java`
- `mom-framework/mom-webmvc/src/main/java/io/github/chrisshi/mom/webmvc/coderule/CodeRuleManagementAccess.java`
- `mom-framework/mom-webmvc/src/test/java/io/github/chrisshi/mom/webmvc/coderule/CodeRuleManagementAccessTest.java`
- `mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V25__seed_code_rule_management_messages.sql`
- `mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V26__seed_readable_code_rule_views.sql`
- `mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V131__add_code_rule_usage_metadata.sql`
- `mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V27__seed_code_rule_usage_metadata_messages.sql`
- `docs/vertical-slices/code-rule-v1a-closeout-2026-10-17.md`

删除：无。

### mom-web

修改：

- `docs/frontend/当前实现地图.md`
- `src/locales/zh-CN.ts`
- `src/modules/system/routes.ts`
- `src/router/route-names.ts`
- `src/router/route-registry.test.ts`
- `src/shared/form-designer/CodeRuleDesigner.vue`
- `src/shared/form-designer/components.test.ts`
- `vite.config.test.ts`
- `vite.config.ts`

新增：

- `src/modules/system/api/code-rule-api.test.ts`
- `src/modules/system/api/code-rule-api.ts`
- `src/modules/system/model/use-code-rule-management.test.ts`
- `src/modules/system/model/use-code-rule-management.ts`
- `src/modules/system/pages/CodeRuleManagementPage.test.ts`
- `src/modules/system/pages/CodeRuleManagementPage.vue`
- `src/modules/system/pages/CodeRuleDefinitionView.vue`
- `src/modules/system/pages/code-rule-management.css`

删除：无。

## 未完成事项与风险

真实主闭环验收通过；两项轻微交互问题已在后续可读性修正中解决；使用场景与说明已按用户后续要求实现；业务场景绑定尚未建模。异常故障未做真实浏览器注入。生产托管必须为 HTML 页面导航提供 SPA 回退且保留 Gateway API；原设计器与 Handler Metadata 的既有中文文案未进行无关国际化重写。未来 Owner 接入仍须提供本地表 Migration、数据源和权限配置，当前未创建 WMS/MES 生产功能。
