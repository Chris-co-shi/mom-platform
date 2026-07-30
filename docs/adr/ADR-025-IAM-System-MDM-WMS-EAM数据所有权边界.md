# ADR-025：IAM、System、MDM、WMS、EAM 数据所有权边界

- 状态：Accepted
- 提出日期：2026-07-29
- 接受日期：2026-07-29
- 决策 Slice：P1.6 S11-A（草案）/ S11-B（接受）
- 决策人：Chris
- 关联：[ADR-023](ADR-023-Locale时区与用户偏好边界.md)、[ADR-024](ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md)、[S11 现状与迁移边界报告](../engineering/P1.6-S11-数据所有权现状与迁移边界报告.md)

> **Accepted Decision。** Chris 于 2026-07-29 接受方案 C 与本 ADR 第 23 节七项决策，S11 数据所有权边界已经冻结。本次仍只完成文档收口：不创建 `mom-system-platform`，不修改 IAM、API、Schema、Flyway 或运行时行为。Accepted 不会自动实施 S12；S12 只能由独立任务启动，且不得提前实现 S13～S17。

## 1. Context

P1.6 S01～S10 已建立工程规范并封板 IAM 安全基线。IAM 当前真实存储账号、凭据、RBAC、Factory Scope、Party Binding、Mobile Access、OAuth Client Policy、Session、Refresh Token 和安全审计；未来 System 候选能力包括参数、通用字典、用户偏好、应用目录、菜单与导航。与此同时，现有架构规划把 Factory、组织、人员、Party 核心身份与单位放在 MDM，并把供应商采购关系、客户销售关系分别留给未来采购/SRM 与销售/CRM 业务域，把 Warehouse 放在 WMS，把设备台账放在 EAM。

如果只按“管理页面”或类型名称拆分，会把安全权威、体验配置和业务主数据重新混合。本 ADR 以 Migration、Entity、Repository、Application Service、API 与固定前端调用方为证据，裁决单一写入权威、允许引用、可重建投影、用户偏好和禁止复制的边界。

## 2. Problem

需要回答：每个对象由谁定义生命周期、状态、唯一性和写事务；其他领域可以保存什么；权威不可用或引用失效时如何处理；现有 IAM 是否包含真正应迁往 System 的生产数据；以及后续 S12～S18 可以建设什么而不形成第二授权权威或共享数据库。

关键概念必须保持分离：

```text
IAM Account ≠ Employee/Person Master Data
MDM Factory Master ≠ IAM Factory Authorization Scope ≠ System Factory Preference
MDM Party Core Identity ≠ Supplier Procurement Relationship ≠ Customer Sales Relationship ≠ IAM Party Binding
IAM OAuth Client / Permission ≠ System Application Catalog / Menu ≠ Web Router
```

## 3. Decision Drivers

1. 每个对象只有一个写入和语义权威，禁止双主、共享表和跨 Schema 写入。
2. IAM 必须继续独立完成认证、授权、Session、Token 与撤销；展示或配置服务不可成为安全前置依赖。
3. 跨领域优先保存稳定 ID/Code；只有查询或展示需要时才建立可重建 Projection。
4. Preference 不能扩大 Permission、Factory Scope 或 Party Binding。
5. Warehouse 与设备台账必须服从专业领域生命周期，而不是被“平台字典”吸收。
6. 当前尚无调用方或生产数据的能力不应为体现阶段成果而提前实现或迁移。
7. 决策必须可演进、可回滚，并明确权威不可用时的 Fail Closed/降级边界。

## 4. Current Evidence

- IAM `V1`～`V8` Migration 建立 `iam_user`、最小 `employee_no`、外部 Party Binding、Role/Permission、User Role、Factory Scope、Mobile Access、Client Policy、Session/Refresh 与追加型安全审计；没有 Factory 名称/地址/时区、完整人员、组织、仓库、设备、单位、菜单或偏好表。
- `iam_user_factory_scope.factory_id` 的数据库注释明确其为 MDM Factory 引用；`iam_external_user_binding.party_id` 明确不复制主体字段且无跨 Schema 外键。
- `IamAuthorizationContextLoader` 从 IAM Repository 实时加载账号、Role、Permission、Factory ID 与 Party Binding；`IamJwtClaimsAssembler` 只接受该权威快照。
- `/api/iam/me` 重新读取 IAM 授权数据并校验请求 Factory；`currentFactoryId` 是经 Scope 校验后的请求上下文，不是 Factory 主数据。
- `IamExternalFactoryScopeVerifier` 明确 Factory 与 Party 关系不属于 IAM；缺少权威适配器时对非空外部 Factory Scope Fail Closed。当前未发现独立的 Party 主数据状态校验适配器，是后续集成缺口。
- `IamAdminService` 是六个 IAM 应用服务的兼容 Facade，管理的对象均为 IAM 安全权威；页面位于“系统管理”不改变其所有权。
- 当前 MDM 生产源码仅有技术探针，历史技术表已由 Migration 删除；WMS/EAM 没有 Warehouse/Equipment 生产模型。相关对象是已规划权威，不是已实现能力。
- mom-web 固定 Ref `40106d3…`：Locale/Timezone/Theme 保存在浏览器偏好缓存；currentFactory 本地保存后通过 `/api/iam/me` 重校验；IAM 菜单为静态元数据并引用 Permission Code。
- mom-mobile 固定 Ref `ab357ee…`：Client ID 固定为 `mom-mobile-pda`；currentFactory 是内存选择并限制在 `/api/iam/me.factoryIds`；离线命令保存授权快照，发送前按当前 Session、Factory、Permission、Party 再校验；未发现 Locale/Timezone 或主数据副本。

完整路径与字段证据见 S11 报告。

## 5. Ownership Classification

| 分类 | 定义 | 约束 |
|---|---|---|
| Own | 唯一写入与语义权威 | 定义生命周期、状态、唯一性、事务并发布事实 |
| Reference | 指向权威对象的稳定 ID/Code | 不复制完整主数据；必须定义失效、删除和重编码策略 |
| Projection | 可重建的只读查询/展示副本 | 记录来源版本或更新时间；可最终一致；不接受独立业务写入 |
| Preference | 用户体验选择 | 不扩大权限、不替代主数据、不进入授权 Claim；无效时回退或重校验 |
| Forbidden Copy | 不得复制的数据 | 包括凭据、Token 明文、Permission 权威、Session 状态、Secret 和不可安全同步的安全状态 |

## 6. Recommended Decision

采用方案 C：“按权威领域拆分，System 只承载平台配置与体验数据”。

- IAM Own 账号、凭据、账号安全状态、User Type、Role、Permission、用户授权关系、Factory Scope、Party Binding、Mobile Access、OAuth Client/Client Policy、Session、Refresh、revoked sid、JWT Claims 规则和 IAM Security Audit。
- System Own 后续独立 Slice 批准的平台配置与体验数据：GLOBAL/APPLICATION 类型化参数、受限通用字典、用户偏好/视图设置、Application Catalog、Menu、Navigation。Dynamic I18n 只有真实调用方时实施；跨领域 Audit Projection 当前暂缓。
- MDM Own Factory、Organization、Department、Person/Employee、Party 核心身份与主体主数据、Unit Directory 与 Conversion。Party 核心包括 Party ID/Code/Type、名称、基础启停状态、基础联系人身份与通用联系方式，以及 Supplier/Customer 的统一主体身份。
- 采购/SRM 业务域是供应商准入、评级、采购关系、供货能力、采购结算关系、供应商绩效及采购业务状态的 Recommended Future Owner / Planned Authority；当前 Not Implemented。
- 销售/CRM 业务域是客户等级、销售关系、信用、价格关系、收款条件及销售业务状态的 Recommended Future Owner / Planned Authority；当前 Not Implemented。
- IAM 只 Own 账号与 Party 的身份绑定、Party Binding 授权关系，以及由此引起的 Session、Claim 和撤销行为；不保存 Party、Supplier、Customer 完整业务主数据。
- WMS Own Warehouse、Storage Location/Bin、Warehouse Status 与 Factory-Warehouse 业务关系。
- EAM Own Equipment Asset、Machine、设备台账状态及 Factory/Location 资产归属；Device Credential 预留给未来 Device IAM/IoT Security。
- MES/制造资源边界推荐 Own Production Line/Work Center 的生产能力与排程语义；EAM 只引用并管理挂接设备，最终对象形态由相应业务 Slice 决定。

当前 IAM 无生产数据需要迁移，结论为 **No current production migration required**；`IamAdminService` 保留 IAM，不迁移。
## 7. Object Ownership Matrix

| 对象 | 当前事实 | 权威领域 | 其他领域分类 | 可保存内容 | 禁止复制 | 一致性方式 | 迁移结论 |
|---|---|---|---|---|---|---|---|
| User Account | IAM 已实现 | IAM Own | System/MDM Reference | `userId` | 凭据、锁定、授权关系 | IAM 同步校验/事件失效 | 不迁移 |
| Username | IAM 已实现且唯一 | IAM Own | 展示 Projection 可选 | 脱敏展示值 | 登录校验副本 | IAM 查询 | 不迁移 |
| Display Name | IAM 已实现账号显示标签 | IAM Own 独立 `displayName` | MDM `personName` 是不同权威字段；客户端可展示 | `userId + displayName + updatedAt` | 完整 Person 档案、法定姓名权威副本 | 可在明确用例中由 MDM 初始化；不形成持续双向同步；IAM 不依赖 MDM 可用性 | 不迁移 |
| Credential | IAM 已实现摘要 | IAM Own | Forbidden Copy | 无 | 密码、摘要、Pepper、重置状态 | 仅 IAM 本地事务 | 不迁移 |
| Account/Lock/First Login | IAM 已实现 | IAM Own | Forbidden Copy/只读结果 | 必要的泛化状态结果 | 失败次数、锁定细节权威副本 | IAM 实时校验 | 不迁移 |
| User Type/Mobile Access | IAM 已实现 | IAM Own | Reference/只读授权结果 | 当前入口判断结果 | 第二套授权状态 | IAM 实时校验、Session 撤销 | 不迁移 |
| Person/Employee | 仅 `employee_no` 最小引用；完整档案不存在 | MDM Own（V1 Accepted） | IAM Reference | `personId/employeeId` 或过渡期 `employeeNo` | 联系方式、组织岗位全档案 | API/事件；权威不可用不扩大权限 | 未来模型新建；无现有生产迁移 |
| Organization/Department/Position | 未实现，仅文档规划 | MDM Own（V1 Accepted） | Reference/Projection | 稳定 ID/Code、展示投影 | 组织生命周期副本 | API/事件 | 未来新建 |
| Role | IAM 已实现 | IAM Own | System Reference | Role ID/Code 仅展示需要时 | Role 定义与分配权威副本 | IAM API | 不迁移 |
| Permission | IAM 已实现且 Flyway/代码管理 | IAM Own | System Reference | Permission Code | Permission 名单第二权威 | 发布时受控引用校验 | 不迁移 |
| User Role | IAM 已实现 | IAM Own | Forbidden Copy | 无；调用方只消费结果 | 分配关系 | IAM 实时校验/短期 Token 快照 | 不迁移 |
| Factory Scope | IAM 已实现 ID 集合 | IAM Own | System 禁止作为 Preference 保存 | 仅 IAM `factoryId` 引用 | Factory 属性、Scope 第二权威 | MDM ID 校验 + IAM 授权事务 | 不迁移 |
| Current Authorization Context/JWT Claims | IAM 已实现 | IAM Own | 客户端短期只读快照 | 已签名 Claims/`/me` 结果 | 客户端自报 Role/Scope/Party | 每次签发与 `/me` 重载；Fail Closed | 不迁移 |
| OAuth Client/Client Policy | IAM/SAS 已实现 | IAM Own | System Reference | Client ID | Secret、Redirect/Grant 安全策略副本 | IAM 校验 | 不迁移 |
| Session/Refresh/revoked sid | IAM 已实现 | IAM Own | Forbidden Copy | 客户端仅持协议要求的 Token；System 无副本 | Session 权威、Refresh 明文/摘要、撤销状态 | IAM 本地事务 + Redis revoked sid | 不迁移 |
| IAM Security Audit | IAM 已实现追加写 | IAM Own | 当前无跨域 Projection | 无 | 原始事件权威写入 | IAM 本地追加写 | 不迁移；统一审计暂缓 |
| Factory | 仅 IAM 引用；主数据未实现 | MDM Own | IAM/System/WMS Reference | `factoryId`；必要展示 Projection | 名称、地址、时区在 IAM/System 的权威副本 | MDM API/事件 | 未来 MDM 新建 |
| Factory Code/Name/Status/Address/Timezone | 未实现 | MDM Own | Projection/Reference | 展示字段、来源版本、更新时间 | 独立写入 | API/事件；停用事件 | 未来 MDM 新建 |
| Default/Last Factory | Web 当前本地偏好 | System Preference | 客户端缓存 | `factoryId`、版本、更新时间 | Factory Scope | 读取后 IAM Scope + MDM 状态重校验 | S16 候选；非 IAM 迁移 |
| Current Request Factory | Web/Mobile 发送 `X-Factory-Id` | 请求上下文；IAM/业务服务校验 | 临时值 | 经校验的 `factoryId` | 持久化为授权权威 | 每请求 Fail Closed | 不迁移 |
| Party Core Identity | IAM 仅绑定 ID；主数据未实现 | MDM Own（V1 Accepted） | IAM Reference | `partyType + partyId` | Party 核心身份与主体主数据副本 | 权威校验/状态事件 | 未来 MDM 新建 |
| Party Binding | IAM 已实现且一账号最多一个 Party | IAM Own | Forbidden Copy | 业务只消费经校验结果 | 绑定关系第二权威 | Party 状态校验 + IAM 事务/撤销 | 不迁移 |
| Supplier Procurement Relationship | Not Implemented | 采购/SRM Planned Authority；Recommended Future Owner | MDM Party Reference；IAM 仅 Party Binding | Party ID 与采购业务事实 | IAM/MDM 中的采购关系第二权威 | API/事件 | 未来采购/SRM Slice 新建 |
| Customer Sales Relationship | Not Implemented | 销售/CRM Planned Authority；Recommended Future Owner | MDM Party Reference；IAM 仅 Party Binding | Party ID 与销售业务事实 | IAM/MDM 中的销售关系第二权威 | API/事件 | 未来销售/CRM Slice 新建 |
| Base Contact Identity | 仅文档规划 | MDM Own | 业务域 Reference/业务快照 | 基础联系人身份与通用联系方式 | IAM 内完整联系人主数据 | API/事件 | 未来 MDM 新建 |
| Warehouse | 未实现 | WMS Own | 其他领域 Reference | Warehouse ID | 通用字典副本 | WMS API/事件 | 未来 WMS 新建 |
| Storage Location/Bin/Warehouse Status | 未实现 | WMS Own | Reference/Projection | Location ID、业务历史快照 | System/IAM 权威副本 | WMS API/事件 | 未来 WMS 新建 |
| Factory-Warehouse Relation | 未实现 | WMS Own | MDM Factory Reference | Factory ID + Warehouse ID | IAM Warehouse Scope（无需求时） | WMS 事务，MDM Factory 校验 | 未来 WMS 新建 |
| Equipment Asset/Machine/Status | 未实现 | EAM Own | 其他领域 Reference/Projection | Equipment ID；业务历史快照 | System 字典或当前 IAM 副本 | EAM API/事件 | 未来 EAM 新建 |
| Device Identity/IoT Connection | 未实现 | EAM Own（业务身份/连接归属） | IoT Security Reference | Device/Equipment ID、连接端点标识 | 用户 IAM 账号模型复用 | EAM 与未来 IoT Security 契约 | 未来新建 |
| Device Credential | 未实现 | Future Device IAM/IoT Security Own | Forbidden Copy | EAM 只存凭据引用/状态摘要 | Secret、证书私钥、Token | 专用安全边界；Fail Closed | 非本阶段 |
| Unit Directory/Conversion | 未实现 | MDM Own | 业务 Reference + 历史快照 | Unit Code、单据换算快照 | System 通用字典替代模型 | MDM API/版本；单据固化 | 未来 MDM 新建 |
| Global Parameter | 未实现 | System Own | 客户端/服务 Reference | 类型化值、Scope、Version、默认值、审计、Sensitive 标记 | Secret | API + 可重建缓存/通知 | S13 新建，不迁移 IAM |
| Application Parameter | 未实现 | System Own | 应用 Reference | 同上，加 Application Scope | Client Secret/OAuth 安全策略 | API + 缓存/版本 | S13 新建 |
| Dictionary | 未实现 | System Own（仅通用低变化非权威） | Reference | Code、Label、Locale、Version | Factory/Warehouse/Equipment/Supplier/Customer/Unit/Permission/Role/安全状态 | API + 可重建缓存 | S14 新建 |
| Locale/Display Timezone/Theme | Web 本地；Mobile 未实现 | System Preference | 客户端缓存 | 白名单值、Version | 授权 Claim、Factory Timezone | System API；失败回退默认 | S16 候选 |
| View Setting/Table Columns/Sorting/Saved Filters | Web 框架存在部分本地能力；无服务端模型 | System Preference | 客户端缓存 | 类型化视图键和值 | 任意深层 JSON、权限与业务事实 | System API + 乐观版本 | S16 候选 |
| Dashboard/Favorites | 未发现业务实现 | System Preference | 客户端缓存 | 稳定对象引用与布局 | 对象权威副本 | System API + 引用校验 | 未来 S16 候选 |
| Application Catalog | Web 应用入口静态、IAM 有 Client Policy | System Own | IAM Client ID 可选 Reference | 独立稳定 `applicationCode`、名称、Icon、Entry URL、Sort、Enabled；可无 OAuth Client，未来可一应用多 Client | Client Secret、Redirect URI、Grant、Scope、Client Policy、安全协议配置 | 发布校验/API/缓存 | S17 新建；不与 IAM OAuth Client 等同 |
| Menu/Navigation | Web IAM 菜单静态 | System Own（动态目录）；Web Own 路由组件 | Permission Code Reference | 树、Route Key、排序、Feature Flag、Permission Code | Permission 权威列表 | System API；服务端仍授权 | S17 候选，静态回退兼容 |
| Dynamic I18n | 无真实调用方 | System Own（条件性） | 客户端缓存 | Key、Locale、Version | 领域主数据多语言字段 | API/缓存；不可用回退静态资源 | S15 保持条件性 |
| Business Audit | 各业务域尚待实现 | 对应业务领域 Own | 当前无跨域 Projection | 无 | 原始审计跨域写入 | 对应业务域本地写入 | 不迁移；统一审计暂缓 |

## 8. IAM Boundary

IAM 保留 User Account、Username、独立账号显示标签 `displayName`、Credential、账号状态/锁定/首次改密、User Type、Mobile Access、Role、Permission、User Role、Factory Scope、Party Binding、Client Policy、OAuth Client、Session、Refresh、revoked sid、JWT Claims、`/api/iam/me` 和 Security Audit。

`IAM displayName` 是账号显示标签/安全界面显示名称；`MDM personName` 是人员主数据中的正式姓名、首选姓名或业务姓名。两者不是同一个权威字段。IAM `displayName` 不是 Person Profile，不代表法定姓名或完整员工姓名。它可以在账号创建等明确用例中由 MDM 人员主数据初始化，但初始化不形成持续双向同步；IAM 登录和安全页面不依赖 MDM 实时可用。未来若需要同步，必须通过独立方案定义来源、冲突处理、更新时间和失败语义；本任务不实施同步。

`employee_no` 当前是最小过渡标识。未来优先改为稳定 `personId/employeeId` Reference，但必须在权威模型和兼容迁移获批后另行实施。当前不修改字段、不迁移数据。IAM 不保存完整人员档案。

`IamAdminService` 及其六个应用服务不迁移：其真实职责全是 IAM 安全对象。“Admin 页面”是交互入口，不是 System 数据所有权证据。

P1.6 保持一个账号最多绑定一个 Party，不通过数组字段或隐藏配置提前放开。真实多 Party 需求出现时必须新建 ADR，重新设计 Claims、Session Context、Current Party、UI 切换、数据权限、Offline Command、Refresh 恢复、审计主体和撤销语义。
## 9. System Boundary

System 未来只承载：

1. GLOBAL/APPLICATION 类型化参数：Key、类型、Scope、Version、默认值、环境覆盖规则、审计、缓存策略和 Sensitive 标记。Sensitive 只用于禁止回显/提示治理，不授权保存 Secret。
2. 非权威、跨业务、低变化通用字典。领域枚举继续由代码/领域模型拥有；MDM 主数据和配置参数不是字典。
3. 用户 Preference：Locale、显示时区、Theme、Density、Page Size、默认应用、Default/Last Factory、视图、表格列、排序、过滤器、Dashboard 和 Favorites。Preference 不参与 Authorization。
4. Application Catalog/Menu/Navigation。System Application 使用独立稳定 `applicationCode`，不等同 IAM OAuth Client；可选引用 `clientId`，允许没有 OAuth Client 的应用，并为未来一应用多 Client 保留模型空间。System 只引用 Permission Code。
5. Dynamic I18n 仅在存在真实调用方时实施；不存在调用方则 S15 Deferred，不创建表、API、缓存或后台页面。Web/Mobile 静态语言资源由客户端拥有，业务主数据多语言名称由对应业务域拥有。
6. 跨领域 Audit Projection 当前暂缓。System 不接管 IAM Security Audit 或业务域原始审计写入，不创建统一 Audit Center 或跨域 Projection 表；真实合规/跨域查询出现后另立 ADR。

IAM 继续权威拥有 OAuth Client、Client Secret、Redirect URI、Grant、Scope、Client Policy 和安全协议配置，System 不复制。System 参数禁止承载数据库密码、私钥、Token、Client Secret、Refresh Pepper 或任何 Secret。System 不拥有 Factory、Warehouse、Equipment、Party 交易关系、Role、Permission、Account/Session Status。
## 10. MDM Boundary

MDM 是 V1 中 Factory、Organization、Department、Person/Employee、Party 核心身份与主体主数据、基础联系人身份和通用联系方式、Unit Directory 与 Conversion 的唯一权威。当前尚无这些生产模型，状态必须记为 Planned Authority / Not Implemented，不能写成已实现能力。

Person/Employee：IAM 只保存稳定 `personId`、`employeeId` 或过渡期 `employeeNo` Reference，不保存完整档案；System 不拥有人员主数据。未来出现招聘、入转调离、合同、考勤、薪酬等独立 HR 生命周期时，通过新 ADR 拆分 HR Domain。

Party：MDM 只 Own Party ID/Code/Type、名称、基础启停状态、基础联系人身份与通用联系方式，以及 Supplier/Customer 统一主体身份。供应商准入、评级、采购关系、供货能力、采购结算关系、绩效和采购业务状态由未来采购/SRM 业务域 Own；客户等级、销售关系、信用、价格关系、收款条件和销售业务状态由未来销售/CRM 业务域 Own。上述业务域当前均为 Recommended Future Owner / Planned Authority / Not Implemented。IAM 只 Own Party Binding 及其安全后果。

Factory ID 由 MDM 生成。Factory Code 是否允许修改由 MDM 决定；跨域引用必须使用 ID。停用后 IAM 不再新增/签发包含无效 Factory 的授权上下文，已有 Preference 自动失效并回退。物理删除前必须完成引用影响分析。
## 11. WMS Boundary

Warehouse 权威固定属于 WMS。WMS 定义 Warehouse、Storage Location/Bin、状态、层级、Factory-Warehouse 关系及其生命周期。其他领域只引用 Warehouse/Location ID，必要时保存业务发生时的不可变快照。

System 不把 Warehouse 作为通用字典；IAM 当前没有真实 Warehouse 授权需求，因此不新增 Warehouse Scope。未来若出现必须先新增 ADR/安全设计，不能从 Factory Scope 推导或在 Preference 中模拟。

## 12. EAM Boundary

设备台账权威固定属于 EAM。EAM 定义 Equipment Asset、Machine、设备状态、资产履历及 Factory/Location 归属。System 不拥有设备主数据；当前用户 IAM 不因“设备登录”而拥有设备台账。

Device Identity 与 IoT Connection 的业务身份/归属推荐由 EAM 管理；证书、密钥和设备 Token 属于未来 Device IAM/IoT Security。安全边界不可用时设备认证 Fail Closed，EAM 只能保存凭据引用和非敏感状态摘要。

## 13. Cross-context Reference Rules

1. ID 由权威领域生成，Java/JSON 按 String 传输；跨域不创建外键。
2. 业务 Code 是否可修改由权威域定义；安全引用优先 ID，Permission Code 与 Client ID 是明确例外，由 IAM 管理并提供发布校验。
3. 引用对象停用：安全授权立即或在可证明的短窗口内失效；展示可标记“已停用”。
4. 引用对象删除：默认保留 Tombstone/停用状态；物理删除不得破坏审计与历史单据。
5. 业务发生时需要历史语义的对象（单位、名称、地址等）由业务单据保存必要不可变快照，并标记来源 ID/版本。
6. 禁止跨 Schema FK、JOIN、Mapper 或 Repository 访问；同步通过 API/Client，异步状态传播通过版本化事件。
7. IAM 与未来 System 互不读取对方 Repository；System Preference 取值后仍调用 IAM/MDM 权威校验。

## 14. Projection Rules

- 必须标记 `sourceContext`、`sourceId`、`sourceVersion` 或 `sourceUpdatedAt`，并具有本地 `projectedAt`。
- 只读、可删除、可重建，不接受独立业务写入，也不产生权威状态事件。
- 延迟或不可用时，普通展示可显示上次成功值并标记陈旧；授权、安全、库存、设备控制等决策不得使用陈旧投影放行。
- Projection 修复采用重放、全量重建或对账，不反向写权威域。

候选仅包括 Factory/Party/Person 的展示摘要、Application Catalog 客户端缓存以及未来跨领域 Audit Projection；本 ADR 不批准具体投影表。

## 15. Preference Rules

Preference 不参与授权，也不写入 JWT 授权 Claim。默认 Factory 流程必须为：

```text
System defaultFactoryId
→ IAM Factory Scope 校验
→ MDM Factory 有效性校验
→ 才能成为当前请求 Factory
```

Default/Last Factory 被删除、停用或失去 Scope 时清除并从有效范围选择显式默认；无安全默认时要求用户选择。Locale 无效回退 `zh-CN`；显示时区无效按场景回退 Factory 时区或 UTC；Theme/View 失败回退应用默认。Factory Timezone 始终属于 MDM，API 时间点始终使用 UTC/RFC 3339。

## 16. Forbidden Copies

任何非 IAM 领域不得复制：密码/摘要/Pepper、Refresh Token 明文或摘要、Session 权威状态、revoked sid 权威集合、Role/Permission 定义与分配、Factory Scope、Party Binding、OAuth Client Secret、签名私钥和授权 Claims 组装规则。

System 还不得保存其他 Secret，或用 Parameter/Dictionary 绕过领域建模。客户端不得提供权威 Role、Permission、Factory Scope、Party Binding 或安全状态；离线命令中的授权字段只是命令创建时快照，发送前必须重新校验。

## 17. Migration Assessment

### 17.1 IAM 当前生产数据

结论：**No current production migration required**。

IAM 现有字段均为安全权威或必要 Reference。`displayName` 是最小身份展示属性；`employee_no` 是过渡 Reference；`iam_user_application` 是 Mobile Access 授权，不是 Application Catalog；Factory/Party 只有 ID 引用。因此不为了 S11 强行迁移表或服务。

### 17.2 `IamAdminService`

不迁移。其 Facade/应用服务管理账号、Role、Permission、Scope、Binding、Session、Client 与 Security Audit，全部属于 IAM。未来 System 页面可以通过 IAM API 展示这些能力，但 System 不接管用例、Repository 或写事务。

### 17.3 未来候选

| 候选 | 当前实现 | 是否迁移 | 目标 Slice | 兼容/双读/事件 | 回滚 |
|---|---|---|---|---|---|
| Web Locale/Timezone/Theme | 浏览器本地缓存 | Future migration required（体验数据来源迁移） | S16 | System 成为服务端权威后，本地缓存作短期 fallback；不双写授权；通常不需事件 | 停用远端读取，恢复本地默认 |
| Web currentFactory | 本地偏好 + IAM 重校验 | Future migration required（Preference） | S16 | 可先读 System、失败读本地；每次仍校验 IAM/MDM；无需授权事件 | 清除远端偏好并保留本地选择 |
| Web 静态应用/菜单 | 源码静态元数据 | Future source transition，非 IAM 数据迁移 | S17 | 静态路由继续；目录 API 可按版本切换，必要时静态 fallback；Permission 仍 IAM | 切回静态目录 |
| 历史 IAM 偏好规划 | 仅文档 Backlog | Documentation/Frontend ownership correction only | S16 | 无数据、无需双读/事件 | 文档回退 |
| 完整人员/组织/Factory/Party 主数据 | IAM 未实现 | 无现存数据可迁；未来若误入 IAM 才另立迁移 | 对应业务 Slice | 另行扩展—迁移—收缩；可能需要事件 | 保留旧读窗口/反向补偿，需独立方案 |

本 ADR 不实施双写、同步、迁移脚本或数据回填。

## 18. Security Consequences

- 正面：IAM 不依赖 System 可用性；Permission 与 Scope 不出现第二权威；Preference 和菜单无法放大权限。
- 代价：应用目录与安全目录需要发布期引用校验；每次使用 Factory Preference 需要权威重校验。
- Fail Closed：Permission、Factory Scope、Party Binding、Session、OAuth Client、安全状态或其权威校验不可用时不得放行变更或签发新授权。
- 展示 Projection 可降级，但不得用于安全决策。IAM Security Audit 继续由 IAM 写入，System 不接管。

## 19. Consistency Consequences

同一领域内使用本地事务。跨域 Reference 创建前同步校验；状态传播可使用 Outbox/Inbox 事件实现最终一致，并辅以对账。权威状态变更与 Projection 延迟并存时，以权威 API/安全上下文为准。不得使用 Seata、共享数据库或跨 Schema Join 解决本决策中的目录/偏好一致性。

## 20. Operational Consequences

- 需要监控无效引用、投影延迟、Preference 回退、Application Catalog 中未知 Permission/Client 引用。
- 权威 API 不可用应区分安全 Fail Closed 与展示降级，日志不得包含 Token、Secret 或敏感人员字段。
- 每个投影需要重建、对账和版本可观测性；每个 Preference Key 需要白名单、默认值、版本和审计策略。
- S12 以后新增模块/Schema/表仍需独立 Slice、Migration 与验证，不能引用 Proposed ADR 作为实施授权。

## 21. Alternatives

| 方案 | 优点 | 代价/风险 | 结论 |
|---|---|---|---|
| A. IAM 承载全部平台管理数据 | 单一管理入口、短期少服务 | IAM 膨胀；偏好/菜单混入安全域；Factory/Party 生命周期耦合；IAM 可用性受业务配置影响 | 拒绝 |
| B. System 接管 IAM Admin | 页面看似统一 | 页面归属不等于所有权；Credential/Role/Permission/Session 无法安全迁移；形成第二授权权威 | 拒绝 |
| C. 按权威领域拆分，System 只承载配置与体验 | 边界清晰、独立演进、安全核心稳定 | 需要 API/事件、引用校验和投影治理 | 推荐 |
| D. 共享平台数据库和跨 Schema Join | 初期查询方便 | 所有权不清、绕过 API/审计、事务失控、无法独立演进、安全耦合 | 拒绝 |

## 22. Rejected Alternatives

- 用 System Dictionary 表示 Factory、Warehouse、Equipment、Supplier、Customer、Unit、Permission、Role 或安全状态：它隐藏生命周期和约束，已拒绝。
- 把前端 currentFactory 当作 Scope：客户端状态可篡改且可能陈旧，已拒绝。
- 在 IAM 复制 Factory/Party 完整主数据以减少调用：同步失败会形成安全歧义，已拒绝。
- 将设备账号建成当前 `iam_user` 并据此拥有设备台账：混淆人类账号与设备身份，已拒绝。
- 为过渡同时让 IAM/System 或 MDM/System 双写：没有单一失败恢复点，已拒绝。

## 23. Accepted Decisions

Chris 于 2026-07-29 接受方案 C，并批准以下七项决策：

1. **Person / Employee**：V1 由 MDM 唯一 Own Person、Employee、Organization、Department；IAM 仅保存稳定 ID 或过渡期 `employeeNo` Reference，System 不 Own 人员主数据。未来独立 HR 生命周期通过新 ADR 拆分；当前不修改 `employee_no`、不迁移数据。
2. **Party / Supplier / Customer**：MDM Own Party 核心身份与统一主体主数据；采购/SRM 是供应商采购关系的 Recommended Future Owner / Planned Authority，销售/CRM 是客户销售关系的 Recommended Future Owner / Planned Authority，当前均 Not Implemented；IAM 只 Own Party Binding 与相关 Session/Claim/撤销行为。
3. **Application Catalog**：`System Application ≠ IAM OAuth Client`。System 使用独立稳定 `applicationCode`，可选引用 `clientId`，可支持无 OAuth Client 的应用及未来一应用多 Client；IAM 继续 Own 全部 OAuth 安全配置。
4. **Display Name**：`IAM displayName` 是独立账号显示标签，`MDM personName` 是人员主数据姓名，两者不是同一个权威字段。允许明确用例中的一次性初始化，不形成持续双向同步；本任务不实施同步。
5. **多 Party Binding**：P1.6 保持一账号最多一个 Party；未来真实需求必须新增 ADR 并重新设计完整授权、会话、离线、审计和撤销语义。
6. **Dynamic I18n**：S15 保持条件性；有真实调用方才执行，无调用方则 Deferred，且不创建表/API/缓存/后台页面。
7. **跨领域 Audit Projection**：暂缓建设；IAM Security Audit 与各业务域 Business Audit 保持各自权威，System 不接管原始写入。真实合规/跨域查询出现后再以新 ADR 选择 System Projection、独立 Compliance/Audit 服务、数据仓库或可观测平台。

以上决策冻结 S11 数据所有权边界。
## 24. Acceptance Gate

Acceptance Gate 已于 2026-07-29 由 Chris 通过：

- 方案 C 与七项决策已接受；
- Permission 继续由 IAM Own，Warehouse 由 WMS Own，Equipment Ledger 由 EAM Own；
- Preference 不参与 Authorization，System 参数禁止 Secret；
- IAM 结论为 **No current production migration required**，`IamAdminService` 不迁移；
- S11 状态可收口为 Completed，S12 仍为 Not Started。

Accepted 只表示决策边界冻结，不表示自动创建模块、迁移数据或启动后续 Slice。
## 25. S12 Input

S12 只能由独立任务启动，并且只允许创建 `mom-system-platform` 技术骨架、确定 api/client/server 或最终批准结构、建立依赖方向与 ArchUnit 门禁、建立空模块/基础配置/测试骨架。

S12 不得提前实现参数、字典、Preference、Application Catalog、Menu、Navigation、Dynamic I18n、Audit Projection、业务 API、数据同步或 IAM 数据迁移；不得提前实现 S13～S17。

冻结依赖门禁：

```text
mom-system-platform
不得依赖 mom-iam-server
不得访问 IAM Repository
不得访问 IAM Schema
不得保存 Permission
不得保存 Factory Scope
不得保存 Party Binding
不得保存 Session / Refresh / revoked sid
不得保存 Secret
不得跨 Schema FK / JOIN
```
## 26. Rollback / Supersession Strategy

本 ADR 已 Accepted，但本次仍是纯文档决策收口，没有生产回滚、数据库迁移或 Token 失效。S11-A 两个已推送文档 Commit 与 S11-B 新文档收口 Commit 均保留，不改写历史。

若未来边界变化，创建新的 ADR 并将 ADR-025 标记为 Superseded，不静默改写本决策；对应实现和数据迁移必须使用独立扩展—迁移—收缩方案。

## 27. S15-B 后续批准记录

Chris 于 2026-07-30 明确批准建设 Dynamic I18n 后端能力。该批准不改写 S15-A 在审计时点“没有已经接入生产的远程动态国际化调用链”的历史事实，而是以明确产品决策满足 ADR-025 的独立实施授权。

S15-B 的目标消费者为 `mom-web`、`mom-mobile` 和未来 System 管理端；当前客户端尚未接入。本 Slice 仅建设 System 自有的后端存储、草稿、显式发布、不可变发布快照、认证运行时读取、ETag/304 与创建新单调版本的回滚能力。它不修改客户端、不进入 S16 Preference 或 S17 Catalog/Menu，不保存 IAM Permission/Role，也不引入缓存、消息、Seata 或跨 Schema 访问。

实施期间状态为：S15-A `Completed`、S15-B `In Progress`、S15 `In Progress`、S16 `Not Started`。只有 S15-B 全部实现与门禁通过后，才可将 S15-B 和 S15 标记为 `Completed`。

后续完成记录：S15-B 全部门禁通过后已标记为 `Completed`，S15 已完成；S16 仍为 `Not Started`。
