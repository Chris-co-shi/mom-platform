# ADR-041：Mini Auth 简化三层包结构

- 状态：Superseded by ADR-042
- 提出日期：2026-09-03
- 接受日期：2026-09-03
- 决策人：Chris
- 适用范围：`mom-auth-platform/mom-auth-server`
- 关联决策：ADR-040、ADR-026
- 后续决策：[ADR-042：MOM 渐进式分层与对象模型](ADR-042-MOM渐进式分层与对象模型.md)
- 精确替代范围：ADR-027、ADR-028 在 `mom-auth-server` V1 内的强制分层与 Repository Port 要求

> 本 ADR 保留 2026-09-03 从旧 IAM 复杂分层收敛到简化三层的历史决策。
> 2026-09-04 起，ADR-042 将“简化三层”提升为 MOM 项目级渐进式架构原则，并将 Mini Auth 的业务层包名从 `service` 收敛为语义更明确的 `application`。
> Mini Auth 当前有效结构请以 ADR-042 与 `docs/engineering/standards/package-directory-architecture-standard.md` 为准。

## 1. 背景

`mom-platform` 早期为了强化六边形架构和依赖倒置，逐步形成 `Web → Application → Domain Port ← Infrastructure Adapter`、按业务 Feature 分包、Domain Repository Port、MyBatis Repository Adapter 等结构。

这些规则适合复杂 bounded context，但 Mini Auth V1 当前只有 User、Role、Permission、登录、Opaque Token 签发与 Logout 等有限能力。继续机械复制 `web/application/domain/infrastructure`、Repository Port、Adapter 与多层 DTO/Converter，会显著增加第一版代码量和理解成本，不利于当前“恢复掌控、先跑通业务”的目标。

因此 Mini Auth V1 单独采用更直接的三层结构。

## 2. 原决策

`mom-auth-server` 当时冻结的顶层业务包为：

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

## 3. 原依赖方向

Mini Auth V1 当时默认依赖方向：

```text
controller
    ↓
service
    ↓
infrastructure
```

这是一种有意的简化三层架构，不宣称满足严格 Clean Architecture 或 Hexagonal Architecture。

`service` 可以直接依赖本模块 Infrastructure 的持久化组件。当时明确不为了“形式上的依赖倒置”强制增加：

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
- 多表关系、引用校验和事务由业务层明确编排。

ADR-026 继续完全生效：Auth 自主业务表和关系表不建立物理外键，引用完整性由业务层、本地事务、Unique/Check、索引和测试共同保证。

## 5. 原业务组织方式

第一版当时不按 User/Role/Permission 在顶层重复三层目录，而是：

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

代价：业务层对具体 Infrastructure 的编译期依赖更强，持久化替换不是零成本；如果 Auth 复杂度未来显著增长，需要重新引入更严格边界。当前明确接受这些代价。

## 7. 后续演进

ADR-042 保留本 ADR 的核心取舍：

- 简单业务不预付完整 Clean/Hexagonal 架构成本；
- 业务层允许直接使用本服务 Infrastructure；
- 不创建无业务价值的 Repository Port/Adapter、Converter 和空接口；
- 复杂度真实出现后再引入 Domain/Port/Adapter。

同时 ADR-042 将业务层统一命名为：

```text
application
```

以避免 `service` 与 Spring `@Service`、MyBatis-Plus `IService`、Domain Service、Remote Service 等多重语义混淆。

## 8. 当前有效 Mini Auth 结构

当前结构不再使用本 ADR 的 `service` 包，而使用 ADR-042：

```text
controller → application → infrastructure
```

具体 Package、对象模型和升级条件以 ADR-042 及当前工程规范为准。