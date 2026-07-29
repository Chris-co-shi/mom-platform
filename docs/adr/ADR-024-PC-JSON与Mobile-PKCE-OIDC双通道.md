# ADR-024：PC JSON 与 Mobile PKCE/OIDC 双通道

- 状态：Proposed
- 日期：2026-07-29
- 决策人：Chris（尚未批准）
- Slice：P1.6 S07-A
- 关联 ADR：[ADR-019：P1.5 认证与授权闭环](ADR-019-P1.5认证与授权闭环.md)
- 关联文档：[P1.6 S06 IAM 端点、调用方与职责审计](../engineering/P1.6-S06-IAM端点调用方与职责审计.md)、[IAM、安全协议、Gateway 与 Resource Server 运行规范](../engineering/standards/security-protocol-runtime-standard.md)、[P1.6 IAM 与 System 平台治理计划](../plans/P1.6-IAM与System平台治理计划.md)

> 本 ADR 是待 Chris 明确批准的架构决策草案，不代表已接受、已批准或已完成。
> 本次只形成决策证据，不修改任何生产实现、公开 API、认证运行时、Token Claim、Session、
> Refresh Token、revoked sid、Gateway、Resource Server、mom-web 或 mom-mobile。
>
> 本 ADR 在 Proposed 状态下不修改 ADR-019 的 Accepted 历史状态。只有 Chris 明确批准并完成
> 独立状态变更后，才允许评估本 ADR 对 ADR-019 中 PC Web 运行时结论的局部替代关系。

## 1. 背景

P1.5 的目标设计曾统一要求 Web 与 Mobile 使用 Authorization Code + PKCE S256 + OIDC。
P1.6 S06 通过三个仓库固定 Ref 完成真实调用方审计后，确认当前生产代码事实与原目标设计存在差异：

1. MOM Admin、Supplier Portal、Customer Portal 三个 PC 应用实际使用第一方 JSON Runtime；
2. Mobile 实际使用系统浏览器、Authorization Code、PKCE S256、OIDC、标准 Authorization Endpoint、
   标准 Token Endpoint、ID Token、JWK、Issuer、Audience、Nonce 校验和 Gateway Bearer API；
3. Mobile 不调用第一方 JSON 登录端点；
4. 第一方 JSON Refresh 与标准 OAuth2 Refresh Grant 都调用 `IamSessionTokenService.rotate`；
5. 两条通道共享同一个 Session、Opaque Refresh Token、Refresh Rotation、Reuse Detection、
   JWT Access Token、revoked sid 和 Session 撤销语义；
6. 两条通道是不同外部协议 Adapter，不是两套独立安全权威模型；
7. 当前没有证据支持立即删除 Spring Authorization Server、OAuth2/OIDC、IAM HTML Login、
   第一方 JSON Login/Refresh、标准 OAuth2 Refresh Grant、任一 Refresh Adapter 或 mom-web 标准 PKCE Package。

因此，S07 需要对长期协议边界、兼容状态、Logout 目标语义、Redirect URI 环境策略、
双通道行为一致性及后续 Slice 的可变范围形成可批准的决策草案。

## 2. 问题

本 ADR 要回答：

1. PC 第一方 JSON 与 Mobile 标准 PKCE/OIDC 是否长期并存；
2. 两条外部协议是否继续共享同一个 Session、Refresh、JWT 和撤销权威核心；
3. mom-web 标准 PKCE Package 的长期产品状态；
4. Mobile Logout 的目标安全语义；
5. Mobile Redirect URI 的分环境策略；
6. PC 迁移标准 PKCE、Mobile 改用 JSON、引入 BFF 等候选方案为何采用、拒绝或推迟；
7. 双通道之间必须保持哪些一致行为，哪些格式允许因协议不同而不同；
8. S08、S09、S10 可以实施什么、不得改变什么；
9. 哪些变更仍需要 Chris 单独批准。

## 3. 冻结的当前事实

### 3.1 PC Web 当前 Runtime

当前三个 PC 应用：

- MOM Admin；
- Supplier Portal；
- Customer Portal。

实际使用第一方 JSON Runtime：

- `POST /api/iam/auth/login`
- `POST /api/iam/auth/password/change-required`
- `POST /api/iam/auth/refresh`
- `POST /api/iam/auth/logout`
- `GET /api/iam/me`

本 ADR 不把该事实改写为标准 PKCE Runtime，也不要求 P1.6 内迁移。

### 3.2 Mobile 当前 Runtime

当前 Mobile 实际使用：

- 系统浏览器；
- Authorization Code；
- PKCE S256；
- OIDC；
- 标准 Authorization Endpoint；
- 标准 Token Endpoint；
- ID Token；
- JWK；
- Issuer、Audience、Nonce 校验；
- Gateway Bearer API。

Mobile 不调用第一方 JSON 登录端点。本 ADR 不把 Mobile 改写为第一方 JSON Runtime。

### 3.3 单一权威核心

第一方 JSON Refresh 与标准 OAuth2 Refresh Grant 均调用：

`IamSessionTokenService.rotate`

并共享：

- Session；
- Opaque Refresh Token；
- Refresh Rotation；
- Reuse Detection；
- JWT Access Token；
- revoked sid；
- Session 撤销语义。

Controller、Provider、Handler 是协议 Adapter。`IamSessionTokenService` 是协议无关的权威核心。
任何后续重构不得复制第二套 Session、Refresh、JWT 或撤销模型。

### 3.4 当前删除结论

当前没有证据支持立即删除：

- Spring Authorization Server；
- OAuth2/OIDC；
- IAM HTML Login；
- 第一方 JSON Login；
- 第一方 JSON Refresh；
- 标准 OAuth2 Refresh Grant；
- 任一 Refresh Adapter；
- mom-web 标准 PKCE Package。

“当前未接入”不等于“废弃”或“可删除”。

## 4. 候选方案

### 4.1 方案 A：双通道并存、共享权威核心

**结论：Recommended / Awaiting Chris Approval**

范围：

- PC 使用第一方 JSON；
- Mobile 使用 Authorization Code + PKCE S256 + OIDC；
- Session、Refresh、JWT、撤销共享同一权威核心；
- 不复制安全模型；
- 协议 Adapter 可独立演进，但必须通过一致性矩阵验证共同安全语义。

优点：

- 与 S06 已确认的真实调用方和当前运行时一致；
- 不要求三个 PC 应用在 P1.6 内进行高风险登录迁移；
- 保留 Mobile 已实现的系统浏览器、PKCE、OIDC、App Link 和安全存储边界；
- 保持统一 Session、Refresh Rotation、Reuse Detection、JWT 和撤销语义；
- 对后续 S08/S09 的行为保持重构提供稳定边界；
- 迁移和回滚风险最低。

缺点：

- 需要长期维护两个外部协议 Adapter；
- 必须维护跨协议行为一致性测试；
- PC 与 Mobile 的请求、响应和登录 UX 不统一；
- 文档必须清楚区分“协议差异”与“权威核心一致”。

### 4.2 方案 B：PC 逐步迁移到标准 PKCE/OIDC

**结论：Future Evaluation；不作为 P1.6 强制迁移目标**

潜在收益：

- PC 与 Mobile 的外部协议更标准化；
- 可统一 Authorization Endpoint、标准 Token Endpoint、OIDC Discovery、JWK 和浏览器登录状态；
- 长期可能减少第一方 JSON 登录入口的兼容维护成本；
- mom-web 标准 PKCE Package 可成为迁移基础。

主要成本与风险：

1. 登录 UX 从站内 JSON 表单切换为 IAM 页面跳转、回调和返回路径恢复；
2. 必须重新验证 Access Token、Refresh Token 的浏览器存储边界；
3. 三个 PC 应用需要独立 Client、精确 Redirect URI 和多环境回调治理；
4. 首次改密需要在保存的 Authorization Request 中完整续接；
5. Logout 必须同时处理 MOM Session 撤销、应用本地状态和可选 OIDC 浏览器会话；
6. MOM Admin、Supplier Portal、Customer Portal 均需要迁移和兼容窗口；
7. 旧版本客户端与新版本客户端需并存；
8. 回滚必须能够恢复第一方 JSON Runtime，不能以删除入口作为迁移起点；
9. 页面刷新、跨标签页、错误恢复、Single Flight Refresh 和高风险请求重放策略需重新验证；
10. 当前没有足够产品收益证据支持在 P1.6 承担该迁移。

未来重新评估方案 B 时，必须新建或更新 ADR，并提供跨仓库调用方、UX、安全、兼容、
E2E 和回滚证据，且需要 Chris 单独批准。

### 4.3 方案 C：Mobile 改用第一方 JSON

**结论：Rejected**

拒绝原因：

- 放弃当前系统浏览器授权流程；
- 弱化已实现的 PKCE S256、OIDC、Nonce、Issuer、Audience、JWK 和 App Link 边界；
- 需要在 Mobile 重新设计账号密码采集和凭证输入安全；
- 与当前 Mobile Runtime 和 S06 审计事实不一致；
- 会把 Native Public Client 拉回非标准第一方密码协议；
- 不能减少 Session、Refresh、JWT 和撤销核心复杂度，只会替换外部 Adapter；
- 迁移收益不足以覆盖安全和产品风险。

### 4.4 方案 D：引入 BFF

**结论：Deferred；不进入 P1.6**

潜在收益：

- 浏览器不直接持有 Access Token 和 Refresh Token；
- Token 可由服务端统一管理；
- 可通过 HttpOnly Cookie 降低浏览器脚本直接读取 Token 的风险；
- 可集中处理后端调用、Token Refresh 和部分前端会话恢复。

新增成本与风险：

- 引入应用 Cookie Session；
- 必须设计 CSRF 防护、SameSite、Domain、Path、Secure 和会话固定攻击防护；
- 需要 BFF 高可用、Session Store、故障恢复和扩缩容策略；
- MOM Admin、Supplier Portal、Customer Portal 的 BFF 边界需独立裁决；
- 新增部署单元、网络跳数、运维复杂度和故障域；
- Gateway、BFF、IAM、业务服务之间的责任需重新划分；
- Logout、管理员撤销、Session 过期和跨应用会话需要重做 E2E；
- 超出 P1.6 IAM 行为保持收敛范围。

未来只有在浏览器 Token 风险、产品 UX 或组织部署约束形成明确驱动时，才允许通过新 ADR 重新评估。

## 5. 推荐方案（待 Chris 批准）

推荐采用方案 A：

**PC 第一方 JSON + Mobile Authorization Code/PKCE/OIDC 双通道长期并存，共享单一 Session、Refresh、JWT 和撤销权威核心。**

推荐边界：

1. PC Web 当前继续使用第一方 JSON；
2. Mobile 当前继续使用标准 Authorization Code + PKCE S256 + OIDC；
3. 不要求 PC 在 P1.6 内迁移到标准 PKCE；
4. 不将 Mobile 改为第一方 JSON；
5. 不在 P1.6 内引入 BFF；
6. 不复制 Session、Refresh、JWT 或撤销核心；
7. Controller、Provider、Handler 只能作为协议 Adapter；
8. `IamSessionTokenService` 继续保持协议无关的权威核心；
9. OAuth2/OIDC 标准响应不得被包装成第一方 JSON 统一响应；
10. 第一方 JSON 契约不得冒充 OAuth2/OIDC 标准协议；
11. 本推荐方案在 Chris 明确批准前不生效为 Accepted 决策。

## 6. Web 标准 PKCE Package 产品状态

建议产品状态：

`Compatibility / Future Migration Capability`

必须明确：

1. 当前 MOM Admin、Supplier Portal、Customer Portal 没有接入该 Package；
2. 本次不删除；
3. 本次不要求三个 PC 应用接入；
4. 它不应被描述为当前生产 Runtime；
5. 它可作为未来 PC 标准 PKCE 迁移能力和兼容验证资产；
6. 当前未接入不等于废弃；
7. 当前代码重复或 Package 体积不构成删除依据。

后续删除必须同时具备：

- 无调用方证据；
- 产品方向裁决；
- 明确兼容窗口；
- 回滚方案；
- PC Admin、Supplier、Customer 与 Mobile 跨仓库测试；
- Login、首次改密、Refresh、Reuse Detection、Logout、管理员撤销完整回归；
- Redirect URI 与 Client Registration 核验；
- Chris 明确批准。

## 7. Mobile Logout 目标安全语义

本 ADR 只冻结目标语义，不创建具体 API，不决定具体协议组合。

Mobile Logout 必须达到：

1. 用户执行 Mobile Logout 时，服务端权威 Session 必须被撤销；
2. 对应 Refresh Token 必须失效；
3. 已签发 Access Token 的 `sid` 必须进入现有 revoked sid 机制；
4. 本地 Token、安全存储、授权事务和运行时状态必须清理；
5. 浏览器 OIDC 会话清理属于附加协议步骤，不能替代服务端 MOM Session 撤销；
6. 网络结果不确定时必须 Fail Closed，不得继续使用旧 Refresh Token；
7. 具体使用 OIDC RP-Initiated Logout、标准 Token Revocation、现有 Session 撤销能力或新的内部 Adapter，
   由后续实现设计决定；
8. 未经独立 API 契约 Review，不得在 S07 创建新公开端点；
9. 客户端本地清理成功但服务端撤销结果未知时，仍必须丢弃本地 Refresh Token，并要求重新认证；
10. Logout 审计必须能够关联用户、Client、Session `sid`、结果和失败原因，但不得记录 Token 明文；
11. 管理员撤销 Session 与用户主动 Logout 必须最终收敛到同一权威 Session 撤销语义；
12. OIDC 浏览器会话仍存在时，重新登录可能快速完成，但不能恢复已撤销的 MOM Session 或旧 Refresh Token。

EG-01 在本 ADR 中形成目标安全语义，但具体 API、协议编排、客户端实现和 E2E 仍属于后续 Slice。

## 8. Mobile Redirect URI 分环境策略

### 8.1 冻结原则

1. 所有 Redirect URI 必须精确登记；
2. 禁止通配符 Redirect URI；
3. 不允许正式客户端依赖未验证的动态 Redirect；
4. 本地 H5 或开发服务器只能使用明确的开发环境 URI；
5. Android 本地开发可保留精确登记的开发 Scheme 或开发 App Link；
6. 正式 Android 优先使用经过平台验证的 HTTPS App Link；
7. H5、Android 开发、测试、预生产、生产必须使用独立 Client 或独立精确 Redirect URI 集合；
8. IAM 默认值和 mom-mobile 默认值不一致的问题必须在 S10 E2E 前关闭；
9. Secret、Client ID、Redirect URI 和环境配置不得写入 Token Claim 或用户偏好；
10. Redirect URI 变化属于协议兼容事件，必须同步 Client Registration、客户端环境配置、测试和回滚记录；
11. 正式环境不得复用本地 loopback、开发 Scheme 或测试 App Link；
12. 每个环境必须明确 Owner、登记位置、验证方式和变更审批记录。

### 8.2 分环境矩阵模板

以下值均为待环境配置确认的占位符，不代表已部署事实：

| 运行形态 | 建议 Client 边界 | 精确 Redirect URI 占位符 | 允许范围 | 验证要求 | 状态 |
|---|---|---|---|---|---|
| H5 本地开发 | 独立开发 Client | `<mobile-dev-loopback-uri>` | 仅本机开发服务器 | 精确端口、路径和 Origin；不得进入生产注册 | 待环境配置确认 |
| Android 本地开发 | 独立开发 Client | `<android-dev-callback>` | 精确开发 Scheme 或开发 App Link | 包名、Scheme/Host/Path 与签名环境匹配 | 待环境配置确认 |
| Android 测试 | 独立测试 Client | `<android-test-app-link>` | 测试环境 | 平台关联文件、测试签名和回调 E2E | 待环境配置确认 |
| Android 预生产 | 独立预生产 Client | `<android-staging-app-link>` | 预生产环境 | HTTPS、平台验证、与生产隔离 | 待环境配置确认 |
| Android 生产 | 独立生产 Client | `<android-production-app-link>` | 正式发布 | 经过平台验证的 HTTPS App Link、精确登记、发布验收 | 待环境配置确认 |

### 8.3 EG-03 收口要求

S10 E2E 前必须：

- 确认每个环境的 Client ID；
- 确认每个 Client 的精确 Redirect URI 集合；
- 关闭 IAM 默认值与 mom-mobile 默认值不一致；
- 验证 Authorization Request、Callback、`state`、`nonce`、Code Exchange 和 App Link；
- 验证错误 URI、错误环境、通配符和未登记 URI 均被拒绝；
- 记录兼容窗口和回滚配置。

## 9. 双通道行为一致性矩阵

分类说明：

- **完全一致**：两条通道必须共享相同安全语义和权威数据；
- **协议差异允许**：请求、响应、页面或错误格式可以不同，但不得改变核心安全结果；
- **PC 专属**：只属于第一方 JSON PC Runtime；
- **Mobile/OIDC 专属**：只属于标准 Authorization Code + PKCE/OIDC Runtime。

| 行为 | 一致性要求 | PC 第一方 JSON | Mobile PKCE/OIDC | 分类 |
|---|---|---|---|---|
| 账号状态校验 | 禁用、锁定、过期等结果必须等价 | JSON 登录前校验 | IAM 页面/授权流程校验 | 完全一致 |
| 用户类型 | Client 与 `user_type` 入口隔离必须等价 | Admin/Supplier/Customer 校验 | Mobile 仅允许授权的 INTERNAL | 完全一致 |
| Client Policy | Client 启用、用户类型、Mobile Access 等策略同源 | JSON Adapter 执行 | Authorization/Token Adapter 执行 | 完全一致 |
| 首次改密 | 未改密前不得签发可用业务 Session | JSON `password/change-required` | IAM 页面保存请求后续接 | 完全一致；协议差异允许 |
| Session 绝对有效期 | 由同一权威 Session 规则决定，Rotation 不延长 | PC Session 策略 | Mobile Session 策略 | 完全一致的模型；TTL 可按 Client Policy 不同 |
| Access Token TTL | 同一 Issuer/Client Policy 决定 | JSON 响应返回 | 标准 Token Response 返回 | 完全一致 |
| Refresh Token Rotation | 每次成功 Refresh 必须轮换 | JSON Refresh Adapter | OAuth2 Refresh Grant Adapter | 完全一致 |
| 单 ACTIVE Refresh | 一个 Session 最多一个 ACTIVE | 同一数据库约束 | 同一数据库约束 | 完全一致 |
| Reuse Detection | 旧 Token 重放使 Session 进入受损/撤销语义 | JSON Adapter 返回兼容错误 | OAuth2 标准错误 | 完全一致；协议差异允许 |
| Session 撤销 | 同一 `sid` 和 Session 权威记录 | JSON Logout/管理员撤销 | Mobile 后续 Logout 编排/管理员撤销 | 完全一致 |
| revoked sid | 撤销后已签发 Access Token 必须被阻断 | Gateway/Resource Server | Gateway/Resource Server | 完全一致 |
| JWT Claim | Claim 来源、含义和签名必须一致 | 同一 JWT Issuer | 同一 JWT Issuer | 完全一致 |
| `/api/iam/me` | 当前授权上下文权威来源一致 | JSON 登录后调用 | Bearer Token 调用 | 完全一致 |
| Factory | 当前 Factory 必须重新校验，不以客户端输入为授权 | PC UI 上下文 | Mobile 上下文 | 完全一致 |
| Party | Supplier/Customer 主体边界不可由客户端切换 | Portal 强校验 | Mobile 通常不适用，若出现仍按同一规则 | 完全一致 |
| Role | 当前有效 Role 从 IAM 权威模型读取 | `/me` 与 JWT | `/me` 与 JWT | 完全一致 |
| Permission | 最终权限由业务服务强制执行 | UI 仅体验控制 | Mobile UI 仅体验控制 | 完全一致 |
| Logout | 必须撤销权威 Session、失效 Refresh、写 revoked sid、清本地状态 | 当前 JSON Logout 已具备服务端撤销 | 目标语义已冻结，具体实现待后续 | 完全一致；协议编排不同 |
| 安全审计事件 | 必须记录可关联事件且不泄露凭证 | JSON 事件类型可独立 | OAuth/OIDC 事件类型可独立 | 完全一致的审计字段；事件名可不同 |
| 错误信息泄露 | 不得泄露账号存在性、Token、内部栈和敏感状态 | 第一方兼容 JSON 错误 | OAuth2/OIDC 标准错误 | 完全一致的泄露边界；格式不同 |
| Fail Closed | 依赖故障或状态不确定不得继续使用旧凭证 | Refresh/Logout 不确定时关闭 | Refresh/Logout 不确定时关闭 | 完全一致 |
| 时钟偏差 | JWT/OIDC 时间校验使用统一允许偏差策略 | JWT 校验 | JWT、ID Token 校验 | 完全一致 |
| 并发 Refresh | 同一 Session 并发只允许一个成功结果 | Single Flight + 服务端行锁 | Lease/Replacement + 服务端行锁 | 完全一致；客户端机制不同 |
| 管理员撤销 Session | 两条通道下一次受保护请求均被阻断 | Gateway/Resource Server | Gateway/Resource Server | 完全一致 |
| 请求格式 | 可使用 MOM 第一方 JSON | JSON DTO | OAuth2/OIDC 参数 | 协议差异允许 |
| 响应格式 | 不强制统一 | MOM 第一方 JSON | 标准 OAuth2/OIDC JSON/Redirect | 协议差异允许 |
| 登录页面与跳转 | PC 可保持站内体验 | 站内 JSON 表单 | 系统浏览器、IAM 页面、回调 | 协议差异允许 |
| OIDC ID Token | 不适用于当前 PC JSON Runtime | 不要求 | 必须校验 Issuer、JWK、Audience、Nonce | Mobile/OIDC 专属 |
| `state` 与 PKCE verifier | 不适用于当前 PC JSON Runtime | 不要求 | 必须生成、持久化事务并校验 | Mobile/OIDC 专属 |
| 第一方首次改密端点 | 当前三个 PC 应用使用 | 必须保持兼容 | 不调用 | PC 专属 |
| OIDC 浏览器会话清理 | 不属于 PC JSON 必需步骤 | 不要求 | 可作为 Logout 附加步骤 | Mobile/OIDC 专属 |

不得为了“响应统一”而包装或破坏 OAuth2/OIDC 标准响应格式。

## 10. Gateway 与业务 Resource Server revoked sid

1. Gateway revoked sid Fail Closed 与客户端认证通道是两个独立问题；
2. 本 ADR 不取消业务 Resource Server 独立 JWT 验证；
3. 业务服务必须继续验证 JWT 签名、Issuer、Audience、时间和最终领域授权；
4. 如果业务服务存在绕过 Gateway 的访问路径，必须具备等价撤销能力；
5. 如果部署边界声称业务服务不可直连，必须用网络、部署和回归证据证明，而不能只靠约定；
6. EG-02 在 S07 中继续保留；
7. 具体实现与回归由 S08/S10 收口；
8. S07 不实现新的 Filter、Redis 查询、网络调用或生产行为；
9. 任何等价机制都不得把 Gateway Header 当作唯一身份或撤销证明；
10. Redis 或撤销依赖故障时，受保护访问必须保持 Fail Closed。

## 11. S08、S09、S10 实施边界

| Slice | 可以实施 | 不得改变 |
|---|---|---|
| S08 | 无行为拆分 `IamAuthorizationServerConfiguration`；抽取第一方认证应用服务；补充行为保持测试；结合部署边界设计 EG-02 收口方案 | 不改 PC JSON、Mobile PKCE/OIDC 外部协议；不删入口/Adapter；不改公开 API、Token Claim、Session、Refresh、Logout、Gateway、Resource Server 行为 |
| S09 | 整理 IAM Admin 职责、异常模型和 Token Adapter；保持 OAuth2/OIDC 标准错误；证据化重复构造 | 不把 OAuth2/OIDC 包装成第一方 JSON；不删除 Refresh Adapter；不改变 Client Policy、Permission、Session 或撤销语义 |
| S10 | 执行 PC Admin/Supplier/Customer/Mobile 全协议 E2E；关闭 Redirect URI 默认值不一致；验证 Login、首次改密、Refresh、Reuse Detection、Logout、管理员撤销、Gateway 与业务 Resource Server | 不在没有新 ADR 和 Chris 批准的情况下迁移协议、引入 BFF、删除入口、改变双通道权威核心 |

补充约束：

- Mobile Logout 具体实现需要独立协议/API 契约 Review；
- 如果实现需要新增公开端点，必须先获得独立批准，S07 不预先授权；
- S08/S09 的重构不得改变本 ADR 冻结的外部协议与安全语义；
- S10 只验证和收口，不得通过测试变更暗中改写协议决策。

## 12. 兼容、迁移与删除门禁

任一认证入口、Adapter、Package 或协议能力删除前，必须同时满足：

1. 重新生成 mom-platform、mom-web、mom-mobile 跨仓库调用方矩阵；
2. 完成 PC Admin、Supplier、Customer 和 Mobile E2E；
3. 覆盖 Login、首次改密、Refresh、Reuse Detection、Logout 和管理员撤销；
4. 覆盖 Gateway 和业务 Resource Server；
5. 定义旧客户端兼容窗口；
6. 定义回滚策略；
7. 确认 Redirect URI 和 Client Registration；
8. 证明 Session、Refresh、JWT、revoked sid 和权限语义没有分叉；
9. 记录协议错误和第一方错误兼容策略；
10. 由 Chris 明确批准；
11. 不得仅凭代码重复、类过大或当前调用较少删除协议能力；
12. S08/S09 的重构不得改变本 ADR 冻结的外部协议与安全语义；
13. 迁移必须从“并行兼容 + E2E”开始，不得从删除旧入口开始；
14. 回滚必须不依赖 force-push、数据库回退或恢复已删除协议代码。

## 13. 仍需 Chris 单独批准的变更

以下事项不因本 Proposed ADR 自动获得授权：

1. 将本 ADR 状态改为 Accepted；
2. 将 PC 从第一方 JSON 迁移到标准 PKCE/OIDC；
3. 将 Mobile 改为第一方 JSON；
4. 引入 BFF、Cookie Session 或新的 Session Store；
5. 删除或弃用任一认证入口、Controller、Provider、Handler、Refresh Adapter；
6. 删除 mom-web 标准 PKCE Package；
7. 新增、删除或修改公开认证 API；
8. 采用具体 Mobile Logout 协议组合；
9. 新增 Mobile Logout 公开端点；
10. 修改 Token Claim、TTL、Session、Refresh Token、Reuse Detection 或 revoked sid 模型；
11. 修改 Client ID、正式 Redirect URI 或生产 Client Registration；
12. 改变 Gateway/Resource Server 撤销边界；
13. 修改 Permission、Schema、Flyway 或 Redis 行为；
14. 将 PR #33 转为 Ready 或合并；
15. 进入任何会改变外部认证行为的实施。

## 14. 正向后果

- 当前真实运行时与架构文档得到一致描述；
- PC 和 Mobile 可按产品形态使用不同协议，同时共享统一安全核心；
- 避免为了形式统一而进行高风险迁移；
- Mobile 已实现的标准协议和安全边界得到保留；
- Web PKCE Package 获得明确的兼容/未来迁移定位；
- Mobile Logout、Redirect URI 和 Resource Server 撤销缺口获得可验证的后续门禁；
- S08/S09 可以在稳定边界内做行为保持重构；
- 任何删除都必须经过调用方、E2E、兼容、回滚和明确批准。

## 15. 负向后果与技术债

- 两个外部协议 Adapter 需要长期维护；
- 双通道行为一致性矩阵需要持续自动化；
- PC 与 Mobile 的登录 UX 和错误格式不同；
- ADR-019 的原始“全部用户客户端 PKCE”目标与当前运行事实存在历史差异，需要在本 ADR获批时明确局部替代关系；
- Mobile Logout 尚未完成生产实现；
- Redirect URI 正式环境值仍待环境配置确认；
- 业务 Resource Server revoked sid 等价能力仍未闭环；
- Web PKCE Package 仍需维护但当前无生产应用接线。

## 16. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 两条 Adapter 安全语义漂移 | 维护本 ADR 一致性矩阵和跨协议契约测试 |
| PC JSON 被误写成非标准 OAuth2 | 明确第一方协议边界，不使用 OAuth2 名义包装 |
| OAuth2/OIDC 响应被统一包装破坏标准 | Token/Authorization/OIDC 端点保持 SAS 标准格式 |
| Mobile Logout 只清本地状态 | 后续实现必须撤销权威 Session、Refresh 和 sid |
| Redirect URI 环境串用 | 独立 Client 或独立精确集合，S10 前完成矩阵 |
| PC 未来迁移导致中断 | 并行兼容、旧客户端窗口、跨仓库 E2E、可回滚 |
| Web PKCE Package 被误删 | 删除门禁 + Chris 明确批准 |
| 业务服务绕过 Gateway 接受已撤销 Token | 等价 revoked sid 能力或可复核的不可直连部署证据 |
| Redis/网络结果不确定时继续使用凭证 | Fail Closed，丢弃旧 Refresh Token |
| 重构借机改变协议行为 | S08/S09 只允许行为保持，外部契约 diff 和 E2E 门禁 |

## 17. 验证方式

### 17.1 S07 文档验证

- Git diff 只包含预期 Markdown；
- ADR 状态必须为 Proposed；
- 阶段状态必须为 `S07: In Progress / Awaiting Chris Approval；S08: Not Started`；
- 不得出现 S07 已完成或 S08 已开始的权威状态；
- EG-01 形成 Mobile Logout 目标语义；
- EG-02 保留至 S08/S10；
- EG-03 形成 Redirect URI 原则和矩阵模板；
- EG-04 形成 Web PKCE Package 产品状态；
- 不修改生产代码、配置、脚本、Schema、Flyway 或客户端。

### 17.2 后续实现验证门禁

- PC Admin、Supplier、Customer Login/首次改密/Refresh/Logout；
- Mobile Authorization Code + PKCE S256 + OIDC；
- ID Token Issuer/JWK/Audience/Nonce；
- Refresh Rotation、并发 Refresh、单 ACTIVE、Reuse Detection；
- 用户 Logout、管理员撤销和 revoked sid；
- Gateway 与业务 Resource Server；
- `/api/iam/me`、Role、Permission、Factory、Party；
- 安全审计、错误信息泄露、Fail Closed、时钟偏差；
- H5、Android 开发、测试、预生产、生产 Redirect URI；
- 旧客户端兼容与回滚演练。

## 18. 替代与回滚条件

只有出现以下情况之一，才允许提出新的 ADR 替代本决策：

- PC 标准 PKCE 迁移的产品与安全收益得到完整证据；
- BFF 成为明确的组织级浏览器安全基线；
- Mobile 平台约束使现有 OIDC/PKCE 无法可靠运行；
- 外部 IAM 或协议栈发生不可兼容变化；
- 当前双 Adapter 一致性无法通过自动化和治理维持；
- 法规、审计或客户安全要求强制改变 Token/Session 边界。

替代 ADR 必须包含调用方矩阵、兼容窗口、跨仓库 E2E、回滚策略和 Chris 明确批准。
在替代方案完成前，继续保留当前双通道与单一权威核心。

## 19. 参考资料

仓库权威资料：

- [P1.6 S06 IAM 端点、调用方与职责审计](../engineering/P1.6-S06-IAM端点调用方与职责审计.md)
- [P1.6 工程规范覆盖与缺口清单](../engineering/P1.6-工程规范覆盖与缺口清单.md)
- [P1.6 IAM 与 System 平台治理计划](../plans/P1.6-IAM与System平台治理计划.md)
- [P1.6 实施进度](../plans/P1.6-实施进度.md)
- [P1.5 认证与授权设计基线](../security/P1.5-认证与授权设计基线.md)
- [ADR-019：P1.5 认证与授权闭环](ADR-019-P1.5认证与授权闭环.md)
- [IAM、安全协议、Gateway 与 Resource Server 运行规范](../engineering/standards/security-protocol-runtime-standard.md)

协议依据：

- RFC 6749：OAuth 2.0 Authorization Framework；
- RFC 7636：Authorization Code PKCE；
- RFC 7009：OAuth 2.0 Token Revocation；
- RFC 9700：OAuth 2.0 Security Best Current Practice；
- OpenID Connect Core 1.0；
- OpenID Connect RP-Initiated Logout 1.0；
- Spring Authorization Server Protocol Endpoints 与 Configuration Model。

## 20. 当前停止状态

`S07: In Progress / Awaiting Chris Approval；S08: Not Started`

本 ADR 尚未获得 Chris 批准，不得标记为 Accepted，不得据此实施 S08 或改变认证运行时。
