# ADR-024：PC JSON 与 Mobile PKCE/OIDC 双通道

- 状态：Accepted
- 提出日期：2026-07-29
- 接受日期：2026-07-29
- 决策人：Chris
- 批准来源：Chris 在 P1.6 S07-B 当前对话中的明确输入
- Slice：P1.6 S07
- 替代：[ADR-019：P1.5 认证与授权闭环](ADR-019-P1.5认证与授权闭环.md)
- 关联文档：[P1.6 S06 IAM 端点、调用方与职责审计](../engineering/P1.6-S06-IAM端点调用方与职责审计.md)、[IAM、安全协议、Gateway 与 Resource Server 运行规范](../engineering/standards/security-protocol-runtime-standard.md)、[P1.6 IAM 与 System 平台治理计划](../plans/P1.6-IAM与System平台治理计划.md)

> Chris 已明确接受本 ADR 的双通道长期方向、IAM 密码认证权威边界、IAM 授权上下文权威边界，
> 以及共享 Session、Refresh、JWT Claims、Token 签发和撤销核心的职责边界。
>
> 本次接受只完成架构决策和 S07 文档收口，不代表统一应用服务、Claims Assembler、Token Issuer、
> Mobile Logout、Resource Server revoked sid 或 Redirect URI 环境配置已经实现。
> 本次不修改任何生产实现、公开 API、认证运行时、Token Claim、Session、Refresh Token、
> revoked sid、Gateway、Resource Server、mom-web 或 mom-mobile；S08 保持 Not Started。

## 1. 背景

P1.5 的目标设计曾统一要求 Web 与 Mobile 使用 Authorization Code + PKCE S256 + OIDC。
P1.6 S06 通过三个仓库固定 Ref 完成真实调用方审计后，确认当前运行时事实与原目标设计存在差异：

1. MOM Admin、Supplier Portal、Customer Portal 三个 PC 应用实际使用第一方 JSON Runtime；
2. Mobile 实际使用系统浏览器、Authorization Code、PKCE S256、OIDC、标准 Authorization Endpoint、
   标准 Token Endpoint、ID Token、JWK、Issuer、Audience、Nonce 校验和 Gateway Bearer API；
3. Mobile 不调用第一方 JSON 登录端点；
4. 第一方 JSON Refresh 与标准 OAuth2 Refresh Grant 都调用 `IamSessionTokenService.rotate`；
5. 两条通道共享 Session、Opaque Refresh Token、Refresh Rotation、Reuse Detection、JWT Access Token、
   revoked sid 和 Session 撤销语义；
6. 两条通道是不同外部协议 Adapter，不是两套独立安全权威模型；
7. 当前没有证据支持立即删除 Spring Authorization Server、OAuth2/OIDC、IAM HTML Login、
   第一方 JSON Login/Refresh、标准 OAuth2 Refresh Grant、任一 Refresh Adapter 或 mom-web 标准 PKCE Package。

S07 需要把当前真实运行时、长期协议方向、内部认证权威、Token 签发职责和后续行为保持重构边界统一冻结，
避免把“外部协议不同”错误演变为“内部安全核心分裂”，也避免把第一方 JSON 登录重新包装成 Password Grant。

## 2. 问题

本 ADR 回答：

1. PC 第一方 JSON 与 Mobile 标准 PKCE/OIDC 是否长期并存；
2. 两条外部协议是否共享同一个账号认证、授权上下文、Session、Refresh、JWT Claims、Token 签发和撤销核心；
3. 第一方 JSON Login 的协议定位和密码认证边界；
4. Role、Permission、Factory、Party 等授权参数的权威来源；
5. Spring Authorization Server Token 生成基础设施是否可以被两条 Adapter 复用；
6. 为什么禁止 Password Grant、自定义密码 Grant 和 IAM 内部 HTTP 回环；
7. Web 标准 PKCE Package、Mobile Logout 和 Redirect URI 的长期状态；
8. 双通道必须保持哪些一致行为，哪些请求、响应和错误格式允许不同；
9. S08、S09、S10 可以实施什么、不得改变什么；
10. 哪些变更仍需要 Chris 单独批准。

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

P1.6 不要求三个 PC 应用迁移到页面跳转式 PKCE/OIDC。

### 3.2 Mobile 当前 Runtime

Mobile 实际使用：

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

Mobile 不调用第一方 JSON登录端点，也不改为第一方 JSON Runtime。

### 3.3 当前单一 Session/Refresh 权威核心

第一方 JSON Refresh 与标准 OAuth2 Refresh Grant 均调用：

`IamSessionTokenService.rotate`

并共享：

- Session；
- Opaque Refresh Token；
- Refresh Rotation；
- 单 ACTIVE Refresh；
- Reuse Detection；
- JWT Access Token；
- revoked sid；
- Session 撤销语义。

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

## 4. 决策摘要

采用以下长期架构：

> **PC 第一方 JSON + Mobile Authorization Code/PKCE/OIDC 双通道长期并存。两条外部协议共享 IAM 内部唯一的账号认证、授权上下文、Session、Refresh、JWT Claims、Token 签发和撤销权威核心。**

冻结边界：

1. PC Web 继续使用第一方 JSON；
2. Mobile 继续使用系统浏览器 Authorization Code + PKCE S256 + OIDC；
3. PC JSON Login 是 MOM 第一方认证协议 Adapter，不是 OAuth2 Password Grant；
4. 用户名和密码只能由 IAM 接收和校验；
5. Role、Permission、Factory、Party 和其他授权 Claim 只能由 IAM 权威数据加载；
6. 两条 Adapter 共享认证后的 Session、Refresh、Claims、Token 签发和撤销核心；
7. 可以评估复用 SAS `OAuth2TokenGenerator`、`JwtGenerator`、`OAuth2TokenCustomizer<JwtEncodingContext>`
   或等价内部组件，但不得改变现有行为；
8. 禁止第一方 JSON Controller 通过内部 HTTP 调用自己的 `/oauth2/token`；
9. 不引入 BFF；
10. 不复制第二套 Session、Refresh Rotation、Claims、Access Token Issuer 或撤销机制；
11. 不强制统一两条协议的请求、响应、页面、Redirect 或错误格式；
12. OAuth2/OIDC 标准响应不得被包装成第一方 JSON 响应；
13. 本 ADR 只冻结职责与允许方向，不声明统一组件已经实现。

## 5. 第一方 JSON Adapter 定位

PC 第一方 JSON Login 是面向 MOM 第一方 PC 应用的认证协议 Adapter。

它可以接收协议所需的输入，例如：

- `clientId`；
- `username`；
- `password`；
- 验证码或未来认证挑战；
- 明确允许的登录上下文。

它负责：

- JSON Request DTO 解析和输入规范化；
- 调用 IAM 内部认证用例；
- 返回现有第一方 JSON 兼容响应；
- 把协议错误转换为第一方兼容错误；
- 不泄露账号存在性、密码、Token 明文、内部栈或敏感状态。

它不负责独立维护：

- 账号认证规则；
- Client Policy；
- Role、Permission、Factory、Party 加载；
- Session 模型；
- Refresh Rotation；
- JWT Claims 规则；
- Access Token 签名；
- revoked sid；
- 撤销模型。

第一方 JSON 请求和 OAuth2/OIDC 标准请求不需要相同格式；第一方 JSON 错误和 OAuth2/OIDC 标准错误也不需要相同格式。

## 6. Password Grant 结论

PC 第一方 JSON Login：

- 不是 OAuth2 Resource Owner Password Credentials Grant；
- 不得使用 `grant_type=password`；
- 不得通过 `urn:mom:grant-type:*` 或其他自定义 Grant Type 重新包装用户名密码换 Token；
- 不得把现有 JSON Login 迁移到标准 Token Endpoint 以制造表面协议统一；
- 不得要求 SAS Token Endpoint 接管第一方 JSON 登录请求与错误契约。

禁止原因：

1. 客户端用户名密码直接成为 OAuth2 Grant 会扩大凭证暴露面；
2. 会混淆标准 Token Endpoint 与第一方认证用例；
3. 首次改密、验证码、MFA、锁定和风险控制难以保持现有语义；
4. 会破坏 OAuth2/OIDC 标准错误边界；
5. 自定义 Grant 名称不能改变其“用户名密码直接换 Token”的安全本质；
6. 当前双通道已经共享安全核心，不需要通过 Password Grant 达成内部复用。

## 7. 密码认证权威边界

用户名和密码只能进入 IAM。

允许承担密码认证职责的边界：

- `mom-iam-server`；
- IAM 内部 `AuthenticationManager` 或等价认证基础设施；
- IAM 内部账号认证 Application Service；
- IAM 权威账号、状态、锁定、首次改密和安全审计能力。

以下组件不得校验、转发或长期持有用户密码：

- MOM Admin 普通业务后端；
- Supplier Portal 业务服务；
- Customer Portal 业务服务；
- Gateway；
- `mom-system-platform`；
- MDM；
- WMS；
- QMS；
- Integration；
- 其他业务 Resource Server；
- PC 前端以外的中间代理；
- Mobile；
- 任意第三方业务服务。

PC 前端只允许将用户主动输入的密码通过受保护连接提交给 IAM 第一方 JSON Login Endpoint。
密码不得在客户端持久化，不得写入日志、Trace、安全审计、URL、浏览器存储或普通业务请求。

## 8. 授权上下文权威边界

以下授权信息只能由 IAM 权威数据加载并校验：

- User；
- Account Status；
- User Type；
- Client Policy；
- Role；
- Permission；
- Factory；
- Party；
- Mobile Access；
- Session Context。

不得接受客户端或普通业务服务传入的以下值作为 Token 签发依据：

- `roles`；
- `permissions`；
- `factory_ids`；
- `party_type`；
- `party_id`；
- `user_type`；
- `sid`；
- 任意授权 Claim。

最终 Role、Permission、Factory 和 Party 必须由 IAM 查询、交叉校验并形成授权快照。
客户端输入的 `clientId` 也必须与 IAM Client Registration 和 Client Policy 进行权威校验，不能被直接信任。

## 9. 统一认证后权威核心

两条 Adapter 必须共享以下内部权威能力：

1. 账号认证规则；
2. Client Policy；
3. 账号状态和用户类型；
4. 首次改密；
5. 授权上下文加载；
6. Session 创建和绝对有效期；
7. Opaque Refresh Token；
8. Refresh Rotation；
9. 单 ACTIVE Refresh；
10. Reuse Detection；
11. Session 撤销；
12. revoked sid；
13. JWT Claim 语义；
14. JWT Token 签发；
15. 安全审计事件；
16. Fail Closed 语义。

Controller、AuthenticationConverter、AuthenticationProvider、Handler 和协议响应序列化器只属于 Adapter。
不得复制第二套 Session Service、Refresh Rotation、JWT Claims 规则、Access Token Issuer 或撤销机制。

## 10. 概念职责与允许实现方向

以下名称只表示建议职责，不批准具体类名、包结构或 S08 实现细节。

### 10.1 `IamFirstPartyLoginApplicationService`

建议负责：

- 编排第一方 Client Policy 校验；
- 调用账号认证；
- 处理账号状态和首次改密分支；
- 加载授权上下文；
- 调用 Session/Refresh 权威服务；
- 调用统一 Access Token Issuer；
- 触发安全审计；
- 向 JSON Adapter 返回协议中立结果。

### 10.2 `IamAuthorizationContextLoader`

建议负责加载并校验：

- 当前用户；
- User Type；
- Role；
- Permission；
- Factory；
- Party；
- Client Policy；
- 当前账号和授权状态。

### 10.3 `IamJwtClaimsAssembler`

建议统一构造：

- `iss`
- `sub`
- `aud`
- `iat`
- `nbf`
- `exp`
- `jti`
- `sid`
- `client_id`
- `user_type`
- `roles`
- `permissions`
- `factory_ids`
- `party_type`
- `party_id`

JSON Controller、OAuth2 Provider 或 Handler 不得各自维护不同 Claims 语义。

### 10.4 `IamAccessTokenIssuer`

建议负责：

- 使用统一 RSA Key 和 `kid`；
- 使用统一 Issuer；
- 使用统一 Audience；
- 使用统一 TTL；
- 使用统一 Claims Assembler；
- 签发 JWT Access Token。

允许评估复用：

- Spring Authorization Server `OAuth2TokenGenerator`；
- Spring Authorization Server `JwtGenerator`；
- `OAuth2TokenCustomizer<JwtEncodingContext>`；
- 或满足相同职责边界的内部组件。

复用前提：

- 不改变 JWT Claim；
- 不改变签名算法；
- 不改变 `kid`；
- 不改变 Access Token TTL；
- 不改变 Session 期限；
- 不改变 Refresh Token；
- 不改变 Refresh Rotation；
- 不改变 Reuse Detection；
- 不改变 revoked sid；
- 不改变 OAuth2/OIDC 标准响应；
- 不改变第一方 JSON 请求、响应和错误契约。

S07 不创建这些类，也不声称它们已经实现。

## 11. 禁止 IAM 内部 HTTP 回环

禁止：

```text
/api/iam/auth/login
  → HTTP localhost/oauth2/token
```

原因：

- 产生不必要的内部 HTTP 回环；
- 破坏认证、Session 和审计事务边界；
- 可能重复创建 Session 或 Refresh；
- 可能重复记录安全审计；
- 增加错误转换和协议耦合；
- 混淆 Session/Refresh 权威组件；
- 需要伪造 Authorization Code 或新增自定义 Grant；
- 造成自身网络依赖和额外故障域。

正确方向是两个 Adapter 直接调用相同的内部 Java 权威组件，不通过 HTTP 调用自身端点。

## 12. 目标拓扑

```text
PC Web
  → First-Party JSON Adapter
  → IAM Account Authentication
  → IAM Authorization Context Loader
  → IamSessionTokenService
  → Unified JWT Claims Assembler
  → Unified Access Token Issuer
  → First-Party JSON Response

Mobile
  → System Browser
  → Authorization Code + PKCE S256 + OIDC
  → SAS OAuth2/OIDC Adapter
  → IAM Account Authentication
  → IAM Authorization Context Loader
  → IamSessionTokenService
  → Unified JWT Claims Assembler
  → Unified Access Token Issuer
  → OAuth2/OIDC Standard Response
```

该拓扑表示目标职责，不表示 S07 已完成内部组件抽取。

两条协议：

- 请求格式可以不同；
- 响应格式可以不同；
- 错误格式可以不同；
- 页面、Redirect 和客户端恢复流程可以不同；
- 内部账号认证、授权数据、Session、Refresh、Claims、Token 和撤销语义必须一致；
- 不得为了格式统一破坏 OAuth2/OIDC 标准响应。

## 13. Web 标准 PKCE Package 产品状态

接受产品状态：

`Compatibility / Future Migration Capability`

冻结规则：

1. 当前 MOM Admin、Supplier Portal、Customer Portal 没有接入该 Package；
2. 当前不删除；
3. 当前不要求三个 PC 应用接入；
4. 它不应被描述为当前生产 Runtime；
5. 它可作为未来 PC 标准 PKCE 迁移能力和兼容验证资产；
6. 当前未接入不等于废弃；
7. 代码重复或 Package 体积不构成删除依据。

后续删除必须同时具备：无调用方证据、产品方向裁决、兼容窗口、回滚方案、跨仓库 E2E、
Redirect URI 与 Client Registration 核验，以及 Chris 明确批准。

## 14. Mobile Logout 目标安全语义

本 ADR 接受目标语义，但不创建具体 API、不选择具体协议组合、不声明已经实现。

Mobile Logout 必须达到：

1. 服务端权威 Session 被撤销；
2. 对应 Refresh Token 失效；
3. 已签发 Access Token 的 `sid` 进入现有 revoked sid 机制；
4. 本地 Token、安全存储、授权事务和运行时状态被清理；
5. 浏览器 OIDC 会话清理属于附加协议步骤，不能替代 MOM Session 撤销；
6. 网络结果不确定时 Fail Closed，不得继续使用旧 Refresh Token；
7. 客户端本地清理成功但服务端结果未知时，仍丢弃本地 Refresh Token并要求重新认证；
8. 审计关联用户、Client、`sid`、结果和失败原因，但不记录 Token 明文；
9. 管理员撤销和主动 Logout 最终收敛到同一 Session 撤销语义；
10. 具体使用 RP-Initiated Logout、Token Revocation、现有 Session 撤销能力或内部 Adapter，留给后续设计；
11. 未经独立 API 契约 Review，不得新增公开端点。

EG-01 的目标决策已关闭；具体协议、API、客户端实现和 E2E 仍是后续实施项。

## 15. Mobile Redirect URI 分环境策略

冻结原则：

1. Redirect URI 必须精确登记，禁止通配；
2. 正式 Client 不依赖未验证的动态 Redirect；
3. 本地 H5 或开发服务器只使用明确的开发环境 URI；
4. Android 本地开发可使用精确登记的开发 Scheme 或开发 App Link；
5. 正式 Android 优先使用经过平台验证的 HTTPS App Link；
6. H5、Android 开发、测试、预生产、生产使用独立 Client 或独立精确 URI 集合；
7. IAM 默认值和 mom-mobile 默认值不一致必须在 S10 E2E 前关闭；
8. Secret、Client ID、Redirect URI 和环境配置不得写入 Token Claim 或用户偏好；
9. Redirect URI 变化属于协议兼容事件，必须同步 Registration、客户端配置、测试和回滚记录；
10. 正式环境不得复用 loopback、开发 Scheme 或测试 App Link。

以下值是待环境配置确认的占位符，不代表已部署事实：

| 运行形态 | Client 边界 | 精确 Redirect URI 占位符 | 验证要求 | 状态 |
|---|---|---|---|---|
| H5 本地开发 | 独立开发 Client | `<mobile-dev-loopback-uri>` | 精确端口、路径和 Origin | 待环境配置确认 |
| Android 本地开发 | 独立开发 Client | `<android-dev-callback>` | 包名、Scheme/Host/Path、签名环境匹配 | 待环境配置确认 |
| Android 测试 | 独立测试 Client | `<android-test-app-link>` | 平台关联文件、测试签名、回调 E2E | 待环境配置确认 |
| Android 预生产 | 独立预生产 Client | `<android-staging-app-link>` | HTTPS、平台验证、与生产隔离 | 待环境配置确认 |
| Android 生产 | 独立生产 Client | `<android-production-app-link>` | HTTPS App Link、精确登记、发布验收 | 待环境配置确认 |

EG-03 的架构决策已关闭；正式 Client ID、URI、平台关联和跨仓库 E2E 仍需 S10 收口。

## 16. 双通道行为一致性矩阵

分类：

- **完全一致**：共享安全语义和权威数据；
- **协议差异允许**：请求、响应、页面或错误格式可以不同；
- **PC 专属**：只属于第一方 JSON Runtime；
- **Mobile/OIDC 专属**：只属于 Authorization Code + PKCE/OIDC Runtime。

| 行为 | PC 第一方 JSON | Mobile PKCE/OIDC | 决策 |
|---|---|---|---|
| 账号状态校验 | JSON 用例校验 | IAM 页面/授权流程校验 | 完全一致 |
| 用户类型 | Admin/Supplier/Customer Client Policy | Mobile INTERNAL + Mobile Access | 完全一致 |
| Client Policy | JSON Adapter 调用权威策略 | Authorization/Token Adapter 调用权威策略 | 完全一致 |
| 首次改密 | 第一方改密端点 | IAM 页面保存请求后续接 | 安全语义一致，协议不同 |
| Session 绝对有效期 | 按 PC Client Policy | 按 Mobile Client Policy | 模型一致，期限可按 Client 不同 |
| Access Token TTL | 统一 Token 签发规则 | 统一 Token 签发规则 | 完全一致 |
| Refresh Rotation | JSON Refresh Adapter | OAuth2 Refresh Grant Adapter | 完全一致 |
| 单 ACTIVE Refresh | 同一数据库约束 | 同一数据库约束 | 完全一致 |
| Reuse Detection | 第一方兼容错误 | OAuth2 标准错误 | 处置一致，格式不同 |
| Session 撤销 | JSON Logout/管理员撤销 | Mobile 后续编排/管理员撤销 | 完全一致 |
| revoked sid | Gateway/Resource Server | Gateway/Resource Server | 完全一致 |
| JWT Claims | 统一 Claims Assembler | 统一 Claims Assembler | 完全一致 |
| JWT 签名、Issuer、Audience、TTL | 统一 Access Token Issuer | 统一 Access Token Issuer | 完全一致 |
| `/api/iam/me` | Bearer 调用 | Bearer 调用 | 完全一致 |
| Role、Permission、Factory、Party | IAM 权威数据 | IAM 权威数据 | 完全一致 |
| Logout | 服务端撤销并清本地状态 | 目标语义已接受，实施待后续 | 安全语义一致，编排不同 |
| 安全审计 | 第一方事件类型 | OAuth/OIDC 事件类型 | 字段与泄露边界一致，事件名可不同 |
| 错误信息泄露 | 第一方兼容错误 | OAuth2/OIDC 标准错误 | 泄露边界一致，格式不同 |
| Fail Closed | Refresh/Logout 不确定时关闭 | Refresh/Logout 不确定时关闭 | 完全一致 |
| 时钟偏差 | JWT 校验 | JWT 与 ID Token 校验 | 统一允许偏差策略 |
| 并发 Refresh | Single Flight + 服务端行锁 | Lease/Replacement + 服务端行锁 | 服务端结果一致，客户端机制不同 |
| 管理员撤销 Session | 下一次请求被阻断 | 下一次请求被阻断 | 完全一致 |
| 请求格式 | MOM JSON DTO | OAuth2/OIDC 参数 | 协议差异允许 |
| 响应格式 | MOM 第一方 JSON | 标准 Token JSON/Redirect | 协议差异允许 |
| 登录页面与跳转 | PC 站内表单 | 系统浏览器、IAM 页面、回调 | 协议差异允许 |
| OIDC ID Token | 不适用 | 必须验证 Issuer、JWK、Audience、Nonce | Mobile/OIDC 专属 |
| `state`、Nonce、PKCE verifier | 不适用 | 必须生成、保存并校验 | Mobile/OIDC 专属 |
| 第一方首次改密端点 | 当前 PC 使用 | 不调用 | PC 专属 |

## 17. 候选方案结论

### 17.1 方案 A：双通道并存、共享权威核心

**Accepted。**

收益：符合真实调用方、迁移风险最低、保留 Mobile 标准安全边界、共享统一安全核心。
代价：长期维护两个协议 Adapter，必须维护跨协议一致性测试和清晰文档。

### 17.2 方案 B：PC 逐步迁移到标准 PKCE/OIDC

**Future Evaluation，不作为 P1.6 强制目标。**

未来评估必须覆盖登录 UX、Token 存储、回调与 Redirect、首次改密、Logout、三个 PC 应用迁移、
兼容窗口、旧客户端和回滚成本，并由 Chris 单独批准。

### 17.3 方案 C：Mobile 改用第一方 JSON

**Rejected。**

它会放弃系统浏览器授权，弱化 PKCE/OIDC/Nonce/JWK/App Link 边界，重新引入移动端凭证采集，
与当前 Mobile Runtime 不一致且收益不足。

### 17.4 方案 D：引入 BFF

**Deferred，不进入 P1.6。**

BFF 可让浏览器不直接持有 Token，但会引入 Cookie Session、CSRF、Session Store、高可用、
多 PC 应用边界、额外部署单元和故障域，需要新的产品与架构驱动以及独立 ADR。

## 18. 与 ADR-019 的关系

本 ADR 接受后，ADR-019 标记为 `Superseded by ADR-024`。

原因：ADR-019 冻结“全部用户客户端采用 Authorization Code + PKCE/OIDC”，与 S06 已确认的 PC 第一方 JSON Runtime
及本 ADR 接受的长期双通道决策冲突。仓库 ADR 规则要求决策变化时创建新 ADR，并把旧 ADR 标记为 Superseded。

ADR-019 继续保留为 P1.5 历史设计和实施证据，不删除、不重写其历史结论。
P1.5 认证与授权设计基线也继续保留历史实施价值；其中与 PC Runtime 冲突的 PKCE、Web Token 恢复和页面跳转条款，
自本 ADR 接受之日起由 ADR-024 替代。

ADR-024 完整继承 ADR-019 中仍有效的原则：

1. 使用 Spring Authorization Server，不自研无 Session 的密码 JWT；
2. Mobile 使用 Authorization Code + PKCE S256 + OIDC；
3. 禁止 Resource Owner Password Credentials Grant；
4. 禁止自定义密码 Grant；
5. Gateway API 使用 Bearer Access Token，不建设 BFF；
6. Access Token 使用短期 JWT，Refresh Token 使用高熵 Opaque Token；
7. Session 具有绝对有效期，Rotation 不延长绝对期限；
8. Refresh Token 摘要存储、单 ACTIVE、事务行锁、Reuse Detection；
9. JWT `sid`、revoked sid 和 Redis 故障 Fail Closed；
10. User → Role → Permission，Factory Scope 和 Party Scope；
11. Gateway 做协议和粗粒度入口，业务 Resource Server 做最终授权；
12. `/api/iam/me` 是正式权限上下文来源；
13. 前端或 Mobile UI 权限不是最终安全边界；
14. 客户端和普通业务服务不得提供授权 Claims；
15. CurrentActor、审计和 P1.5 已完成数据库模型继续有效。

## 19. Resource Server revoked sid

Gateway revoked sid Fail Closed 与客户端认证通道是两个独立问题。

本 ADR：

- 不取消业务 Resource Server 独立 JWT 验证；
- 不把 Gateway 当作业务服务唯一认证依据；
- 如果业务服务存在绕过 Gateway 的访问路径，必须具备等价撤销能力，或由部署边界证明无法直连；
- 保留 EG-02；
- 不实现 Filter、Redis 查询或网络调用；
- 具体实现、部署证明和回归由 S08/S10 收口。

## 20. S08、S09、S10 实施边界

### 20.1 S08 允许范围

S08 可以在行为保持前提下评估或实施：

- First-Party Login Application Service 抽取；
- JSON Controller 只保留 DTO 和协议响应；
- 账号认证编排收口；
- Authorization Context Loader 职责抽取；
- JWT Claims Assembler 职责抽取；
- Access Token Issuer 职责抽取；
- 评估复用 SAS `JwtGenerator` 或 `OAuth2TokenGenerator`；
- 配置类无行为拆分；
- 双通道 Claims 等价性测试；
- 现有公开协议回归测试；
- EG-02 设计和证据收口。

### 20.2 S08 禁止范围

S08 不得：

- 修改 JSON API 路径、请求、响应或错误契约；
- 修改 OAuth2/OIDC 标准端点或响应；
- 增加 Password Grant 或自定义密码 Grant；
- 让 JSON Login 内部 HTTP 调用 `/oauth2/token`；
- 修改首次改密、Login、Refresh 或 Logout 语义；
- 修改 JWT Claim、签名、`kid`、Issuer、Audience 或 TTL；
- 修改 Session 期限、Refresh 模型、Rotation、Reuse Detection 或 revoked sid；
- 修改 Permission、Gateway 或 Resource Server 运行时；
- 删除任何 Adapter；
- 自动进入 S09。

如果复用 SAS Token Generator 会导致任何行为变化，S08 必须停止该部分并记录 Evidence Gap，不得强行统一。

### 20.3 S09

S09 可以处理 IAM Admin 职责拆分、错误模型行为保持整理、Token Adapter 内部职责整理和内部重复代码收敛。
不得包装 OAuth2/OIDC 标准错误，不得删除 Adapter，不得修改外部协议、Claims 或 Token 生命周期。

### 20.4 S10

S10 必须覆盖：

- PC Admin、Supplier、Customer Login；
- 首次改密；
- Mobile PKCE/OIDC；
- JSON Refresh 和 OAuth2 Refresh Grant；
- 并发 Refresh 和 Reuse Detection；
- PC Logout 和 Mobile Logout；
- 管理员撤销 Session；
- Gateway revoked sid；
- 业务 Resource Server revoked sid 或等价部署证明；
- `/api/iam/me`；
- Role、Permission、Factory、Party；
- Redirect URI 分环境；
- JWT Claim 等价性；
- 两条 Adapter 的统一 Token 签发证据。

## 21. 兼容、迁移和删除门禁

任一认证入口、Adapter 或 Package 删除前必须：

1. 重新生成固定 Ref 的跨仓库调用方矩阵；
2. 完成 PC Admin、Supplier、Customer 和 Mobile E2E；
3. 覆盖 Login、首次改密、Refresh、Reuse Detection、Logout 和管理员撤销；
4. 覆盖 Gateway 和业务 Resource Server；
5. 定义旧客户端兼容窗口；
6. 定义回滚策略；
7. 确认 Redirect URI 和 Client Registration；
8. 由 Chris 明确批准；
9. 不得仅凭代码重复、类过大或当前调用较少删除协议能力；
10. S08/S09 的重构不得改变本 ADR 冻结的外部协议和安全语义。

## 22. 仍需 Chris 单独批准的事项

本 ADR Accepted 不自动批准：

- PC 迁移标准 PKCE/OIDC；
- Mobile 改为第一方 JSON；
- 引入 BFF；
- 新增自定义 OAuth2 Grant；
- 新增公开认证 API；
- 删除 JSON Login；
- 删除 OAuth2/OIDC；
- 删除 IAM HTML Login；
- 删除标准 Refresh Grant；
- 删除任一 Refresh Adapter；
- 删除 mom-web 标准 PKCE Package；
- 修改正式 Client ID；
- 修改正式 Redirect URI；
- 修改 JWT Claim、签名或 Token TTL；
- 修改 Session、Refresh 或 revoked sid 模型；
- 修改 Gateway 或业务 Resource Server 撤销策略；
- PR #33 转 Ready 或合并。

上述事项执行前必须说明预期结果、兼容影响、风险与回滚方式，并获得 Chris 明确批准。

## 23. 正向后果

- 架构与当前真实调用方一致；
- PC 保留站内 JSON 登录 UX；
- Mobile 保留标准 PKCE/OIDC 安全边界；
- 密码和授权数据权威明确集中在 IAM；
- Session、Refresh、Claims、Token 和撤销不复制；
- S08/S09 获得清晰的行为保持拆分边界；
- 未来可以在不改变协议的前提下复用 SAS Token 生成基础设施；
- 协议差异不会破坏 OAuth2/OIDC 标准响应。

## 24. 负向后果与技术债

- 长期维护两个外部协议 Adapter；
- 需要双通道一致性和 Claim 等价性测试；
- 当前统一 Claims Assembler 和 Access Token Issuer 职责尚未显式抽取；
- Mobile Logout 目标语义尚未实施；
- 业务 Resource Server revoked sid 等价能力尚未闭环；
- Redirect URI 正式矩阵尚待环境确认；
- ADR-019 和 P1.5 基线需要作为历史资料理解，当前协议权威转移到 ADR-024。

## 25. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| JSON 与 OAuth2 Adapter 各自维护 Claims | 抽取统一 Claims Assembler，并做 Claim 等价性测试 |
| 为复用 SAS 引入行为变化 | S08 只允许行为保持；变化立即停止并登记 Evidence Gap |
| 普通业务服务接触密码 | ADR 明确禁止，架构测试和 Review 检查调用路径 |
| 客户端伪造授权参数 | IAM 只从权威数据加载，不接受客户端授权 Claims |
| JSON 被包装成 Password Grant | 明确禁止 `grant_type=password` 和自定义密码 Grant |
| IAM 内部 HTTP 调用自身 Token Endpoint | 明确禁止，两个 Adapter 直接调用内部 Java 核心 |
| 双通道错误格式被强制统一 | 保留协议差异，OAuth2/OIDC 标准错误不包装 |
| Mobile Logout 只清本地状态 | S10 前实现并验证服务端 Session 撤销目标语义 |
| 业务服务绕过 Gateway 后忽略撤销 | S08/S10 完成等价能力或部署不可直连证明 |
| 文档把未实现职责写成已完成 | 进度和 ADR 明确区分 Accepted 决策与后续实施 |

## 26. 验证方式

### S07 文档验证

- ADR 状态和批准记录检查；
- ADR-019 与 ADR-024 状态关系检查；
- Password Grant、自定义密码 Grant和内部 HTTP 回环禁止项检查；
- IAM 密码认证和授权上下文权威边界检查；
- S07 Completed、S08 Not Started 全仓状态检查；
- Git diff 仅包含预期 Markdown；
- 最终 Head Engineering Baseline 和全 Reactor `clean verify`；
- 最终 Head 必需 GitHub Actions。

### S08/S10 后续验证

- 双通道 JWT Claim、签名、Issuer、Audience、TTL 等价性；
- Client 与 user_type 入口隔离；
- 首次改密；
- Refresh Rotation、并发 Refresh、Reuse Detection；
- PC/Mobile Logout 和管理员撤销；
- Redis revoked sid Fail Closed；
- Gateway 和业务 Resource Server；
- Redirect URI、state、nonce、PKCE、ID Token；
- `/api/iam/me`、Role、Permission、Factory、Party；
- 日志和安全审计敏感信息扫描。

## 27. 替代与回滚条件

出现以下情况时可以提出新的 ADR：

- PC 标准 PKCE 迁移的产品与安全收益形成完整证据；
- BFF 成为组织级浏览器安全基线；
- Mobile 平台约束使当前 OIDC/PKCE 无法可靠运行；
- 外部 IAM 或协议栈发生不可兼容变化；
- 双 Adapter 一致性无法通过自动化和治理维持；
- 法规、审计或客户安全要求强制改变 Token/Session 边界。

替代 ADR 必须包含调用方矩阵、兼容窗口、跨仓库 E2E、回滚策略和 Chris 明确批准。
在替代方案完成前，继续保留双通道和单一权威核心。

## 28. 参考资料

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
- Spring Authorization Server Protocol Endpoints、Configuration Model 和 Token 组件。

## 29. S07 收口状态

`ADR-024: Accepted；S07: Completed；S08: Not Started`

本 ADR 已获得 Chris 明确批准。批准仅适用于本 ADR 冻结的架构边界，不代表 S08 已启动或任何生产实现已变更。