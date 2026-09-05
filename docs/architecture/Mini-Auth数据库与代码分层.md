# Mini Auth 数据库与代码分层

- 状态：Current
- 当前事实来源：`fix/mini-auth`
- 最近更新：2026-09-05
- 模块：`mom-auth-platform/mom-auth-server`
- 认证决策：[ADR-040](../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- 分层决策：[ADR-042](../adr/ADR-042-MOM渐进式分层与对象模型.md)
- 数据关联决策：[ADR-026](../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 当前目标

Mini Auth V1 保持最小闭环：

```text
User → Role → Permission → Login → Opaque Token → Resource Server → Logout
```

不包含 Employee/Organization、OAuth Client、Session、Refresh Token、OIDC、JWT 或通用数据范围模型。

## 2. 数据库

PostgreSQL Schema：`mom_auth`。

核心表：

| 表 | 作用 |
|---|---|
| `auth_user` | 登录账号与密码摘要 |
| `auth_role` | 角色目录 |
| `auth_permission` | Permission 目录 |
| `auth_user_role` | 用户角色关系 |
| `auth_role_permission` | 角色权限关系 |

按 ADR-026 不建立物理外键；关系完整性由 Application、本地事务、唯一约束、索引和测试共同维护。

Token 不保存到 PostgreSQL，由 `mom-security.MomTokenStore` 保存到 Redis。

## 3. 当前代码分层

```text
io.github.chrisshi.mom.auth
├── controller
│   ├── request
│   └── response
├── application
│   └── model
└── infrastructure
    ├── configuration
    ├── entity
    ├── mapper
    ├── query
    └── security
```

依赖：

```text
controller → application → infrastructure
```

Controller 不直接访问 Mapper/Entity/Redis/PasswordEncoder；Application 可以直接依赖本 bounded context 的具体 Infrastructure。Mini Auth V1 不创建无业务价值的 Domain、Repository Port、Adapter、Converter 或 MyBatis-Plus `IService/ServiceImpl`。

`infrastructure/security` 存放 Spring Security 适配：

```text
AuthUserDetailsService
AuthUserPrincipal
```

它们存在的理由是隔离 Spring Security 与数据库 Entity，而不是为了形成新的架构层。

## 4. 对象模型

继续遵守 ADR-042：

```text
Request / Response  HTTP 契约
Entity              数据库行模型
View                Application 输出
Row / Projection    仅复杂 SQL 确实需要时出现
```

新增约束：

- `UserEntity` 不实现 `UserDetails`；
- Spring Security Principal 使用独立 `AuthUserPrincipal`；
- `Result<T>` 是 HTTP 返回信封，位于 `mom-webmvc`，不得成为 Application 返回模型；
- 通用分页直接使用 `mom-core.PageResult<T>`，不得再增加只复制分页字段的 Auth 私有 View/Response。

## 5. 分页

对外分页统一为：

```text
pageNo + pageSize
        ↓
Application
        ↓
PageResult<T>
```

`PageResult<T>`：

```text
records / pageNo / pageSize / total / totalPages
```

Controller 可通过 `PageResult.map(...)` 完成 Application View → HTTP Response 的记录转换，同时保留分页元数据。

Mapper 继续使用：

```sql
LIMIT #{limit} OFFSET #{offset}
```

这是数据库实现细节，不再泄露为 `/users?limit=&offset=` 形式的公共分页协议。

## 6. 用户名密码认证

认证职责现在拆为：

```text
AuthenticationApplication
        ↓
AuthenticationManager
        ↓
ProviderManager
        ↓
DaoAuthenticationProvider
        ├── AuthUserDetailsService
        └── PasswordEncoder
        ↓
AuthUserPrincipal
```

`AuthUserDetailsService`：加载 User + Role/Permission authorities。

`DaoAuthenticationProvider`：使用 Spring Security 原生能力完成密码验证和 enabled 状态检查。

`AuthUserPrincipal`：

```text
userId          稳定 MOM 身份
username        登录名称
password        仅认证阶段使用，成功后由 ProviderManager 清理
Authorities     ROLE_* + permission.code
```

`AuthenticationApplication` 不再直接注入 `UserMapper`、`AuthenticationQueryMapper`、`PasswordEncoder` 做手工密码认证，只接收认证完成的 Principal 并签发 Opaque Token。

## 7. 密码与 Token

密码继续使用：

```text
DelegatingPasswordEncoder
└── BCrypt strength 12
```

编码格式：`{bcrypt}$2...`。

认证成功后 MOM 自己负责：

```text
SecureRandom 32 bytes
→ Base64URL raw token
→ MomTokenPrincipal(userId, authorities, expiresAt)
→ MomTokenStore
→ Redis
```

因此“使用 Spring Security 原生认证组件”不等于改用 Session/JWT，也不改变已有 Redis Opaque Token 架构。

## 8. HTTP 返回边界

Controller 统一：

```text
Result<T>
├── code
├── message
└── data
```

规则：

1. Controller 负责 Result 包装；
2. Application 返回 View/PageResult/业务结果，不返回 Result；
3. 创建接口仍可返回 HTTP 201；认证失败仍 401、权限/账号状态等继续使用真实 HTTP 状态；
4. 删除/Logout 返回 `Result<Void>`，不再用 204 空响应破坏统一信封；
5. Bean Validation 与业务异常也转换为 Result；
6. V1 只使用默认消息，`messageKey` 继续仅作为国际化预留。

## 9. RBAC 与关系完整性

运行时 authority：

```text
Role PLATFORM_ADMIN → ROLE_PLATFORM_ADMIN
Permission auth:user:read → auth:user:read
```

User/Role/Permission 删除前检查关系引用，不做隐藏级联；关系整体替换在本地事务中完成。

## 10. 注释规范

Mini Auth 不要求逐行注释。必须注释的是“删除代码后无法从语法直接恢复的设计意图”：

- 类在分层中的职责边界；
- 为什么 UserEntity 与 UserDetails 分离；
- Spring Security 与 MOM Opaque Token 的责任边界；
- V1 为什么账号锁定/过期/密码过期固定返回 true；
- 安全相关行为（凭据清理、Token 存储等）；
- 未来维护者容易误判为遗漏的 V1 限制。

禁止 `// 查询用户`、`// 判断为空` 这类只翻译代码的注释。

## 11. 2026-09-05 迭代更新

本轮从“能跑的 Mini Auth”继续向“可解释、可掌控”收敛：

| 项目 | 旧实现 | 当前规范 |
|---|---|---|
| HTTP 返回 | Response/ProblemDetail/204 混用 | Controller 统一 `Result<T>` + 真实 HTTP status |
| 分页 | `PageView` + `OffsetPageResponse` + limit/offset | `mom-core.PageResult<T>` + pageNo/pageSize |
| 登录认证 | Application 手工查用户、matches、enabled | Spring Security `AuthenticationManager/DaoAuthenticationProvider/UserDetailsService` |
| Principal | 无独立登录 Principal | `AuthUserPrincipal`，与 `UserEntity` 分离 |
| 注释 | 关键边界说明不足 | 只补设计原因、安全语义和 V1 边界 |

明确未做：i18n 运行时转换、JWT、Refresh Token、Session、OAuth Client、SSO/OIDC、额外 Domain/Port/Adapter。

## 12. 测试状态说明

认证单元测试已同步为 `AuthenticationManager` 委托模型，并覆盖：

- username 规范化后交给 Spring Security；
- 成功认证后签发 256-bit Opaque Token；
- BadCredentials → 稳定认证失败错误；
- Disabled → 账号停用错误；
- Authentication Infrastructure 故障 Fail Closed；
- TokenStore 故障 Fail Closed；
- Logout 删除当前 Token。

当前执行环境无法解析 `github.com`，因此本次修改不能在本地执行 Maven；测试通过与否必须以后续开发机/CI 的真实执行结果为准。
