# MOM 服务端 Package 与目录架构规范

- 状态：Accepted
- 生效范围：正式 bounded context 的 `mom-*-server/src/main/java`
- 当前决策：[ADR-042](../../adr/ADR-042-MOM渐进式分层与对象模型.md)
- 历史复杂分层参考：[ADR-027](../../adr/ADR-027-服务端包结构与基础设施适配器分层.md)

## 1. 默认结构与依赖方向

MOM 新增简单业务默认采用 Level 1：

```text
<root-package>
├── controller
├── application
└── infrastructure
```

默认依赖方向：

```text
controller → application → infrastructure
```

`web` 是已有模块或明确采用 Web Adapter 命名时的等价入站包名，不要求为了统一名称批量搬迁已有代码。

不再要求每个 bounded context 默认创建 `domain`、`port`、`repository`、`adapter`、`converter`、`configuration` 等目录。只有真实职责出现时再创建。

## 2. Controller

Controller 层负责 HTTP 边界：

- 路由；
- 请求参数绑定；
- Bean Validation；
- 认证主体接入；
- 调用 Application；
- HTTP 状态和 Response 契约；
- 协议异常映射。

Controller 不得直接依赖 Mapper、Repository、数据库 Entity、Redis Template 或远程基础设施实现。

请求/响应对象在数量较少时可以直接位于 `controller`，数量增长后再创建：

```text
controller
├── request
└── response
```

禁止为了目录形式预先创建空包。

## 3. Application

Application 是业务用例层，负责：

- 业务用例与关系编排；
- 本地事务；
- 引用完整性；
- 业务授权；
- 幂等；
- 状态变更；
- 查询结果组合；
- 调用 Domain（存在时）或 Infrastructure。

简单 Level 1 中，Application 可以直接依赖本 bounded context 的 Mapper、Entity、QueryMapper 或其他 Infrastructure 组件。不要为了形式满足 DIP 创建一对一 Repository Port/Adapter。

类名优先使用业务能力 + `Application`：

```text
UserApplication
RoleApplication
PermissionApplication
AuthenticationApplication
```

不使用 MyBatis-Plus `IService/ServiceImpl` 代替业务 Application。

Application 内部只有在真实需要时才增加：

```text
application
└── model
    └── UserDetailView
```

`command`、`query`、`service` 等子包不是默认必建目录。

## 4. Domain：按需出现

只有出现明确领域不变量、状态机、复杂生命周期或跨实体业务规则时才创建 `domain`。

一旦创建：

- Domain 不依赖 Controller/Web；
- Domain 不依赖 Infrastructure；
- Domain 不依赖 MyBatis/JDBC/Feign/Redis；
- Domain 只表达领域语义。

不得为了“以后可能会复杂”创建空 Domain、贫血 Domain Wrapper 或只复制 Entity 字段的 Domain Object。

## 5. Infrastructure

Level 1 默认可以保持直接：

```text
infrastructure
├── entity
├── mapper
└── query          # 仅复杂查询出现时创建
```

当外部技术种类明显增长后，再按 Adapter 类型升级：

```text
infrastructure
├── persistence
│   ├── entity
│   ├── mapper
│   ├── query
│   └── repository   # 仅真实需要
├── client
├── messaging
├── cache
└── storage
```

Infrastructure 第一组织维度是技术职责。不得因为某个业务 Feature 同时需要 Entity、Mapper、Repository，就在 persistence 下复制一套 `persistence.<feature>.entity/mapper/repository` 烟囱。

## 6. Persistence 职责

### 6.1 Entity

数据库行模型使用 `*Entity`。

允许位置：

```text
infrastructure.entity
```

或复杂 Persistence 结构中的：

```text
infrastructure.persistence.entity
```

Entity 不得直接作为 HTTP/API Request/Response，也不得跨服务暴露。

### 6.2 Mapper

普通 MyBatis-Plus Mapper 使用 `*Mapper`。

允许位置：

```text
infrastructure.mapper
```

或：

```text
infrastructure.persistence.mapper
```

Mapper 只负责数据访问，不承载完整业务事务和业务流程。

### 6.3 Repository

Repository 不是 Mapper 的必选包装层。

只有出现以下情况才创建：

- 已有 Domain Repository Port；
- ORM 细节明显污染业务模型；
- 聚合加载/保存需要封装多个底层操作；
- 存在真实可替换持久化实现；
- 测试隔离收益已经明确。

禁止一表一个只转发 Mapper 的 Repository Adapter。

### 6.4 Query

只有复杂 JOIN、统计、组合分页、搜索或查询复用出现时创建 QueryMapper。

简单单表查询继续使用普通 Mapper，不创建占位 Query 包。

QueryMapper 的 SQL 原始结果可使用 `Row` / `Projection`，最终 Application 展示结果使用 `View`。多表 SQL 结果默认不是 DDD Aggregate。

### 6.5 Converter / TypeHandler

- Converter 只在转换规则复杂或多处复用时创建；
- 禁止一表一 Converter；
- 禁止万能 Bean Copy 抽象；
- PostgreSQL/JSONB 等通用 TypeHandler 保持在 `mom-framework/mom-data`。

## 7. 3 + 1 对象命名

新增业务对象默认只使用以下架构语义：

```text
Request / Response
Entity
View
Row / Projection    # 按需
```

不使用 `POJO` 作为架构角色；新增代码不默认使用 `DO`、`PO`、`BO`、`VO` 后缀。`DTO` 可以用于描述“传输对象”这个概念，但具体类型名称优先表达用途，例如 `CreateUserRequest` 而不是 `CreateUserRequestDTO`。

跨层调用不自动要求创建新类型。只有真实协议边界、持久化边界、查询模型或领域语义存在时才增加对象。

## 8. Configuration

只有真实服务级 Bean 装配、Properties 或条件配置出现时才创建 `configuration`。

简单 Infrastructure 自身配置可以先位于 `infrastructure.configuration`。不为顶层目录对称提前增加空 `configuration`。

## 9. Mapper XML

XML 默认保留：

```text
src/main/resources/mapper/<context>/
```

普通 MyBatis-Plus CRUD 不需要 XML 时，不创建 XML 占位文件。

## 10. 命名、移动和测试

文件路径必须与 `package` 完全一致，仓库不得存在重复 FQCN。移动必须同步：

- Java import/FQCN；
- Spring Bean/Import；
- `@MapperScan`；
- Component Scan；
- XML namespace；
- 反射/序列化字符串；
- 测试 Package；
- ArchUnit；
- 文档。

不得创建旧 Package 代理类长期保留双结构。

## 11. Mini Auth 当前结构

`mom-auth-platform/mom-auth-server` 明确采用：

```text
io.github.chrisshi.mom.auth
├── AuthApplication
├── controller
│   ├── UserController
│   ├── RoleController
│   ├── PermissionController
│   └── AuthenticationController
├── application
│   ├── UserApplication
│   ├── RoleApplication
│   ├── PermissionApplication
│   └── AuthenticationApplication
└── infrastructure
    ├── entity
    │   ├── UserEntity
    │   ├── RoleEntity
    │   ├── PermissionEntity
    │   ├── UserRoleEntity
    │   └── RolePermissionEntity
    ├── mapper
    │   ├── UserMapper
    │   ├── RoleMapper
    │   ├── PermissionMapper
    │   ├── UserRoleMapper
    │   └── RolePermissionMapper
    └── query          # 仅复杂查询出现时创建
```

验收：

- Controller 不直接依赖 Mapper/Entity；
- Application 可以直接编排 Mapper/Entity；
- 不使用 `IService/ServiceImpl` 作为业务层；
- 不创建只为形式满足 DIP 的接口或一对一代理；
- 多表查询只在真实需要时增加 QueryMapper/Row/View；
- User/Role/Permission CRUD 与登录链能够从 Application 清晰追踪。

## 12. 升级条件

出现以下情况时评估从 Level 1 升级，而不是继续在三层中无序堆叠：

- 明确且复杂的领域状态机/不变量；
- 多个可替换持久化或外部实现；
- Application 中大量 ORM/SDK 类型扩散；
- 数据库、远程服务、消息等多种 Infrastructure 同时参与一个能力；
- 单元测试难以隔离不可控外部依赖；
- 单一 Application 类持续膨胀且已形成独立聚合生命周期。

升级时只提取已经证明有价值的 Domain、Port、Repository 或 Adapter，不批量生成模板结构。