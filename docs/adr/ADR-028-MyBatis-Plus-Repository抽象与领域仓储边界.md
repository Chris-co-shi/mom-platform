# ADR-028：MyBatis-Plus Repository 抽象与领域仓储边界

- 状态：Accepted
- 日期：2026-07-31
- 决策人：Chris
- 适用范围：`mom-*-platform/mom-*-server` 正式 bounded context
- 关联决策：ADR-020、ADR-026、ADR-027

## 1. 背景

MOM 已冻结 `Web → Application → Domain Port ← Infrastructure Adapter` 的依赖方向，并禁止 Mapper、Entity、Wrapper、`IPage` 等 MyBatis-Plus 类型进入 Domain、Application 和 Web。

此前项目为防止 ORM 抽象泄漏，进一步全面禁止 `IService`、`ServiceImpl` 以及 Repository 抽象复用。该规则保护了领域边界，但也使部分单表 Repository Adapter 只能重复组合 Mapper，产生 `selectById`、`selectOne`、`selectList`、`selectCount`、`insert`、`updateById` 等模板代码。

MyBatis-Plus 3.5.9 之后将通用 Service 抽象重构为 Repository 语义。当前项目使用 3.5.17，正式单表 Adapter 可以在 Infrastructure 内部复用 `CrudRepository<M, E>`，同时继续向上实现框架无关的 MOM Domain Repository Port。

## 2. 决策

### 2.1 领域契约保持框架无关

Domain Repository Port：

- 只能表达领域或用例需要的持久化语义；
- 不得继承 `IRepository`、`IService` 或其他 ORM 接口；
- 不得暴露 Entity、Mapper、Wrapper、`IPage`、affected rows 或数据库异常；
- Application 和 Web 只能依赖 Domain Port，不得依赖具体 MyBatis Adapter。

### 2.2 单表 CRUD Adapter 优先复用 CrudRepository

同时满足以下条件时，Infrastructure Repository Adapter 应优先：

```java
@Repository
public class MybatisExampleRepository
        extends CrudRepository<ExampleMapper, ExampleEntity>
        implements ExampleRepository {
}
```

适用条件：

1. 一个主要 Mapper 对应一个主要 Entity；
2. 普通单表 CRUD、等值/范围过滤、计数、固定排序或分页是主要持久化路径；
3. Adapter 实现框架无关的 Domain Repository Port；
4. 事务、授权、领域校验和用例编排仍位于 Application；
5. 唯一冲突、乐观锁冲突和数据库异常仍转换为稳定 MOM 语义。

Adapter 可使用 `getById`、`getOne`、`list`、`count`、`save`、`updateById` 和 `getBaseMapper`。`getBaseMapper` 仅用于固定、受审查的 Mapper 扩展能力，不得重新形成第二套 CRUD 封装。

### 2.3 IService 与 ServiceImpl 继续禁止

正式 bounded context 不使用：

- `IService<T>` 作为业务 Service 或 Repository；
- `ServiceImpl<M, T>` 作为 Application Service；
- 自定义接口继承 `IRepository<T>` 后向 Application/Web 暴露通用 CRUD；
- `Db`、Active Record 或静态 ORM Helper 绕过 Domain Port。

原因是这些接口会把通用 CRUD、Persistence Entity 和 ORM 语义提升为业务契约。

### 2.4 不强制继承的类型

以下类型不得为了形式统一强行继承单一 `CrudRepository`：

- 多 Mapper 聚合 Repository；
- 多 Entity、本地聚合或关系替换 Adapter；
- Query Repository、Projection Repository 和 Read Model；
- 协议表、OAuth/SAS Store 和基础设施状态存储；
- 主要依赖 CTE、JOIN、窗口函数、批量 Upsert、`SKIP LOCKED` 等专用 SQL 的 Adapter；
- 一个 Domain Repository 同时管理多张生命周期不同的表。

这些类型可以直接组合 Mapper；当拆分能显著减少重复且不破坏事务/聚合边界时，也可以组合多个 Infrastructure 内部 `CrudRepository` 表级组件。拆分不是强制目标。

### 2.5 继承能力不得越层泄漏

`CrudRepository` 的公共通用方法只属于 Adapter 实现能力：

- Domain Port 不声明这些方法；
- Application/Web 不按具体 Adapter、`IRepository` 或 `CrudRepository` 注入；
- Infrastructure Repository 包之外不得依赖 `com.baomidou.mybatisplus.extension.repository`；
- 具体 Adapter 仍按能力命名为 `Mybatis*Repository`，不得命名为业务 `*Service`。

## 3. 分类与验收

| 分类 | 默认实现 |
|---|---|
| 单表 Domain Port Adapter | `CrudRepository + Domain Repository Port` |
| 多 Mapper 聚合 Adapter | 组合 Mapper，必要时组合内部表级 Repository |
| Query/Projection Repository | QueryMapper/专用查询实现 |
| 协议或基础设施存储 | 精确协议实现与 ADR/例外 |
| Framework 内部 Repository | 按 Framework 规范，不套业务 Domain Port |

验收必须证明：

- Domain/Application/Web 无 MyBatis-Plus Repository/Service 依赖；
- 单表候选已经复用 `CrudRepository` 或有精确、不扩散的理由；
- HTTP、事务、SQL、锁、Version、逻辑删除和异常语义保持；
- 没有为了继承而合并不同聚合、不同表生命周期或 Query 模型。

## 4. 当前迁移裁决

- `MybatisSystemParameterRepository`：单表 Domain Port Adapter，迁移；
- `MybatisSystemDictionaryRepository`：单表 Domain Port Adapter，迁移；
- `MybatisSystemDictionaryItemRepository`：单表 Domain Port Adapter，迁移；
- `MybatisSystemI18nRepository`：三 Mapper、多 Entity、发布历史聚合，保留组合；
- `MybatisSystemUserPreferenceRepository`：两 Mapper、两表生命周期，保留组合；
- IAM Admin/Authorization Repository：协议投影、多 Mapper、关系替换或专用安全 SQL，不机械迁移；
- MDM、Integration 当前无正式业务 Domain Repository 候选。

## 5. 后果

正向结果：

- Domain/Hexagonal 边界保持不变；
- 单表 Adapter 减少 Mapper CRUD 模板代码；
- MyBatis-Plus Repository 语义只存在于 Infrastructure；
- 多表、协议和 Query 场景不被错误抽象统一。

代价与风险：

- 具体 Adapter 继承了宽 CRUD 能力，必须由门禁阻止上层按具体类型或 MP 接口依赖；
- MyBatis-Plus 升级需要验证 `CrudRepository` 注入、批处理和返回语义；
- 不能仅凭“一个 Mapper”判断适用性，仍需审查聚合、协议和专用 SQL 职责。
