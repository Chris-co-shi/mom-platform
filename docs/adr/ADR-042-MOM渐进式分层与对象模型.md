# ADR-042：MOM 渐进式分层与对象模型

- 状态：Accepted
- 提出日期：2026-09-04
- 接受日期：2026-09-04
- 决策人：Chris
- 适用范围：`mom-platform` 所有 bounded context 的新增业务代码
- 关联决策：ADR-026、ADR-027、ADR-028、ADR-041
- 替代关系：替代 ADR-027/ADR-028 作为“所有 bounded context 默认强制结构”的解释；ADR-027/ADR-028 继续作为复杂 Domain/Port/Adapter 场景的有效架构选项；Mini Auth 的后续包结构以本 ADR 为准

## 1. 背景

MOM 早期为了强化依赖倒置、领域隔离和可测试性，将 `Web → Application → Domain Port ← Infrastructure Adapter`、Repository Port、Repository Adapter、Command/Query、Converter 等模式逐步提升为默认结构。

这些模式在复杂领域、多个外部适配器、强业务不变量或需要替换基础设施时有明确价值，但当它们被机械应用到简单 CRUD、主数据管理、基础认证等场景时，会产生大量只做转发或字段复制的类型，使“一个业务从入口到数据库”的调用链难以直接追踪。

MOM 当前优先目标是恢复代码可控性：每一个类必须能够解释其存在的业务或技术理由，而不是因为某种架构模式要求每层必须拥有对称对象和接口。

因此冻结“简单开始、按复杂度升级”的项目级原则。

## 2. 核心决策

### 2.1 默认采用简化三层

新增 bounded context 或新增简单业务能力默认从以下结构开始：

```text
controller / web
        ↓
application
        ↓
infrastructure
```

- `controller/web`：协议适配；
- `application`：业务用例、事务、授权、关系编排和业务校验；
- `infrastructure`：数据库、Redis、HTTP/Feign、消息、文件等具体技术实现。

Application 在该层级允许直接依赖本 bounded context 的 Mapper、Entity 或具体 Infrastructure 组件。项目不为了形式上的依赖倒置强制增加 Repository Port、Application Service 接口、Adapter 接口或一对一代理。

### 2.2 Domain 是按需升级层，不是目录占位符

仅当业务出现可独立表达且值得隔离测试的不变量时，引入 `domain`。典型信号包括：

- 明确且持续增长的状态机；
- 多个实体必须共同维护事务一致性；
- 复杂生命周期、资格判断或跨实体规则；
- Application 中出现大量重复的状态判断和业务决策；
- 同一业务规则需要被多个用例复用。

升级后可形成：

```text
controller / web
        ↓
application
        ↓
domain
        ↑
infrastructure
```

一旦建立 Domain，Domain 必须保持对 Web、MyBatis、JDBC、Feign、Redis 等具体技术实现无关。

### 2.3 Port / Adapter 只解决真实替换边界

只有出现以下情况之一时，再引入 Port/Adapter：

- 同一能力存在多个真实实现；
- 外部系统或基础设施需要被业务核心隔离；
- 单元测试确实需要替换不可控外部依赖；
- ORM/SDK 类型已经明显污染业务代码；
- 一个用例同时协调数据库、远程系统、消息等多个外部边界，且边界需要独立失败语义。

典型结构：

```text
application / domain
        ↓ Port
infrastructure adapter
```

Integration Hub、SAP/PCS/AGV/LIMS 等外部系统接入通常比普通 CRUD 更适合该模式。

## 3. 对象模型：3 + 1 规则

MOM 不再把 DTO、DO、PO、BO、VO、POJO 等缩写同时作为架构角色使用。

新增代码默认只使用以下对象语义：

| 类型 | 语义 | 示例 |
|---|---|---|
| Request / Response | HTTP 或稳定 API 契约 | `CreateUserRequest`、`UserResponse` |
| Entity | 数据库持久化行模型 | `UserEntity` |
| View | Application 产生的业务查询/展示结果 | `UserDetailView` |
| Row / Projection | 仅在复杂 SQL 结果与最终 View 明显不同、需要专用查询模型时出现 | `UserAuthorityRow` |

规则：

1. `POJO` 只是 Java 对象形态，不作为架构命名；
2. `DO` 存在 Data Object / Domain Object 歧义，新增代码禁止使用该后缀表达架构角色；
3. `PO`、`BO`、`VO` 不作为 MOM 默认命名；
4. `DTO` 可以作为概念描述，但类名优先使用具体语义 `Request`、`Response`、`View`，不机械追加 `DTO`；
5. 不是每个请求都必须同时出现四类对象，只创建真实边界需要的类型。

## 4. 不因“跨层”机械创建对象

层之间发生调用，不自动意味着必须创建新的 Command、Query、Domain Object、Converter 或 Assembler。

简单用例允许：

```text
CreateUserRequest
        ↓
UserApplication
        ↓
UserEntity / UserMapper
```

当入参复杂、同一用例有多个入口、需要与 HTTP 契约独立演进，或参数本身具有明确业务语义时，才增加 `CreateUserCommand` 等 Application 模型。

禁止为了形成：

```text
Request → Command → Domain → Entity → Domain → View → Response
```

而创建没有独立规则、只复制字段的类型。

## 5. 查询模型与 DDD Aggregate 的边界

“多表 JOIN 返回对象”默认不是 DDD Aggregate。

DDD Aggregate 表达的是一致性和事务边界；管理列表、详情组合、报表、搜索、权限展开等多表结果通常属于 Read Model / View / Projection。

MOM 查询默认分为三种路径：

### 5.1 简单单表查询

```text
Application
    ↓
Mapper
    ↓
Entity
    ↓
View / Response 所需结果
```

不为了查询形式额外创建 Repository、Query Service 或 Projection。

### 5.2 有界本地多表查询

当 MyBatis-Plus 单表 DSL 已不能清晰表达时：

```text
Application
    ↓
QueryMapper
    ↓
Row / Projection
    ↓
View
```

`QueryMapper`、`Row/Projection` 只在真实 JOIN、聚合统计、复杂分页或复用查询存在时创建。

### 5.3 领域聚合加载

只有写行为确实需要维护 Aggregate 不变量时，才通过 Repository 或有界查询加载 Domain Aggregate。不能因为 SQL JOIN 了 User、Role、Permission 就命名为 `UserAggregate`。

## 6. 架构升级而不是架构预付费

MOM 采用以下演进路径：

```text
Level 1：controller/web → application → infrastructure

Level 2：controller/web → application → domain + infrastructure

Level 3：controller/web → application/domain → port ← infrastructure adapter
```

升级必须由实际复杂度触发，不得仅以“以后可能需要”作为创建空接口、空包、Converter 或 Repository 包装器的理由。

已经采用 Level 2/3 且运行稳定的 IAM/System/MES/WMS 等代码，不要求为了本 ADR 做无业务收益的降级搬包；后续新增能力根据所在 bounded context 的既有边界和真实复杂度选择层级。

## 7. Mini Auth 裁决

`mom-auth-platform/mom-auth-server` 采用 Level 1：

```text
io.github.chrisshi.mom.auth
├── controller
├── application
└── infrastructure
    ├── entity
    ├── mapper
    └── query        # 仅复杂查询出现时创建
```

Application 类优先使用清晰的用例命名：

```text
UserApplication
RoleApplication
PermissionApplication
AuthenticationApplication
```

不使用 MyBatis-Plus `IService/ServiceImpl` 充当业务层，也不默认创建 Domain、Repository Port、Repository Adapter、一表一 Converter。

## 8. 验收规则

新增或评审一个类型时，必须能够回答：

1. 它解决了哪个真实业务或技术边界；
2. 删除它并直接使用相邻对象会损失什么语义、隔离或可测试性；
3. 如果它只是字段复制或方法转发，为什么不能省略；
4. 多表结果究竟是业务一致性 Aggregate，还是只读 View/Projection；
5. 引入 Domain/Port/Adapter 的触发条件是否已经真实出现。

不能回答以上问题的抽象默认不进入第一版。

## 9. 后果

正向结果：

- 简单业务调用链更短、更容易理解和调试；
- 对象类型和命名统一，降低 DTO/DO/PO/BO/VO 混用；
- DDD、Hexagonal、CQRS 思想继续保留，但按问题使用而不是按模板使用；
- 复杂制造领域仍可升级为强 Domain Model，外部集成仍可使用 Ports & Adapters。

代价：

- Level 1 Application 对具体 Infrastructure 的编译期耦合更强；
- 后续复杂度上升时可能需要提取 Domain 或 Port；
- 不同 bounded context 可以处于不同架构层级，Review 必须关注依赖语义而不是目录是否完全对称。

这些代价当前明确接受。