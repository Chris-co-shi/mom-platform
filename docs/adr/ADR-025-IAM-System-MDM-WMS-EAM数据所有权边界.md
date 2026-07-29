# ADR-025：IAM、System、MDM、WMS、EAM 数据所有权边界

- 状态：Proposed
- 提出日期：2026-07-29
- 决策 Slice：P1.6 S11-A
- 决策人：待 Chris Review
- 关联：[ADR-023](ADR-023-Locale时区与用户偏好边界.md)、[ADR-024](ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md)、[S11 现状与迁移边界报告](../engineering/P1.6-S11-数据所有权现状与迁移边界报告.md)

> **Proposed 不代表已接受。** 本 ADR 只形成推荐决策，不创建 `mom-system-platform`，不修改 IAM、API、Schema、Flyway 或运行时行为。S12 不能在 ADR-025 经 Chris 明确批准并标记为 `Accepted` 前开始；System 模块形态仍以最终 Accepted ADR 为准，本 ADR 不自动触发任何 IAM 数据或代码迁移。

## 1. Context

P1.6 S01～S10 已建立工程规范并封板 IAM 安全基线。IAM 当前真实存储账号、凭据、RBAC、Factory Scope、Party Binding、Mobile Access、OAuth Client Policy、Session、Refresh Token 和安全审计；未来 System 候选能力包括参数、通用字典、用户偏好、应用目录、菜单与导航。与此同时，现有架构规划把 Factory、组织、供应商、客户和单位放在 MDM，把 Warehouse 放在 WMS，把设备台账放在 EAM。

如果只按“管理页面”或类型名称拆分，会把安全权威、体验配置和业务主数据重新混合。本 ADR 以 Migration、Entity、Repository、Application Service、API 与固定前端调用方为证据，裁决单一写入权威、允许引用、可重建投影、用户偏好和禁止复制的边界。

## 2. Problem

需要回答：每个对象由谁定义生命周期、状态、唯一性和写事务；其他领域可以保存什么；权威不可用或引用失效时如何处理；现有 IAM 是否包含真正应迁往 System 的生产数据；以及后续 S12～S18 可以建设什么而不形成第二授权权威或共享数据库。

关键概念必须保持分离：

```text
IAM Account ≠ Employee/Person Master Data
MDM Factory Master ≠ IAM Factory Authorization Scope ≠ System Factory Preference
Party/Supplier/Customer Master ≠ IAM External Identity Binding
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

采用“按权威领域拆分，System 只承载平台配置与体验数据”：

- IAM Own 账号、凭据、账号安全状态、User Type、Role、Permission、用户授权关系、Factory Scope、Party Binding、Mobile Access、OAuth Client/Client Policy、Session、Refresh、revoked sid、JWT Claims 规则和 IAM Security Audit。
- System 候选 Own GLOBAL/APPLICATION 参数、受限通用字典、用户偏好/视图设置、Application Catalog、Menu、Navigation，以及有真实调用方时的动态国际化资源和可选跨领域审计 Projection。
- MDM 推荐 Own Factory、Organization、Department、Person/Employee（V1 推荐，是否以后拆 HR/Organization Domain 待决策）、Party/Supplier/Customer（V1 推荐统一 Party 模型，长期形态待决策）、Unit Directory 与 Conversion。
- WMS Own Warehouse、Storage Location/Bin、Warehouse Status 与 Factory-Warehouse 业务关系。
- EAM Own Equipment Asset、Machine、设备台账状态、设备与 Factory/Location 的资产归属；Device Credential 预留给未来 Device IAM/IoT Security，不进入当前用户 IAM 或 System。
- MES/制造资源边界推荐 Own Production Line/Work Center 的生产能力与排程语义；EAM 只引用并管理挂接设备，最终对象形态需在相应业务 Slice 决定。

## 7. Object Ownership Matrix

| 对象 | 当前事实 | 权威领域 | 其他领域分类 | 可保存内容 | 禁止复制 | 一致性方式 | 迁移结论 |
|---|---|---|---|---|---|---|---|
| User Account | IAM 已实现 | IAM Own | System/MDM Reference | `userId` | 凭据、锁定、授权关系 | IAM 同步校验/事件失效 | 不迁移 |
| Username | IAM 已实现且唯一 | IAM Own | 展示 Projection 可选 | 脱敏展示值 | 登录校验副本 | IAM 查询 | 不迁移 |
| Display Name | IAM 已实现最小身份展示 | IAM Own（最小属性） | 人员域可为来源候选；客户端 Projection | `userId + displayName + updatedAt` | 完整 Person 档案 | 后续受控同步；IAM 不依赖其可用性 | 不迁移；边界待 Chris 确认 |
| Credential | IAM 已实现摘要 | IAM Own | Forbidden Copy | 无 | 密码、摘要、Pepper、重置状态 | 仅 IAM 本地事务 | 不迁移 |
| Account/Lock/First Login | IAM 已实现 | IAM Own | Forbidden Copy/只读结果 | 必要的泛化状态结果 | 失败次数、锁定细节权威副本 | IAM 实时校验 | 不迁移 |
| User Type/Mobile Access | IAM 已实现 | IAM Own | Reference/只读授权结果 | 当前入口判断结果 | 第二套授权状态 | IAM 实时校验、Session 撤销 | 不迁移 |
| Person/Employee | 仅 `employee_no` 最小引用；完整档案不存在 | MDM Own（V1 推荐） | IAM Reference | `personId/employeeId` 或过渡期 `employeeNo` | 联系方式、组织岗位全档案 | API/事件；权威不可用不扩大权限 | 未来模型新建；无现有生产迁移 |
| Organization/Department/Position | 未实现，仅文档规划 | MDM Own（V1 推荐） | Reference/Projection | 稳定 ID/Code、展示投影 | 组织生命周期副本 | API/事件 | 未来新建 |
| Role | IAM 已实现 | IAM Own | System Reference | Role ID/Code 仅展示需要时 | Role 定义与分配权威副本 | IAM API | 不迁移 |
| Permission | IAM 已实现且 Flyway/代码管理 | IAM Own | System Reference | Permission Code | Permission 名单第二权威 | 发布时受控引用校验 | 不迁移 |
| User Role | IAM 已实现 | IAM Own | Forbidden Copy | 无；调用方只消费结果 | 分配关系 | IAM 实时校验/短期 Token 快照 | 不迁移 |
| Factory Scope | IAM 已实现 ID 集合 | IAM Own | System 禁止作为 Preference 保存 | 仅 IAM `factoryId` 引用 | Factory 属性、Scope 第二权威 | MDM ID 校验 + IAM 授权事务 | 不迁移 |
| Current Authorization Context/JWT Claims | IAM 已实现 | IAM Own | 客户端短期只读快照 | 已签名 Claims/`/me` 结果 | 客户端自报 Role/Scope/Party | 每次签发与 `/me` 重载；Fail Closed | 不迁移 |
| OAuth Client/Client Policy | IAM/SAS 已实现 | IAM Own | System Reference | Client ID | Secret、Redirect/Grant 安全策略副本 | IAM 校验 | 不迁移 |
| Session/Refresh/revoked sid | IAM 已实现 | IAM Own | Forbidden Copy | 客户端仅持协议要求的 Token；System 无副本 | Session 权威、Refresh 明文/摘要、撤销状态 | IAM 本地事务 + Redis revoked sid | 不迁移 |
| IAM Security Audit | IAM 已实现追加写 | IAM Own | System 可选 Projection | 脱敏事件投影与来源 | 原始事件权威写入 | 事件/只读 API | 不迁移 |
| Factory | 仅 IAM 引用；主数据未实现 | MDM Own | IAM/System/WMS Reference | `factoryId`；必要展示 Projection | 名称、地址、时区在 IAM/System 的权威副本 | MDM API/事件 | 未来 MDM 新建 |
| Factory Code/Name/Status/Address/Timezone | 未实现 | MDM Own | Projection/Reference | 展示字段、来源版本、更新时间 | 独立写入 | API/事件；停用事件 | 未来 MDM 新建 |
| Default/Last Factory | Web 当前本地偏好 | System Preference | 客户端缓存 | `factoryId`、版本、更新时间 | Factory Scope | 读取后 IAM Scope + MDM 状态重校验 | S16 候选；非 IAM 迁移 |
| Current Request Factory | Web/Mobile 发送 `X-Factory-Id` | 请求上下文；IAM/业务服务校验 | 临时值 | 经校验的 `factoryId` | 持久化为授权权威 | 每请求 Fail Closed | 不迁移 |
| Party | IAM 仅绑定 ID；主数据未实现 | MDM Own（V1 推荐） | IAM Reference | `partyType + partyId` | 主体名称、地址、联系人、状态副本 | 权威校验/状态事件 | 未来 MDM 新建；Decision Required |
| Party Binding | IAM 已实现且一账号最多一个 Party | IAM Own | Forbidden Copy | 业务只消费经校验结果 | 绑定关系第二权威 | Party 状态校验 + IAM 事务/撤销 | 不迁移 |
| Supplier/Customer/Contact | 仅文档规划 | MDM Own（V1 推荐统一 Party） | IAM Reference/展示 Projection | Party ID/Type；必要联系人快照由业务定义 | IAM 内完整主数据 | API/事件 | 未来新建；长期边界待决策 |
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
| Application Catalog | Web 应用入口静态、IAM 有 Client Policy | System Own | IAM Client ID Reference | Application Code、名称、Icon、Entry URL、Sort、Enabled、Client ID 引用 | Client Secret/授权策略 | 发布校验/API/缓存 | S17 新建；应用编码待决策 |
| Menu/Navigation | Web IAM 菜单静态 | System Own（动态目录）；Web Own 路由组件 | Permission Code Reference | 树、Route Key、排序、Feature Flag、Permission Code | Permission 权威列表 | System API；服务端仍授权 | S17 候选，静态回退兼容 |
| Dynamic I18n | 无真实调用方 | System Own（条件性） | 客户端缓存 | Key、Locale、Version | 领域主数据多语言字段 | API/缓存；不可用回退静态资源 | S15 保持条件性 |
| Business Audit | 各业务域尚待实现 | 对应业务领域 Own | System 可选 Projection | 脱敏聚合视图 | 原始审计跨域写入 | 事件/查询投影 | 不迁移；按业务 Slice 新建 |

## 8. IAM Boundary

IAM 保留 User Account、Username、最小 Display Name、Credential、账号状态/锁定/首次改密、User Type、Mobile Access、Role、Permission、User Role、Factory Scope、Party Binding、Client Policy、OAuth Client、Session、Refresh、revoked sid、JWT Claims、`/api/iam/me` 和 Security Audit。

`displayName` 推荐保留为协议和安全界面所需的最小身份展示属性，使 IAM 在人员域不可用时仍能认证并显示主体；它不能演变为包含生日、联系方式、组织、岗位等的 Person Profile。账号没有完整员工档案时，IAM 仍可按账号安全规则工作；需要员工业务事实的用例由相应主数据域拒绝或提示补档，不能让 IAM 伪造档案。

`employee_no` 当前是最小过渡标识。未来优先改为稳定 `personId/employeeId` Reference，但必须在权威模型和兼容迁移获批后另行实施。当前不修改或迁移。

`IamAdminService` 及其六个应用服务不迁移：其真实职责全是 IAM 安全对象。“Admin 页面”是交互入口，不是 System 数据所有权证据。

## 9. System Boundary

System 未来只承载：

1. GLOBAL/APPLICATION 类型化参数：Key、类型、Scope、Version、默认值、环境覆盖规则、审计、缓存策略和 Sensitive 标记。`Sensitive` 只用于禁止回显/提示治理，不授权保存 Secret。
2. 非权威、跨业务、低变化通用字典。领域枚举继续由代码/领域模型拥有；MDM 主数据和配置参数不是字典。
3. 用户 Preference：Locale、显示时区、Theme、Density、Page Size、默认应用、Default/Last Factory、视图、表格列、排序、过滤器、Dashboard 和 Favorites。
4. Application Catalog/Menu/Navigation；只引用 IAM Client ID 和 Permission Code。菜单隐藏从不代替 Gateway 或业务服务权限校验。
5. 有真实调用方时的 Dynamic I18n，以及经单独批准的跨领域只读 Audit Projection。

System 参数禁止承载数据库密码、私钥、Token、Client Secret、Refresh Pepper 或 IAM OAuth Client 安全配置。System 不拥有 Factory、Warehouse、Equipment、Supplier、Customer、Unit、Role、Permission、Account/Session Status。

## 10. MDM Boundary

MDM 已规划且推荐拥有 Factory（ID、Code、Name、Status、Timezone、Address）、Organization、Department、Person/Employee、Party/Supplier/Customer、联系人、Unit Directory 与 Conversion。当前尚无这些生产模型，ADR 不能把规划写成实现。

Factory ID 由 MDM 生成。Factory Code 是否允许修改由 MDM 决定；跨域引用必须使用 ID，Code 仅作稳定业务标识或展示。停用后 IAM 不再新增/签发包含无效 Factory 的授权上下文；已有 Preference 自动失效并回退。删除优先采用停用/保留引用，物理删除需先完成引用影响分析。

Person/Employee 与 Party 的长期限界上下文仍列为 Open Decision；本 ADR 推荐 V1 先由 MDM 唯一拥有，避免在权威域尚未出现前制造同步服务。

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

## 23. Open Decisions

以下事项需要 Chris 决策；每项已有推荐，不把全部细节上抛：

| 决策点 | 当前事实 | 候选方案 | 推荐 | 兼容/实现影响 | 不决策后果 |
|---|---|---|---|---|---|
| Person/Employee 长期权威 | 仅 IAM `employee_no`；MDM 文档规划 | MDM；HR Domain；独立 Organization Domain | V1 先 MDM，出现独立 HR 生命周期再用新 ADR 拆分 | IAM 未来改存稳定 ID；拆分需事件/兼容 | 无法冻结人员 API 与引用 ID |
| Supplier/Customer/Party | IAM 仅 ID Binding；MDM 文档规划 | MDM 统一 Party；供应商/客户业务域分别拥有 | V1 MDM 统一 Party 核心，业务域拥有交易关系 | 决定 Party ID、状态事件和联系人模型 | IAM Party 有效性校验无稳定上游 |
| Application Code 与 Client ID | Web 应用与 IAM Client 一一对应但语义不同 | 直接用 Client ID；System 独立 Code + 可选 Client ID 引用 | 独立 Application Code | 需发布引用校验；支持非 OAuth 应用 | Catalog 被安全协议标识绑死 |
| IAM Display Name 边界 | Token/`/me` 已使用 | IAM 独立最小值；人员域 Projection；完全迁出 | IAM 保留最小值，可受控同步但不依赖人员域可用性 | 避免协议破坏；需明确冲突处理 | 人员域建设时易双主 |
| 多 Party 绑定 | 当前唯一约束一账号一个 Party | 保持单一；允许多 Party + 当前选择 | P1.6 保持单一，真实业务证明后另立 ADR | 多绑定会改变 Claims、Session 与 UI | 贸然放开会扩大授权模型 |
| S15 Dynamic I18n | 无真实动态调用方 | 现在实现；保持条件性；永久不做 | 保持条件性 | 无调用方则不建表/API | 过早建设闲置平台能力 |
| 跨领域 Audit Projection | IAM 有安全审计，业务审计未实现 | System 聚合；独立 Compliance；暂缓 | 暂缓，真实合规查询出现后再选 | 需要脱敏事件、保留期、访问控制 | 现在建设会猜测需求和数据合同 |

## 24. Acceptance Gate

ADR-025 只有在 Chris 明确批准以下内容后才能从 Proposed 改为 Accepted：

1. IAM/System/MDM/WMS/EAM 推荐边界与 Open Decisions 的取舍；
2. Permission 继续由 IAM Own，Warehouse 由 WMS Own，设备台账由 EAM Own；
3. Preference 不参与授权，System 参数不保存 Secret；
4. 当前 IAM 无生产数据迁移、`IamAdminService` 不迁移；
5. S12 的模块候选边界与禁止依赖。

未通过 Gate 时，S11 状态保持 `In Progress / Awaiting Chris Decision`，S12 保持 `Not Started`。

## 25. S12 Input

若本 ADR Accepted，S12 仅可依据最终批准范围设计 `mom-system-platform` 技术骨架和依赖门禁；不得在骨架 Slice 提前实现参数、字典、偏好、菜单或动态国际化。API/Client/Server 形态、Schema 创建和依赖方向仍需以 Chris 批准后的 ADR 文本及 S12 独立指令为准。

建议 S12 架构门禁至少禁止：System 依赖 `mom-iam-server`、跨 Schema Repository、保存 Permission/Scope/Session、以及任何 Secret 参数能力。

## 26. Rollback / Supersession Strategy

本 ADR 为纯文档 Proposed 草案，无生产回滚、数据库迁移或 Token 失效。Chris 不接受时可修改或标记 Rejected，S12 继续阻塞。若 Accepted 后边界变化，创建新的 ADR 并将 ADR-025 标记为 Superseded，不直接改写历史决策；对应实现和数据迁移必须使用独立扩展—迁移—收缩方案。
