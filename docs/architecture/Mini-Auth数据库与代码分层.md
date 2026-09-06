# Mini Auth 数据库与代码分层

- 状态：Current
- 当前事实来源：`fix/mini-auth`
- 最近更新：2026-09-05
- 模块：`mom-auth-platform/mom-auth-server`
- 模块专属约束：[`mom-auth-server/AGENTS.md`](../../mom-auth-platform/mom-auth-server/AGENTS.md)
- 全局 CRUD 规范：[crud-application-standard.md](../engineering/standards/crud-application-standard.md)
- 全局持久化规范：[persistence-data-modeling-standard.md](../engineering/standards/persistence-data-modeling-standard.md)
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

这里需要区分：**Mini Auth 当前没有引入 `CrudRepository`，并不代表 MOM 全项目禁止 `CrudRepository`。**当前 User/Role/Permission 的单表操作、分页和批量主键查询都能由 `MomBaseMapper` 清晰承担，还没有形成值得独立封装的可复用持久化职责，所以继续直接依赖 Mapper。

全项目的选择规则是：

```text
普通单表数据访问
Application → BaseMapper

出现真实可复用持久化职责
Application → XxxRepository extends CrudRepository → XxxMapper

进入 Level 2/3 Domain/Port
Application/Domain → Repository Port ← MyBatis Adapter
```

`CrudRepository` 是可选 Infrastructure 技术复用，不是 DDD Repository Port，也不是每张表的默认配套类。

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

当前约束：

- `UserEntity` 不实现 `UserDetails`；
- Spring Security Principal 使用独立 `AuthUserPrincipal`；
- `Result<T>` 是 HTTP 返回信封，位于 `mom-webmvc`，不得成为 Application 返回模型；
- 通用分页直接使用 `mom-core.PageResult<T>`，不得再增加只复制分页字段的 Auth 私有 View/Response；
- 简单单表 Entity → View 当前由 `XxxView.from(XxxEntity)` 集中映射；
- Entity 不得反向依赖 Application View。

对象转换是否升级 Converter/MapStruct 属于全项目工程规则，Auth 不单独冻结另一套策略。

## 5. 分页与批量数据访问

分页依赖方向固定为：

```text
HTTP pageNo/pageSize
        ↓
Application
        ↓
PageQuery
        ↓
PageAdapter.toPage(...)
        ↓
MyBatis-Plus BaseMapper.selectPage(...)
        ↓
IPage<Entity>
        ↓
PageAdapter.toResult(page, Entity → View)
        ↓
PageResult<View>
        ↓
Controller PageResult.map(View → Response)
```

当前 Auth 职责边界：

- `mom-core.PageResult<T>` 只表达框架无关分页结果；
- `mom-data.PageAdapter` 负责 MyBatis-Plus `IPage/Page` 与 `PageResult/PageQuery` 适配；
- Auth Application 不自行计算 `offset`、`totalPages`；
- User/Role/Permission 单表 Mapper 不维护 `countActive()` 或自定义 `LIMIT/OFFSET` 分页 SQL；
- User 按 `username,id` 稳定排序；Role/Permission 按 `code,id` 稳定排序；
- 批量主键查询使用 `selectByIds(...)`，不使用已废弃的 `selectBatchIds(...)`；
- 只有真实多表 JOIN/复杂读取才进入 `infrastructure.query`。

MyBatis-Plus 3.5.17 已经为 `BaseMapper` 提供批量查询、删除、插入、按 ID 更新和 insert-or-update 能力，所以以后 Auth 出现批量写需求时，应先评估 BaseMapper 原生 Batch API。只有批次切分、BatchResult 检查或其他数据访问策略形成复用时，才考虑具体 `CrudRepository`。

对外分页统一：

```text
Result<PageResult<UserResponse>>
Result<PageResult<RoleResponse>>
Result<PageResult<PermissionResponse>>
```

HTTP 参数仍为 `pageNo/pageSize`，默认 `1/50`，最大 `200`。

## 6. 用户名密码认证

认证职责固定为：

```text
AuthenticationApplication
        ↓ AuthenticationManager.authenticate
ProviderManager
        ↓
DaoAuthenticationProvider
        ├── AuthUserDetailsService
        │      ├── UserMapper
        │      └── AuthenticationQueryMapper
        └── PasswordEncoder
        ↓
AuthUserPrincipal
        ↓
Authentication
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

`AuthenticationApplication` 不直接注入 `UserMapper`、`AuthenticationQueryMapper`、`PasswordEncoder` 做手工密码认证，只调用 Spring Security `AuthenticationManager`，再基于认证成功的 Principal 签发 Opaque Token。

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
4. 删除/Logout 返回 `Result<Void>`；
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

公共类型和公共方法的中文 Javadoc 是**全项目规则**，权威来源是根 `AGENTS.md`，不属于 Auth 私有约定。

Mini Auth 额外要求注释这些安全语义：

- 为什么 `UserEntity` 与 `UserDetails` 分离；
- Spring Security 与 MOM Opaque Token 的责任边界；
- V1 为什么账号锁定/过期/密码过期固定返回 true；
- Token 随机强度、凭据清理和 Fail Closed；
- authority 快照和非实时刷新限制；
- Logout 只处理当前已认证 Token 的原因。

禁止 `// 查询用户`、`// 判断为空` 这类只翻译代码的注释。

## 11. 2026-09-05 迭代更新

本轮从“能跑的 Mini Auth”继续向“可解释、可掌控”收敛：

| 项目 | 旧实现 | 当前规范 |
|---|---|---|
| HTTP 返回 | Response/ProblemDetail/204 混用 | Controller 统一 `Result<T>` + 真实 HTTP status |
| 分页对象 | Auth 私有 PageView/OffsetPageResponse | 复用 `mom-core.PageResult<T>` |
| 分页执行 | Application 手工 count/offset/totalPages + Mapper 自定义分页 SQL | MyBatis-Plus `selectPage` + `mom-data.PageAdapter.toPage/toResult` |
| Entity/View 映射 | Application 分散私有 `toView()` | 当前简单映射集中 `XxxView.from(Entity)` |
| 批量主键查询 | `selectBatchIds(...)` | 使用 MyBatis-Plus `selectByIds(...)` |
| Repository 规则 | 容易被理解为“禁止 CrudRepository” | 全项目统一为 BaseMapper 默认、CrudRepository 按真实持久化职责引入 |
| 登录认证 | Application 手工查用户、matches、enabled | Spring Security `AuthenticationManager/DaoAuthenticationProvider/UserDetailsService` |
| Principal | 无独立登录 Principal | `AuthUserPrincipal`，与 `UserEntity` 分离 |
| 注释 | 关键边界说明不足 | 全项目公共类型/方法中文 Javadoc + Auth 安全语义补充 |
| 修改约束 | Auth 文档混入大量通用工程规则 | 通用规则上收根 AGENTS/engineering standards，Auth AGENTS 只保留认证专属约束 |

### 分页纠偏记录

初次收敛时虽然删除了 `PageView`，但仍在 Auth Application/Mapper 中重复实现了 `count + offset + totalPages + LIMIT/OFFSET`。该实现没有复用已经存在的 `mom-data.PageAdapter`，属于重复基础设施实现。

当前路径：

```text
PageQuery
→ PageAdapter.toPage
→ BaseMapper.selectPage
→ PageAdapter.toResult(page, mapper)
→ PageResult
```

后续业务模块不得重新手工计算同类分页元数据；如果 MyBatis-Plus 分页能力不足，再基于真实复杂查询场景扩展 QueryMapper，而不是复制一套单表分页。

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

当前执行环境若无法完成 Maven 依赖解析，则测试通过与否必须以后续开发机/CI 的真实执行结果为准。

## 13. 约束层次

后续维护按以下优先级理解：

```text
根 AGENTS.md
    ↓ 全项目执行约束
engineering/standards
    ↓ 全项目技术细则
Auth AGENTS.md
    ↓ 仅追加认证领域特有约束
Mini Auth 架构文档
    ↓ 解释当前实现与取舍
```

Auth 模块不得把全项目通用规则复制成自己的私有标准；如果 Mapper/CrudRepository/批量/分页等全局规则变化，应先修改全局规范，再同步本文件中的当前实现说明。