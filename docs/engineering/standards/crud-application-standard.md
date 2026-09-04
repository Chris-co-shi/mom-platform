# MOM CRUD 与应用服务规范

- 状态：Accepted
- 生效范围：所有正式 bounded context
- 当前架构决策：[ADR-042](../../adr/ADR-042-MOM渐进式分层与对象模型.md)
- 关联决策：[ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 默认分层职责

MOM 新增简单 CRUD 默认使用：

```text
Controller / Web
        ↓
Application
        ↓
Infrastructure Mapper / QueryMapper
```

职责：

- Controller/Web：HTTP 绑定、认证接入、Bean Validation、Request/Response、状态码；
- Application：业务授权、事务、幂等、引用校验、关系编排、状态变更和查询组合；
- Infrastructure：Entity、Mapper、SQL、Redis/Feign/消息等具体实现。

Level 1 中 Application **允许直接依赖本 bounded context 的 Mapper、Entity 和具体 Infrastructure**。项目不再为了形式上的依赖倒置强制增加 Domain Repository Port、Repository Adapter 或只做转发的 Application Service 接口。

Controller 仍禁止直接依赖 Mapper/Repository/Entity、声明业务事务、协调多个持久化组件或返回数据库 Entity。

当业务不变量、状态机、复杂生命周期或真实替换边界出现后，再按 ADR-042 升级到 Domain / Port / Adapter。

## 2. 生命周期用例

- Create：校验业务唯一性和引用，依赖数据库 Unique 最终兜底；客户端重试风险存在时定义幂等键和返回重放语义；
- Update：只接受允许变化的字段，涉及并发控制时携带 Version；affected rows 为 0 必须区分不存在与并发冲突；
- 状态迁移：复杂状态机使用显式业务动作和允许迁移矩阵，不能退化为任意状态字段更新；
- Disable：阻止新业务使用但保留历史，必须定义对既有引用和授权的影响；
- Delete：默认不是 Disable 的别名，执行引用保护、保留期和审计检查；
- Archive：只有真实历史生命周期需要时引入，保留可追踪、完整性验证和恢复/查询入口。

禁止使用通用 `BaseCrudController`、`BaseCrudService`、泛型 Repository、MyBatis-Plus `IService/ServiceImpl` 统一所有领域生命周期。

## 3. 3 + 1 对象模型

新增 CRUD 默认只使用以下对象语义：

| 类型 | 作用 | 例子 |
|---|---|---|
| Request / Response | HTTP 或稳定 API 契约 | `CreateUserRequest`、`UserResponse` |
| Entity | 数据库持久化行模型 | `UserEntity` |
| View | Application 业务查询/展示结果 | `UserDetailView` |
| Row / Projection | 复杂 SQL 原始结果，按需出现 | `UserAuthorityRow` |

规则：

1. `POJO` 不作为架构角色；
2. 新增代码不使用 `DO`、`PO`、`BO`、`VO` 作为默认分层后缀；
3. `DTO` 可以作为概念描述，但类名优先使用真实语义；
4. 不是每个用例都必须同时拥有四类对象；
5. 不因为“跨过一层”就机械创建 Command、Domain Object、Converter 或 Assembler。

简单创建用例允许：

```text
CreateUserRequest
      ↓
UserApplication
      ↓
UserEntity / UserMapper
```

当输入需要脱离 HTTP 复用、字段复杂、存在多个入口或拥有明确业务语义时，才增加 `CreateUserCommand`。

## 4. Entity 使用边界

Entity 属于 Infrastructure 持久化模型，允许 Application 在 Level 1 内部读取、构造和更新，但：

- Controller 不直接接收/返回 Entity；
- `*-api` 不暴露 Entity；
- Entity 不跨服务；
- 密码摘要、逻辑删除位、审计内部字段等不得进入公开响应；
- MyBatis `Page`、Wrapper 等 ORM 类型不得进入 HTTP/API 契约。

Application 对 Entity 的直接使用是当前 Level 1 有意接受的简化，不意味着 Entity 等同领域模型。业务复杂后可以再提取 Domain Model。

## 5. MyBatis-Plus 默认路径

普通单表 Insert、按主键读取、等值/范围过滤、计数、固定排序、分页、Update、逻辑删除优先使用：

- `MomBaseMapper<Entity>`；
- MyBatis-Plus Entity 映射与 `ASSIGN_ID`；
- 项目统一 `MetaObjectHandler`；
- `LambdaQueryWrapper` / `LambdaUpdateWrapper`；
- MyBatis-Plus `Page` 仅限 Infrastructure/Application 内部，不能进入 Web/API；
- 按能力选择 `BaseIdEntity`、`BaseAuditEntity`、`BaseEntity`。

MyBatis-Plus 能清晰表达的操作禁止新增 XML、注解 SQL、重复 Mapper 方法、JdbcTemplate/JdbcClient/NamedParameterJdbcTemplate/SimpleJdbcInsert、`java.sql` 或第二套 JDBC Repository。

Wrapper-only Update 可能跳过实体自动填充；涉及审计/version 时应传 Entity 或显式写全并检查行数，不得静默漏填。

自定义 SQL 仅用于 DSL 不适合的数据库特性或有界多表查询，必须参数化、显式列、说明必要性、处理审计/version、检查 affected rows，并有 PostgreSQL 证据。

## 6. 写入编排

多表写、关系维护和引用完整性由 Application 在明确本地事务中编排。例如 User 分配 Role：

```text
Controller
   ↓
UserApplication.assignRoles(...)
   ├── UserMapper：校验 User
   ├── RoleMapper：批量校验 Role
   └── UserRoleMapper：替换/新增关系
```

第一版不需要为了这段编排再增加：

```text
UserRepositoryPort
RoleRepositoryPort
UserRoleRepositoryPort
MybatisUserRepository
MybatisRoleRepository
MybatisUserRoleRepository
```

除非这些抽象已经有独立业务或技术价值。

## 7. 事务、幂等与并发

写事务默认位于 Application 公共方法，传播为 `REQUIRED`。

多表业务写、Outbox INSERT 必须按用例要求共享同一 DataSource 和事务；事务内禁止 RocketMQ、设备动作、人工等待和无界远程调用。

幂等键保持原始字节语义，不 Trim/改大小写/归一化；占位与结果有 TTL，Redis 故障策略明确，但数据库业务唯一约束和状态机仍是最终防线。

乐观锁通过 Entity + `@Version` 或明确条件更新完成；冲突返回稳定 409，不泄露 SQL/约束名。

所有写入必须检查 affected rows。批量操作定义条目数、单项/整体原子性、重复项语义、锁顺序、超时和失败结果；禁止无界批量和循环远程 N+1。

## 8. 查询

### 8.1 简单单表查询

直接使用普通 Mapper：

```text
Application → Mapper → Entity → View/结果
```

不创建占位 Query Service、QueryMapper、Projection 或 Repository。

### 8.2 复杂多表查询

当存在真实 JOIN、组合分页、统计、搜索或查询复用时：

```text
Application
    ↓
QueryMapper
    ↓
Row / Projection
    ↓
View
```

多表查询遵守 `multi-table-association-query-standard.md`。

多表组合结果默认是 Read Model / View，不因关联多张表就称为 DDD Aggregate。

### 8.3 Response 与 View

Application 优先返回具有业务语义的 View/Result；Controller 根据 HTTP 契约决定是否转换为独立 Response。

如果 View 与 HTTP 返回形状完全一致、没有基础设施信息且该接口不需要独立协议演进，可以直接作为返回模型，避免只做字段复制的一对一 Response；如果是公开稳定 API、跨模块契约、字段命名/兼容需要独立演进，则必须保留独立 Response/API DTO。

## 9. 分页和排序

分页请求必须有服务端最大 page size、稳定且唯一的次级排序；MyBatis `Page` 不进入公开 Web/API。

排序字段由服务端枚举映射为固定列，禁止客户端原始 SQL 表达式。大数据量场景评估 Keyset/Cursor，不在 Java 内存中加载全量后分页。

一对多分页遵守多表关联规范，禁止直接分页展开后的重复 JOIN 行再解释为主对象页。

## 10. Domain / Repository 升级条件

出现以下信号时，再评估提取 Domain 或 Repository Port：

- Application 出现大量重复状态判断或业务不变量；
- 多实体必须作为一致性边界共同变化；
- ORM Wrapper/Page/Entity 大量扩散到业务规则；
- 聚合保存需要封装多个持久化动作；
- 存在多个真实持久化实现；
- 单元测试确实需要替换不可控基础设施；
- 同一规则被多个用例重复使用。

升级只提取已经存在的复杂度，不提前建立空抽象。

## 11. 审计与错误模型

CurrentActor 与统一 MetaObjectHandler 填充审计；缺少可信 Actor 的业务写 fail closed。自定义 SQL 显式写审计字段。

公开错误稳定区分 400 校验、404 不存在、409 唯一/版本/状态冲突、503/504 依赖故障，禁止回显 SQL、连接信息、约束原文、Token 或 Secret。

## 12. 验收证据

每个 CRUD Slice 按真实能力覆盖：成功、Bean Validation、唯一冲突、引用不存在、乐观冲突、affected rows、删除/停用/归档保护、幂等重放、批量上限、事务回滚、分页与排序、审计填充、PostgreSQL 特性。

不是所有简单 CRUD 都必须机械制造每一种异常场景；测试范围应与实际生命周期、并发和协议承诺匹配。规范文件存在不等于验收完成，最终实现必须真实采用声明的技术路径。