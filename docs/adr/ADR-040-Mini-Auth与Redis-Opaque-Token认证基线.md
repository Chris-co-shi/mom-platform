# ADR-040：Mini Auth 与 Redis Opaque Token 认证基线

- 状态：Accepted
- 提出日期：2026-09-03
- 接受日期：2026-09-03
- 决策人：Chris
- 适用范围：`mom-platform` V1 认证与授权运行时
- 替代：ADR-024 在 V1 认证运行时上的结论；ADR-019 保持历史记录
- 关联文档：
  - [P1.5 认证与授权设计基线](../security/P1.5-认证与授权设计基线.md)
  - [安全架构](../architecture/安全架构.md)
  - [CurrentActor 与数据审计基础](../architecture/CurrentActor与数据审计.md)

## 1. 背景

此前 IAM 方案逐步引入 Spring Authorization Server、OAuth2/OIDC Client、JWT、Refresh Token、Session、Refresh Rotation、撤销状态、Factory/Party Scope、安全审计平台等能力。

这些能力并非错误，但对当前第一版目标来说过重，导致认证模块的概念数量、依赖数量、运行链路和维护成本远超当前业务需要，项目整体失去可控性。

当前 V1 的目标不是建设完整身份平台，而是先稳定完成最小业务闭环：

```text
User → Role → Permission → Login → Token → Gateway → Business Service → @PreAuthorize
```

因此需要重新冻结一套更小、可解释、可测试、可逐步演进的认证基线。

## 2. 决策

V1 采用 **第一方账号密码认证 + Redis-backed Opaque Access Token**。

不再以 JWT 或 Spring Authorization Server 作为第一版认证基础。

### 2.1 Token 形态

登录成功后由 `mom-auth`：

1. 校验用户名和密码；
2. 生成高熵随机 Opaque Token；
3. 构造认证快照 `MomTokenPrincipal`；
4. 通过 `MomTokenStore` 写入 Redis；
5. 返回原始 Opaque Token 给客户端。

推荐 Token 生成规则：

- `SecureRandom`；
- 32 字节随机数（256 bit）；
- Base64 URL Safe 编码；
- 不从用户 ID、时间戳或其他业务字段推导。

### 2.2 Redis 存储

逻辑关系：

```text
raw token → MomTokenPrincipal
```

Redis 物理 Key：

```text
mom:token:{SHA-256(rawToken)}
```

Redis Value 为 `MomTokenPrincipal` 的 JSON：

```json
{
  "userId": "10001",
  "authorities": [
    "ROLE_ADMIN",
    "mes:work-order:create"
  ],
  "expiresAt": "2026-09-03T10:30:00Z"
}
```

Redis TTL 必须由 `expiresAt - now` 一次性写入。

原始 Bearer Token 不作为 Redis Key 暴露。

### 2.3 Token Principal

V1 `MomTokenPrincipal` 只包含：

- `userId`
- `authorities`
- `expiresAt`

其中 `authorities` 是认证运行时的扁平权限视图，可以同时包含：

```text
ROLE_ADMIN
ROLE_OPERATOR
mes:work-order:create
wms:inventory:adjust
```

不在 Token Principal 中引入用户名、显示名、邮箱、手机号、Factory、Party、Client、Session、Refresh Token 等字段。

### 2.4 Resource Server

业务服务仍然是独立 Resource Server。

请求链路：

```text
Authorization: Bearer <opaque-token>
        ↓
BearerTokenAuthenticationFilter
        ↓
OpaqueTokenAuthenticationProvider
        ↓
MomOpaqueTokenIntrospector
        ↓
MomTokenStore.find(token)
        ↓
MomTokenPrincipal
        ↓
GrantedAuthority
        ↓
SecurityContext
        ↓
@PreAuthorize
```

`MomOpaqueTokenIntrospector` 必须显式保证：

```text
Authentication#getName() == userId
```

这是 `CurrentActorProvider` 与数据审计链的稳定契约。

### 2.5 Gateway

Gateway 的默认职责是：

- 接收并原样转发 Bearer Token；
- 负责路由、CORS、限流、Correlation ID 等网关能力；
- 不作为唯一认证点。

业务服务继续独立校验 Token，除非未来网络边界发生明确变化并通过新的 ADR 重新决策。

V1 不默认在 Gateway 和业务服务中重复访问 Redis 做两次认证查询。

### 2.6 Logout

Logout 采用直接删除 Token Store 记录：

```text
POST /auth/logout
    ↓
MomTokenStore.remove(rawToken)
    ↓
Redis Key 删除
    ↓
Token 立即失效
```

删除操作应具备幂等语义：Token 已不存在时仍视为完成。

## 3. 安全失败语义

Token Store 是认证权威状态，不是普通缓存。

必须区分：

```text
Token 不存在
→ 无效 Token
→ 401
```

和：

```text
Redis / 存储基础设施故障
→ 基础设施异常
→ 不得伪装成 Token 不存在
```

原则：**Fail Closed**。

以下异常不得被 `MomTokenStore` 或 `MomOpaqueTokenIntrospector` 吞掉：

- Redis 连接失败；
- Redis 超时；
- JSON 数据损坏；
- 序列化/反序列化失败。

具体基础设施异常最终映射 401 还是 5xx，可在 HTTP 错误模型阶段单独冻结；但不得返回认证成功。

## 4. 模块职责

### `mom-security`

负责共享安全运行时基础设施：

```text
MomTokenPrincipal
MomTokenStore
MomTokenFingerprint
RedisMomTokenStore
MomOpaqueTokenIntrospector
SecurityCurrentActorProvider
Resource Server AutoConfiguration
Token Store AutoConfiguration
```

`mom-security` 不负责用户、角色、权限 CRUD，也不负责用户名密码认证。

### `mom-auth`

负责：

- User / Role / Permission；
- 密码校验；
- Login；
- Opaque Token 签发；
- Logout；
- 用户、角色、权限的基础管理能力。

`mom-auth` 通过 `MomTokenStore` 写入或删除 Token，不直接知道 Redis Key、SHA-256、JSON 或 TTL 实现。

### 业务服务

负责：

- Resource Server；
- 最终业务 Permission；
- 业务对象归属；
- 领域状态和业务规则；
- `@PreAuthorize` 和领域级最终授权。

Auth 只提供粗粒度角色和权限身份，不替代业务服务自己的领域授权。

## 5. V1 明确不建设

以下能力不属于当前 V1：

- Spring Authorization Server；
- OAuth2 Client 管理；
- OIDC；
- Authorization Code / PKCE；
- JWT Access Token；
- JWK / Issuer / Audience；
- Refresh Token；
- Session 子系统；
- Refresh Rotation；
- revoked sid / JWT blacklist；
- client_credentials；
- 动态 OAuth Client 策略；
- Factory / Party Scope 通用授权框架；
- SSO；
- 外部身份绑定；
- 安全审计平台。

这些能力未来有真实需求时重新评估，不提前为未来复杂度付费。

## 6. 后果

### 正面后果

- 认证链显著缩短；
- Token 可立即撤销；
- 不需要 JWT blacklist；
- 不需要 JWK、Issuer、Audience、Refresh、Session 等配套设施；
- Redis 数据可直接检查，便于第一版调试和掌控；
- `mom-auth` 与 Resource Server 通过 `MomTokenStore` 解耦；
- Spring Security 的 Bearer Token、Opaque Token、GrantedAuthority、`@PreAuthorize` 能力仍被保留。

### 代价

- 每次受保护请求需要访问 Token Store；
- Redis 成为认证可用性的关键依赖；
- 多地域或超大规模部署时可能需要重新评估 Token 验证方案。

当前阶段接受这些代价，以换取更高的可理解性、可控性和立即撤销能力。

## 7. 重新评估条件

出现以下真实需求之一时，再评估 JWT、OAuth2/OIDC 或完整身份协议：

- 对外第三方开放平台；
- 多组织 SSO；
- 标准 OAuth2/OIDC 互操作；
- 大规模跨地域 Resource Server，Redis 每请求查询成为明确瓶颈；
- Mobile / 外部应用明确需要标准 Authorization Code + PKCE；
- Refresh Token 成为明确产品需求。

重新评估必须通过新的 ADR，不能在现有实现中隐式恢复旧体系。
