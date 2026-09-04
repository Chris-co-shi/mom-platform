# Mini Auth 数据库与代码分层

- 状态：Current
- 当前事实来源：`fix/mini-auth`
- 模块：`mom-auth-platform/mom-auth-server`
- 认证决策：[ADR-040](../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- 分层决策：[ADR-042](../adr/ADR-042-MOM渐进式分层与对象模型.md)
- 数据关联决策：[ADR-026](../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 当前目标与状态

Mini Auth V1 已实现最小认证授权闭环：

```text
User → Role → Permission → Login → Opaque Token → Resource Server → Logout
```

V1 不包含 Employee/Organization、OAuth Client、Session、Refresh Token、OIDC、JWT 或通用数据范围模型。

## 2. 数据库

Auth 使用 PostgreSQL 独立 Schema：

```text
mom_auth
```

核心表：

| 表 | 作用 |
|---|---|
| `auth_user` | 登录账号与密码摘要 |
| `auth_role` | 角色目录 |
| `auth_permission` | Permission 目录 |
| `auth_user_role` | 用户角色关系 |
| `auth_role_permission` | 角色权限关系 |

Flyway：

```text
V1__create_auth_core_tables.sql
V2__seed_platform_admin.sql
V3__seed_auth_management_permissions.sql
```

按 ADR-026 不建立物理外键；关系完整性由 Application、本地事务、唯一约束、索引和测试共同维护。

Token 不保存到 PostgreSQL，由 `mom-security.MomTokenStore` 保存到 Redis。

## 3. Entity

```text
UserEntity             extends BaseEntity
RoleEntity             extends BaseEntity
PermissionEntity       extends BaseEntity
UserRoleEntity         extends BaseCreatedEntity
RolePermissionEntity   extends BaseCreatedEntity
```

普通可更新实体具有：

```text
String id
createdAt / createdBy
updatedAt / updatedBy
version
deleted
```

关系表只需要 String ID 与创建审计，不机械继承乐观锁/逻辑删除。

## 4. 代码分层

Mini Auth 按 ADR-042 使用 Level 1：

```text
io.github.chrisshi.mom.auth
├── AuthApplication
├── controller
│   ├── request
│   └── response
├── application
│   └── model
└── infrastructure
    ├── configuration
    ├── entity
    ├── mapper
    └── query
```

依赖：

```text
controller → application → infrastructure
```

Controller 不直接访问 Mapper/Entity；Application 可以直接依赖本 bounded context Mapper/Entity/QueryMapper。

当前没有 Domain、Repository Port、Repository Adapter、Converter 或 MyBatis-Plus `IService/ServiceImpl`。

## 5. 对象模型

遵守 ADR-042 的 3+1 规则：

```text
Request / Response  HTTP 契约
Entity              数据库行模型
View                Application 输出
Row / Projection    仅复杂 SQL 确实需要时出现
```

当前 Login authority JOIN 直接返回 `List<String>`，因此没有为了形式创建只有一个字段的 Projection。

## 6. Mapper 与 QueryMapper

普通单表持久化：

```text
UserMapper
RoleMapper
PermissionMapper
UserRoleMapper
RolePermissionMapper
```

均使用 `MomBaseMapper<T>`。

复杂读取只有：

```text
AuthenticationQueryMapper
```

它通过 XML JOIN：

```text
auth_user_role
→ auth_role
→ auth_role_permission
→ auth_permission
```

输出已启用、未删除的：

```text
ROLE_<role.code>
permission.code
```

QueryMapper 不继承 `MomBaseMapper<?>`，因为多表查询不存在一个真实单表 Entity 泛型。

## 7. Application

```text
UserApplication
RoleApplication
PermissionApplication
AuthenticationApplication
```

`UserApplication`：

- username 创建时 `strip + lowercase(Locale.ROOT)`；
- 密码写入前 BCrypt；
- User CRUD；
- 管理员密码重置；
- User-Role 查询与整体替换；
- 删除前检查 UserRole 引用。

`RoleApplication`：Role CRUD、Role-Permission 查询/整体替换、删除引用保护。

`PermissionApplication`：Permission CRUD、删除引用保护。

`AuthenticationApplication`：密码认证、authority 聚合、Opaque Token 签发、TokenStore 写入与当前 Token Logout。

## 8. 密码

数据库只保存 `password_hash`。

V1 使用：

```text
DelegatingPasswordEncoder
└── bcrypt cost 12
```

编码格式：

```text
{bcrypt}$2...
```

密码不得进入 Response、日志、Trace 或审计事件，也不得自动 Trim/大小写转换。

## 9. 管理权限

平台管理员角色 `PLATFORM_ADMIN` 没有角色名后门。V3 显式授予：

```text
auth:user:read
auth:user:write
auth:role:read
auth:role:write
auth:permission:read
auth:permission:write
```

Controller 通过 `@PreAuthorize` 使用这些稳定 Permission Code。

## 10. 乐观锁、删除与关系

User/Role/Permission 更新要求客户端携带当前 `version`；版本不匹配或并发更新失败返回 409。

删除使用逻辑删除，但关系表采用物理删除。

为了避免隐藏级联：

- User 仍有 Role 时不能删除；
- Role 仍被 User 使用或仍有 Permission 时不能删除；
- Permission 仍被 Role 使用时不能删除。

关系整体替换在本地事务中执行，目标 ID 必须先通过存在性校验。

## 11. HTTP 路径与 Gateway

Gateway：

```text
/auth/** → StripPrefix=1 → mom-auth-server
```

所以外部：

```text
/auth/login
/auth/logout
/auth/users
/auth/roles
/auth/permissions
```

对应 Auth Server：

```text
/login
/logout
/users
/roles
/permissions
```

仅 `/login` 与联调 `/test` 是 public path；`/logout` 和管理 API 均由 Resource Server 认证。

## 12. 当前测试

当前新增单元测试覆盖认证核心行为：

- 有效凭据签发 256-bit Opaque Token；
- 未知账号/错误密码统一认证失败；
- disabled 用户拒绝登录；
- TokenStore 故障 Login Fail Closed；
- Logout 删除当前 Token；
- V2 管理员 `{bcrypt}` 种子密码与当前 PasswordEncoder 兼容。

由于当前执行环境无法解析 `github.com`，本轮无法在执行容器内实际运行 Maven；测试是否通过必须以开发机/CI 的真实 `mvn test` 结果为准。

## 13. 已知 V1 边界

已经签发的 Token 是登录时 authority 快照。User/Role/Permission 后续修改不会自动刷新已有 Token；当前只通过 Logout 或 TTL 到期失效。

若后续出现“停用用户或撤权必须立即使其所有 Token 失效”的明确需求，再增加用户级 Token 撤销机制，而不是在 V1 预建完整 Session 子系统。
