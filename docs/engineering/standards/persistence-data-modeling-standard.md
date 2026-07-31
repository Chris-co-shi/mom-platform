# MOM 持久化与数据建模规范

## 1. 适用范围与事实层次

本规范冻结 MOM 新增和实质修改数据访问代码的工程边界。当前事实基线是 PostgreSQL 17、Flyway
12.4.0、MyBatis-Plus 3.5.17、Spring Framework 7 / Spring Boot 4.1 与 Spring Authorization Server
7.1.0；框架行为以官方资料为事实来源，命名、失败策略和分层是 MOM 项目决策。

历史实现差异见 [S02 历史例外清单](../P1.6-S02-持久化历史例外清单.md)。历史差异只用于识别债务，
不得复制为新代码模式。MyBatis-Plus Repository 与领域仓储边界以
[ADR-028](../../adr/ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md) 为最高决策来源。

## 2. 数据所有权与物理命名

- V1 使用共享 PostgreSQL 数据库 `mom_platform`，每服务使用独立 `mom_<bounded-context>` Schema。
- 服务账号只拥有自身 Schema 所需权限；默认一个实例只有一个权威 DataSource 和 HikariCP。
- 业务表、Outbox 和 Inbox 共用本服务 DataSource 与事务管理器；数据源不可用时写入 fail closed。
- 禁止跨 Schema JOIN、外键、读写、共享 Mapper，禁止用 View 或只读账号静默绕过边界。
- 跨域查询使用 Query API、本地事件投影或明确查询服务；多数据源必须由 ADR 明确授权。
- 部署必须设置受控 `currentSchema/search_path`，不能依赖公共可写 Schema。

## 3. 表、列、约束与类型

### 3.1 命名

- 标识符使用小写 `snake_case`，不以双引号制造大小写敏感名称。
- MOM 自有普通业务表统一包含 `id`、`created_at/by`、`updated_at/by`、`version`、`deleted`。
- 领域状态时间使用 `activated_at`、`revoked_at`、`occurred_at` 等明确名称，不能以 `updated_at` 代替。
- 新表遵循所属 bounded context 已冻结的本地前缀惯例。

### 3.2 主键、业务键和约束

- Java 技术主键是 `String`，PostgreSQL 是 `varchar(19)`，MyBatis-Plus 使用 `ASSIGN_ID`。
- 技术主键与可变业务编码分离，不以业务编码作为跨服务技术引用。
- 业务唯一性最终由数据库 Unique Constraint/Unique Index 保护；Application 预检查只改善错误信息。
- 逻辑删除后是否允许复用业务键必须显式决定；未批准复用时保留永久唯一约束。
- MOM 自主业务表、关系表、流水表、快照表和平台表全面禁止物理 FK 与物理级联，同一 bounded context、
  Schema 或聚合也不例外；完整性由 Application 校验、受控删除、Unique/Check、索引、事务、测试和对账共同保证。
- 稳定、简单且适合数据库兜底的不变量应评估 Check Constraint。

### 3.3 Java/PostgreSQL 类型

| 语义 | Java | PostgreSQL | 约束 |
|---|---|---|---|
| 全球时间点 | `Instant` | `timestamptz` | 连接会话 UTC；不以 `LocalDateTime` 表示 |
| 业务日期 | `LocalDate` | `date` | 时区/工厂日界线另行定义 |
| 金额、数量、比例 | `BigDecimal` | `numeric(p,s)` | 精度和 scale 由领域范围确定 |
| 布尔 | `boolean/Boolean` | `boolean` | true/false 语义明确 |
| 枚举 | 稳定字符串代码 | 字符列 | 默认不使用 PostgreSQL Native Enum |

JSONB 只用于真正半结构化且检索需求明确的数据，不能替代核心列、状态机或主数据建模。JSONB 的 JDBC
类型适配统一放在 `mom-data` TypeHandler，不允许各业务模块重复编写 JDBC/PGobject 代码。

## 4. Entity 统一规范

### 4.1 默认规则

MOM 关系型 Entity 必须按表能力选择最小充分基类：仅需技术主键使用 `BaseIdEntity`；需要创建和修改
审计使用 `BaseAuditEntity`；同时需要乐观锁与逻辑删除的普通可变业务表使用 `BaseEntity`。业务 Entity
不得重复声明基类已经提供的字段。数据库表必须与所选能力完整对齐；既有表缺列时使用新的 Flyway
Versioned Migration 向前补齐，不得修改已经发布的 Migration。

### 4.2 精确例外

只有下列非普通业务行模型可以不继承 `BaseEntity`，并且必须在设计或 ADR 中明确原因：

- Spring Authorization Server/OAuth2 官方协议表；
- Outbox、Inbox、流水、审计日志、不可修改事实表；
- 无单列技术主键或由外部协议强制定义键结构的表；
- Framework 内部技术租约、锁和基础设施状态表。

关系表、日志、流水、Outbox/Inbox、快照、不可变事实、复合主键或外部协议模型不得为了形式统一强制
继承完整 `BaseEntity`。Entity 只能位于 Infrastructure Persistence；不得进入 api/client、Domain 或公开响应。
禁止 Lombok `@Data` 和敏感全字段 `toString()`。

## 5. Mapper、Domain Repository 与 Adapter

### 5.1 Mapper

- Mapper 属于 Infrastructure Persistence，默认只继承 `MomBaseMapper<Entity>`。
- Mapper 是物理数据访问层，不承担业务授权、事务编排、状态机或跨聚合规则。
- MyBatis-Plus 已能表达的普通 CRUD 不新增重复 Mapper 方法、注解 SQL 或 XML。

### 5.2 Domain Repository Port

Domain Repository Port 必须保持框架无关：

- 只表达领域或用例需要的读取、保存、锁定、版本推进和查询语义；
- 隐藏 Entity、Mapper、Wrapper、`IPage`、affected rows 和底层数据库异常；
- 不继承 `IRepository`、`IService` 或其他 MyBatis-Plus 接口；
- Application Service 定义事务、授权和用例编排；Controller 不得直接依赖 Mapper 或具体 Adapter。

### 5.3 单表 Repository Adapter

一个主要 Mapper 对应一个主要 Entity，且普通单表 CRUD 是主要路径时，Infrastructure Adapter 应优先：

```java
@Repository
public class MybatisExampleRepository
        extends CrudRepository<ExampleMapper, ExampleEntity>
        implements ExampleRepository {
}
```

Adapter 对上只暴露 MOM Domain Repository Port，对内可使用 `getById`、`getOne`、`list`、`count`、`save`、
`updateById` 和受控 `getBaseMapper`。唯一冲突、乐观锁冲突和数据库异常仍必须转换为稳定 MOM 语义。

`CrudRepository` 是 Infrastructure 实现复用机制，不是 Domain 契约。Application/Web 不得按具体 Adapter、
`IRepository` 或 `CrudRepository` 注入。

### 5.4 多表、Query 与协议例外

以下场景不为形式统一强行继承单一 `CrudRepository`：

- 多 Mapper、多 Entity、本地聚合或关系替换；
- Query/Projection/Read Model；
- OAuth/SAS 协议表与 Framework 基础设施状态；
- 主要依赖 JOIN、CTE、窗口函数、批量 Upsert、`SKIP LOCKED` 等专用 SQL；
- 一个 Domain Repository 管理多张生命周期不同的表。

这些 Adapter 可以直接组合 Mapper；只有在显著减少重复且不破坏事务、聚合和生命周期边界时，才拆分为多个
Infrastructure 内部表级 `CrudRepository` 组件。

### 5.5 继续禁止的通用 Service

正式 bounded context 继续禁止：

- `IService<T>` 作为业务 Service 或 Repository；
- `ServiceImpl<M,T>` 作为 Application Service；
- 自定义接口继承 `IRepository<T>` 后向上暴露通用 CRUD；
- `Db`、Active Record 或静态 ORM Helper 绕过 Domain Port。

查询投影可使用专用 Row/View，但不能伪装成领域聚合或公开 HTTP DTO。

## 6. MyBatis-Plus 强制规范

### 6.1 默认实现

普通 Insert、Update、Delete、主键读取、单表等值/范围过滤、计数、固定排序和分页必须优先使用：

- `CrudRepository`（符合单表 Domain Port Adapter 条件时）；
- `MomBaseMapper`；
- Entity；
- `LambdaQueryWrapper` / `LambdaUpdateWrapper`；
- MyBatis-Plus 插件和字段 TypeHandler。

MOM 新增或实质修改的业务模块不得为这些能力新增 Mapper XML，也不得为了“SQL 可见”“格式统一”
创建重复的注解查询方法。Wrapper 条件必须使用类型安全字段引用；动态排序必须先映射到服务端白名单。

### 6.2 System Platform 零 XML 门禁

`mom-system-server/src/main/resources/mapper` 必须为空。Parameter、Dictionary、Dynamic I18n 和 Preference 的
所有正式持久化路径统一使用 MyBatis-Plus。PostgreSQL advisory lock 允许保留一条固定、参数化的 Mapper
注解语句；JSONB 使用 Framework TypeHandler；历史聚合使用受控 QueryWrapper 表达式和 Java 侧分组。

System 新增 XML、MyBatis Provider 或客户端可控 SQL 尾句均直接违反门禁。

### 6.3 自定义数据库能力

其他 bounded context 确实需要 CTE、窗口函数、批量 Upsert、`SKIP LOCKED` 等 MyBatis-Plus 无法稳定表达的
能力时，必须先完成逐语句设计审查。短且固定的参数化语句优先使用注解；XML 不是默认选择，也不能成为绕过
Entity、审计、逻辑删除或乐观锁的第二套持久化体系。

SQL 统一要求：

1. 禁止 `${}` 和客户端原始 SQL；
2. 自定义查询显式列名，禁止 `SELECT *`；
3. 大结果分页、Cursor、流式或分批处理，禁止无界加载和 N+1；
4. 批量查询和写入定义上限；
5. Update/Delete 必须有可证明的限定条件并检查 affected rows；
6. SQL 日志和异常不得输出密码、Token、Secret 或完整原始参数。

## 7. 直接 JDBC 禁区

除精确登记的协议存储和 Framework 基础设施外，正式 bounded context 生产代码禁止：

- `JdbcTemplate`；
- `JdbcClient`；
- `NamedParameterJdbcTemplate`；
- `SimpleJdbcInsert`；
- `java.sql` API；
- 自建 RowMapper 或 JDBC Helper。

Spring Authorization Server 官方 JDBC Store 是协议特例；Framework JSONB TypeHandler 是统一基础设施。
新增例外必须先有 Accepted ADR、精确类名、技术必要性、测试证据和退出条件，禁止包级或通配符白名单。

## 8. Flyway

- Schema 变更只由服务自己的 `src/main/resources/db/migration/<bounded-context>` 管理。
- 已执行的 Versioned Migration 不得修改或删除；新增变化使用更高版本。
- `clean` 在正式配置禁用，`baseline-on-migrate` 默认关闭。
- 滚动升级采用 Expand → Migrate → Contract。
- 大表索引、回填和数据迁移必须评估锁、耗时、磁盘、双版本兼容与回滚。
- 门禁以 Base/Head Git 对象比较不可变性，不以文件时间猜测历史。

## 9. 官方事实来源

物理外键与关联完整性的项目最高决策见
[ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)，Package 与 Adapter 分层见
[ADR-027](../../adr/ADR-027-服务端包结构与基础设施适配器分层.md)，MyBatis-Plus Repository 边界见
[ADR-028](../../adr/ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md)。

- [MyBatis-Plus 持久层接口与 Repository 抽象](https://baomidou.com/guides/data-interface/)
- [PostgreSQL 17 Schemas](https://www.postgresql.org/docs/17/ddl-schemas.html)
- [PostgreSQL 17 Constraints](https://www.postgresql.org/docs/17/ddl-constraints.html)
- [PostgreSQL 17 JSON Types](https://www.postgresql.org/docs/17/datatype-json.html)
- [Flyway Validate](https://documentation.red-gate.com/flyway/reference/commands/validate)
- [MyBatis-Plus ID Generator、Optimistic Locker、Logic Delete、Auto Fill](https://baomidou.com/en/guides/id-generator/)
- [MyBatis-Plus Field TypeHandler](https://baomidou.com/en/guides/type-handler/)
