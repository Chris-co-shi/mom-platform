# Mini Auth 数据库与代码分层

- 状态：Current
- 生效日期：2026-09-03
- 当前事实来源：`fix/mini-auth`
- 模块：`mom-auth-platform/mom-auth-server`
- 认证决策：[ADR-040](../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- 包结构决策：[ADR-041](../adr/ADR-041-Mini-Auth简化三层包结构.md)
- 数据关联决策：[ADR-026](../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 当前目标

Mini Auth V1 只建设最小认证授权闭环：

```text
User → Role → Permission → Login → Opaque Token → Resource Server
```

当前不恢复旧 IAM 的 Employee/Party/Factory Scope、OAuth Client、Session、Refresh Token、OIDC、JWT 或安全审计平台。

## 2. PostgreSQL 与 Flyway

Auth 复用 `mom_platform` PostgreSQL Database，使用独立 Schema：

```text
mom_auth
```

当前运行基线：

```text
spring.application.name = mom-auth-server
DataSource Schema         = mom_auth
Flyway Schema             = mom_auth
Flyway Location           = classpath:db/migration/auth
Java ID                   = String
PostgreSQL ID             = varchar(19)
Java 时间                 = Instant
PostgreSQL 时间           = timestamptz
```

当前正式 Migration：

```text
V1__create_auth_core_tables.sql
V2__seed_platform_admin.sql
```

## 3. V1 核心表

| 表 | 作用 |
|---|---|
| `auth_user` | 登录账号与密码摘要 |
| `auth_role` | 角色目录 |
| `auth_permission` | Permission 目录 |
| `auth_user_role` | 用户角色关系 |
| `auth_role_permission` | 角色权限关系 |

Token 不进入 PostgreSQL，由 `mom-security` 的 `MomTokenStore` 保存到 Redis。

## 4. User

`auth_user` 第一版业务字段：

```text
username
password_hash
display_name
enabled
```

并复用平台普通可更新实体审计字段：

```text
id
created_at
created_by
updated_at
updated_by
version
deleted
```

边界：

- User 表示系统登录账号，不等同于 Employee；
- 不在 User 中保存 Role/Permission 集合；
- 不保存 Session、Token、Refresh Token；
- 不保存组织、工厂、供应商或客户主体；
- `username` 全局唯一，Service 负责规范化；
- `password_hash` 禁止进入 API、日志和 Trace。

## 5. Role 与 Permission

`auth_role`：`code / name / description / enabled`。

`auth_permission`：`code / name / description / enabled`。

Permission Code 推荐使用稳定业务语义，例如：

```text
mes:work-order:create
wms:inventory:adjust
auth:user:read
```

运行时 Authority 可同时包含：

```text
ROLE_PLATFORM_ADMIN
mes:work-order:create
```

## 6. 关系表

```text
auth_user_role
├── user_id
└── role_id

auth_role_permission
├── role_id
└── permission_id
```

按照 ADR-026：不建立物理外键；保留组合唯一约束和查询索引；引用存在性、删除保护和多表事务由 `service` 层负责。

## 7. 初始管理员

`V2__seed_platform_admin.sql` 当前初始化：

```text
username = admin
role     = PLATFORM_ADMIN
admin → PLATFORM_ADMIN
```

不存在“角色名即超级权限”的代码绕过；Permission 仍应显式写入 `auth_permission` 与 `auth_role_permission`。

该初始化只作为 local/dev 第一版基线，正式部署前必须重新评估公共默认凭据风险。

## 8. 代码分层

```text
io.github.chrisshi.mom.auth
├── AuthApplication
├── controller
├── service
└── infrastructure
```

Controller 负责 HTTP；Service 负责业务聚合、事务、引用校验、登录和 Token 编排；Infrastructure 负责 MyBatis-Plus/PostgreSQL 技术实现。

初始持久化结构：

```text
infrastructure
├── entity
│   ├── UserEntity
│   ├── RoleEntity
│   ├── PermissionEntity
│   ├── UserRoleEntity
│   └── RolePermissionEntity
└── mapper
    ├── UserMapper
    ├── RoleMapper
    ├── PermissionMapper
    ├── UserRoleMapper
    └── RolePermissionMapper
```

Repository、Query、Converter 等只有真实需要时才增加。

## 9. 依赖方向

```text
Controller → Service → Infrastructure
```

不默认增加 Domain Port、Application Interface、Repository Port、一表一 Adapter 或一表一 Converter。

同时禁止：Controller 直接调用 Mapper；Entity 直接作为外部 API DTO；MyBatis-Plus `IService/ServiceImpl` 充当业务 Service；跨服务共享 Mapper/Entity。

## 10. Token 与数据库边界

```text
数据库加载 User / Role / Permission
        ↓
Service 聚合 authorities
        ↓
生成 Opaque Token
        ↓
MomTokenStore.store(token, principal)
        ↓
Redis
```

PostgreSQL 不保存 Access Token；Redis TokenStore 也不成为 User/Role/Permission 的权威数据源。

## 11. 当前实现顺序

```text
1. UserEntity / UserMapper
2. User Service 与基础用户管理
3. Role / Permission 持久化
4. User-Role / Role-Permission 关系编排
5. Login 与 PasswordEncoder
6. authorities 聚合
7. Opaque Token 签发
8. Logout
9. Gateway → Auth/业务服务联调
10. 服务间 Token 传播联调
```
