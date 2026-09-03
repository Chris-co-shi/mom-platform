# ADR-041：Mini Auth 简化三层包结构

- 状态：Accepted
- 提出日期：2026-09-03
- 接受日期：2026-09-03
- 决策人：Chris
- 适用范围：`mom-auth-platform/mom-auth-server`
- 关联决策：ADR-040、ADR-026
- 精确替代范围：ADR-027、ADR-028 在 `mom-auth-server` V1 内的强制分层与 Repository Port 要求

## 1. 背景

`mom-platform` 早期为了强化六边形架构和依赖倒置，逐步形成 `Web → Application → Domain Port ← Infrastructure Adapter`、按业务 Feature 分包、Domain Repository Port、MyBatis Repository Adapter 等结构。

这些规则适合复杂 bounded context，但 Mini Auth V1 当前只有 User、Role、Permission、登录、Opaque Token 签发与 Logout 等有限能力。继续机械复制 `web/application/domain/infrastructure`、Repository Port、Adapter 与多层 DTO/Converter，会显著增加第一版代码量和理解成本，不利于当前“恢复掌控、先跑通业务”的目标。

因此 Mini Auth V1 单独采用更直接的三层结构。

## 2. 决策

`mom-auth-server` 顶层业务包固定为：

```text
io.github.chrisshi.mom.auth
├── AuthApplication
├── controller
├── service
└── infrastructure
```

### `controller`

负责 HTTP API 边界：请求接收/校验、调用 Service、返回 API DTO；不直接访问 Mapper、Entity、Redis 或密码编码实现。

### `service`

负责业务聚合与用例编排：User / Role / Permission 管理、关系编排、登录、密码校验、权限聚合、Token 签发、Logout、本地事务和引用完整性。

`service` 就是 Mini Auth V1 的业务层，不再额外创建 `application` 或 `domain` 顶层包。

### `infrastructure`

负责技术和持久化实现，第一版重点是 MyBatis-Plus/PostgreSQL：

```text
infrastructure
├── entity
├── mapper
└── 仅在真实需要时增加 repository / query / configuration 等技术子包
```

Entity、Mapper、Wrapper、`IPage` 等 ORM 类型不得直接成为 Controller DTO。

## 3. 依赖方向

Mini Auth V1 默认依赖方向：

```text
controller
    ↓
service
    ↓
infrastructure
```

这是一种有意的简化三层架构，不宣称满足严格 Clean Architecture 或 Hexagonal Architecture。

`service` 可以直接依赖本模块 Infrastructure 的持久化组件。当前不为了“形式上的依赖倒置”强制增加：

- Domain Repository Port；
- Application Service 接口；
- Infrastructure Adapter 接口；
- 一表一 Converter；
- 只做转发的 Repository 包装器。

出现真实可替换实现、ORM 明显污染 Service、Infrastructure 复杂度显著增长或测试边界确实需要时，再引入接口或额外抽象。

## 4. MyBatis-Plus 使用边界

第一版允许 Infrastructure 直接使用 MyBatis-Plus：

- Entity 复用平台 `BaseEntity` 等基础类型；
- Mapper 使用平台 MyBatis-Plus 基线；
- 普通单表 CRUD 优先使用框架现有能力；
- 不使用 `IService` / `ServiceImpl` 充当业务 Service；
- 不创建无业务价值的通用 `BaseRepository` / `CommonRepository`；
- 多表关系、引用校验和事务由 `service` 明确编排。

ADR-026 继续完全生效：Auth 自主业务表和关系表不建立物理外键，引用完整性由 Service、本地事务、Unique/Check、索引和测试共同保证。

## 5. 业务组织方式

第一版不按 User/Role/Permission 在顶层重复三层目录，而是：

```text
controller
├── UserController
├── RoleController
└── PermissionController

service
├── UserService
├── RoleService
├── PermissionService
└── AuthenticationService

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

仅在文件数量和职责确实增长后，再评估更细技术子包。

## 6. 后果

正面结果：第一版目录和调用方向直观，减少无收益接口与转换，更容易逐步理解 User/Role/Permission CRUD 和登录链。

代价：Service 对具体 Infrastructure 的编译期依赖更强，持久化替换不是零成本；如果 Auth 复杂度未来显著增长，需要重新引入更严格边界。当前明确接受这些代价。

## 7. 与旧 ADR 的关系

ADR-027、ADR-028 对其他正式 bounded context 继续保持原状态。

仅在 `mom-auth-platform/mom-auth-server` 的 Mini Auth V1 范围内，以本 ADR 为准：

```text
ADR-027 / ADR-028 通用复杂分层
            ↓
ADR-041 Mini Auth 精确简化例外
```

该例外不得自动扩散到 MES、WMS、QMS、System 等其他服务。

## 8. 验证

- `mom-auth-server` 顶层业务包只出现 `controller`、`service`、`infrastructure`；
- Controller 不直接依赖 Mapper/Entity；
- Infrastructure 不依赖 Controller；
- 不引入 MyBatis-Plus `IService/ServiceImpl` 作为业务 Service；
- 不创建仅为形式满足 DIP 的空接口或一对一代理；
- Auth V1 的业务事务和关系完整性能够从 Service 代码直接追踪。
