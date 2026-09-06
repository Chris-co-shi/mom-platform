# MOM 持久化与数据建模规范

## 1. 适用范围与事实层次

本规范冻结 MOM 新增和实质修改数据访问代码的工程边界。当前事实基线是 PostgreSQL 17、Flyway
12.4.0、MyBatis-Plus 3.5.17、Spring Framework 7 / Spring Boot 4.1；框架行为以官方资料为事实来源，
命名、失败策略和分层是 MOM 项目决策。

当前分层最高决策来源为 [ADR-042](../../adr/ADR-042-MOM渐进式分层与对象模型.md)。ADR-028 继续作为
已经进入 Level 2/3、确实需要 Domain Repository Port / MyBatis Repository Adapter 的复杂场景参考，
不再解释为所有 bounded context 第一版的默认强制结构。

历史实现差异见 [S02 历史例外清单](../P1.6-S02-持久化历史例外清单.md)。历史差异只用于识别债务，
不得无判断复制为新代码模式。

## 2. 数据所有权与物理命名

- V1 使用共享 PostgreSQL 数据库 `mom_platform`，每服务使用独立 `mom_<bounded-context>` Schema；
- 服务账号只拥有自身 Schema 所需权限；默认一个实例只有一个权威 DataSource 和 HikariCP；
- 业务表、Outbox 和 Inbox 共用本服务 DataSource 与事务管理器；数据源不可用时写入 fail closed；
- 禁止跨 Schema JOIN、外键、读写、共享 Mapper，禁止用 View 或只读账号静默绕过边界；
- 跨域查询使用 Query API、本地事件投影或明确查询服务；多数据源必须由 ADR 明确授权；
- 部署必须设置受控 `currentSchema/search_path`，不能依赖公共可写 Schema。

## 3. 表、列、约束与类型

### 3.1 命名

- 标识符使用小写 `snake_case`，不以双引号制造大小写敏感名称；
- 普通可变业务表按能力选择审计、版本、逻辑删除列，不为了统一模板强制所有表拥有完整字段集；
- 领域状态时间使用 `activated_at`、`revoked_at`、`occurred_at` 等明确名称，不能以 `updated_at` 代替；
- 新表遵循所属 bounded context 已冻结的本地前缀惯例。

### 3.2 主键、业务键和约束

- Java 技术主键是 `String`，PostgreSQL 是 `varchar(19)`，MyBatis-Plus 使用 `ASSIGN_ID`；
- 技术主键与可变业务编码分离，不以业务编码作为跨服务技术引用；
- 业务唯一性最终由数据库 Unique Constraint/Unique Index 保护；Application 预检查只改善错误信息；
- 逻辑删除后是否允许复用业务键必须显式决定；未批准复用时保留永久唯一约束；
- MOM 自主业务表、关系表、流水表、快照表和平台表全面禁止物理 FK 与物理级联，同一 bounded context、
  Schema 或聚合也不例外；完整性由 Application 校验、受控删除、Unique/Check、索引、事务、测试和必要对账共同保证；
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

## 4. Entity 规范

### 4.1 默认规则

MOM 关系型 Entity 必须按表能力选择最小充分基类：

- 仅需技术主键：`BaseIdEntity`；
- 需要创建和修改审计：`BaseAuditEntity`；
- 同时需要乐观锁与逻辑删除的普通可变业务表：`BaseEntity`。

业务 Entity 不得重复声明基类已经提供的字段。数据库表必须与所选能力完整对齐；既有表缺列时使用新的
Flyway Versioned Migration 向前补齐，不得修改已经发布的 Migration。

Entity 允许位于 Level 1：

```text
infrastructure.entity
```

或复杂 Persistence 结构：

```text
infrastructure.persistence.entity
```

Entity 不得进入 `*-api`/`*-client`，不得直接作为公开 HTTP Request/Response，不跨服务暴露。

### 4.2 精确例外

下列非普通业务行模型可以不继承 `BaseEntity`，并按自身生命周期选择基类或完全不继承：

- Outbox、Inbox、流水、审计日志、不可修改事实表；
- 关系表；
- 快照表；
- 无单列技术主键或外部协议强制键结构的表；
- Framework 内部技术租约、锁和基础设施状态表；
- 仍存在的标准协议表。

禁止 Lombok `@Data` 和敏感全字段 `toString()`。

## 5. Mapper、CrudRepository、Domain Repository 与 Application

### 5.1 Mapper 是默认数据访问入口

普通单表 Mapper：

- 位于 `infrastructure.mapper` 或 `infrastructure.persistence.mapper`；
- 默认继承 `MomBaseMapper<Entity>`；
- 不承担业务授权、完整事务编排、状态机或跨聚合规则；
- MyBatis-Plus 已能表达的普通 CRUD/Batch 不新增重复 Mapper 方法、注解 SQL 或 XML。

Level 1 Application 可以直接依赖本 bounded context 的 Mapper 和 Entity：

```text
Controller → Application → Mapper → Entity
```

这是 ADR-042 明确接受的简化，不属于架构违规。Controller 仍不得直接依赖 Mapper/Entity。

### 5.2 具体 CrudRepository 是可选 Infrastructure，不是必选包装层

不要因为存在 Mapper 就自动创建：

```text
XxxRepository extends CrudRepository<XxxMapper, XxxEntity>
```

MyBatis-Plus `CrudRepository` 不是 DDD Repository，也不是 Domain Port。它是建立在 Mapper 之上的具体 Infrastructure 复用基类，主要提供 `IRepository` 风格 API、Batch 执行及相关便利能力。

Level 1 允许在出现真实持久化复用后引入具体 `XxxRepository`，不要求额外接口。成立信号包括：

- 多个用例重复同一组持久化技术操作；
- 需要集中管理 batchSize、分片、BatchResult 检查或数据库异常转换等技术策略；
- 数据访问噪音已经明显挤占 Application，而提取后能形成稳定的可复用持久化职责；
- Repository 拥有当前真实方法和行为，不是零逻辑继承空壳。

以下理由不足以单独引入 `CrudRepository`：

- “每张表都应该有 Repository”；
- “官方提供了 CrudRepository”；
- “名字比 Mapper 更像分层”；
- “以后可能换数据库”；
- “需要批量操作”。

其中“需要批量操作”不是充分理由，是因为 MyBatis-Plus 3.5.17 `BaseMapper` 已经直接提供批量查询、删除、插入、按 ID 更新和 insert-or-update 能力。

### 5.3 Domain Repository Port（Level 2/3）

当 bounded context 已经进入 Domain/Port 模式时，Repository Port 必须保持框架无关：

- 只表达领域或用例需要的读取、保存、锁定、版本推进语义；
- 隐藏 Entity、Mapper、Wrapper、`IPage`、affected rows 和底层数据库异常；
- 不继承 `IRepository`、`IService` 或其他 MyBatis-Plus 接口；
- Application 定义事务、授权和用例编排；
- Controller 不得直接依赖 Mapper 或具体 Adapter。

### 5.4 CrudRepository 在 Level 2/3 的使用

已经明确需要 Domain Repository Port，且一个主要 Mapper 对应一个主要 Entity、普通单表 CRUD 是主要路径时，Infrastructure Adapter 可以内部复用 MyBatis-Plus `CrudRepository`：

```java
@Repository
public class MybatisExampleRepository
        extends CrudRepository<ExampleMapper, ExampleEntity>
        implements ExampleRepository {
}
```

此时 `CrudRepository` 仍然只是 Adapter 的技术实现细节，不能向 Domain 暴露。

因此项目统一区分：

```text
BaseMapper                    默认数据访问能力
XxxRepository/CrudRepository  可选具体持久化封装
Domain Repository Port        Level 2/3 框架无关业务契约
```

### 5.5 继续禁止的通用 Service

正式 bounded context 禁止：

- `IService<T>` 作为业务 Application/Service 或 Repository；
- `ServiceImpl<M,T>` 作为业务 Application；
- 自定义接口继承 `IRepository<T>` 后向上暴露通用 CRUD；
- `Db`、Active Record 或静态 ORM Helper 绕过明确业务边界。

## 6. QueryMapper 与多表查询

简单单表查询继续使用普通 Mapper 或已经因真实复用而存在的具体 Repository。

只有 JOIN、统计、组合分页、搜索或复杂数据库能力真实出现时，再创建：

```text
infrastructure.query
```

或：

```text
infrastructure.persistence.query
```

推荐数据流：

```text
Application → QueryMapper → Row/Projection → View
```

QueryMapper 不要求继承 `MomBaseMapper`，因为它不是普通 Entity CRUD Mapper。

查询投影可以使用专用 `Row`/`Projection`，最终 Application 结果使用 `View`。多表查询形状默认不是 DDD Aggregate。

详细规则见 `multi-table-association-query-standard.md`。

## 7. MyBatis-Plus 强制规范

### 7.1 默认实现

普通 Insert、Update、Delete、主键读取、单表等值/范围过滤、计数、固定排序、分页和基础批量操作优先使用：

- `MomBaseMapper`；
- Entity；
- `LambdaQueryWrapper` / `LambdaUpdateWrapper`；
- MyBatis-Plus 插件和字段 TypeHandler；
- 已因真实持久化复用而存在的具体 `CrudRepository`。

MOM 新增或实质修改的业务模块不得为这些能力新增 Mapper XML，也不得为了“SQL 可见”“格式统一”创建重复注解查询方法。Wrapper 条件必须使用类型安全字段引用；动态排序必须先映射到服务端白名单。

### 7.2 BaseMapper 批量能力与事务边界

当前 MyBatis-Plus 3.5.17 基线下，优先使用：

```text
selectByIds(ids)
deleteByIds(ids)
insert(entities [, batchSize])
updateById(entities [, batchSize])
insertOrUpdate(entities [, batchSize])
```

规则：

1. Batch 能力不等于业务事务；业务事务默认仍位于 Application 公共方法；
2. `insert/updateById/insertOrUpdate(Collection)` 返回 `List<BatchResult>`，需要检查结果的用例不得静默丢弃；
3. `insertOrUpdate` 必须符合业务语义，不能因为方便而绕过唯一性、状态机、版本或显式 Create/Update 规则；
4. 批量查询/写入必须定义上限，超限采用拒绝、分页或显式分片；
5. 批量写定义整体原子性、部分失败、重复项和不存在 ID 的处理语义；
6. 只有批量切分/策略在多个用例间形成复用时，才把其收敛进具体 `CrudRepository`。

### 7.3 System Platform 零 XML 门禁

`mom-system-server/src/main/resources/mapper` 必须为空。Parameter、Dictionary、Dynamic I18n 和 Preference 的既有正式持久化路径统一使用 MyBatis-Plus。PostgreSQL advisory lock 允许保留受控固定参数化语句；JSONB 使用 Framework TypeHandler；历史聚合按已有 Accepted 决策执行。

### 7.4 自定义数据库能力

确实需要 CTE、窗口函数、数据库特有批量 Upsert、`SKIP LOCKED` 等 MyBatis-Plus 无法稳定表达的能力时，必须先完成逐语句设计审查。短且固定的参数化语句可使用注解；复杂多表查询可使用专用 QueryMapper/XML。XML 不是普通 CRUD 的默认选择，也不能成为绕过 Entity、审计、逻辑删除或乐观锁的第二套持久化体系。

SQL 统一要求：

1. 禁止 `${}` 和客户端原始 SQL；
2. 自定义查询显式列名，禁止 `SELECT *`；
3. 大结果分页、Cursor、流式或分批处理，禁止无界加载和 N+1；
4. 批量查询和写入定义上限；
5. Update/Delete 必须有可证明的限定条件并检查 affected rows；
6. SQL 日志和异常不得输出密码、Token、Secret 或完整原始参数。

## 8. 直接 JDBC 禁区

除精确登记的协议存储和 Framework 基础设施外，正式 bounded context 生产代码禁止无 ADR 引入：

- `JdbcTemplate`；
- `JdbcClient`；
- `NamedParameterJdbcTemplate`；
- `SimpleJdbcInsert`；
- `java.sql` API；
- 自建 RowMapper 或 JDBC Helper。

新增例外必须有 Accepted ADR、精确类名、技术必要性、测试证据和退出条件，禁止包级或通配符白名单。

## 9. Flyway

- Schema 变更只由服务自己的 `src/main/resources/db/migration/<bounded-context>` 管理；
- 已执行的 Versioned Migration 不得修改或删除；新增变化使用更高版本；
- `clean` 在正式配置禁用，`baseline-on-migrate` 默认关闭；
- 滚动升级采用 Expand → Migrate → Contract；
- 大表索引、回填和数据迁移必须评估锁、耗时、磁盘、双版本兼容与回滚；
- 门禁以 Base/Head Git 对象比较不可变性，不以文件时间猜测历史。

## 10. 架构升级触发

从 Mapper 直达路径增加一个具体 `CrudRepository`，**不自动意味着** bounded context 已经升级到 Level 2/3；它可以只是 Level 1 的 Infrastructure 重构。

升级到 Domain Repository Port 的触发仍然是：

- 多个真实持久化实现；
- 聚合持久化与领域一致性边界；
- ORM 明显污染业务模型；
- 测试替换收益；
- 复杂持久化语义需要框架无关稳定契约。

“更符合 DDD”“以后可能换数据库”“每张表都应该有 Repository”不是充分理由。

## 11. 官方事实与项目决策来源

项目决策优先级：

1. [ADR-042](../../adr/ADR-042-MOM渐进式分层与对象模型.md)：当前渐进式分层和对象模型；
2. [ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)：物理外键与完整性；
3. [ADR-027](../../adr/ADR-027-服务端包结构与基础设施适配器分层.md)：Level 2/3 Package/Adapter 参考；
4. [ADR-028](../../adr/ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md)：Level 2/3 Repository 参考。

框架事实来源：

- [MyBatis-Plus 持久层接口](https://baomidou.com/guides/data-interface/)
- [MyBatis-Plus BaseMapper 3.5.17 源码](https://github.com/baomidou/mybatis-plus/blob/v3.5.17/mybatis-plus-core/src/main/java/com/baomidou/mybatisplus/core/mapper/BaseMapper.java)
- [MyBatis-Plus CrudRepository 3.5.17 源码](https://github.com/baomidou/mybatis-plus/blob/v3.5.17/mybatis-plus-spring/src/main/java/com/baomidou/mybatisplus/spring/repository/CrudRepository.java)
- [PostgreSQL 17 Schemas](https://www.postgresql.org/docs/17/ddl-schemas.html)
- [PostgreSQL 17 Constraints](https://www.postgresql.org/docs/17/ddl-constraints.html)
- [PostgreSQL 17 JSON Types](https://www.postgresql.org/docs/17/datatype-json.html)
- [Flyway Validate](https://documentation.red-gate.com/flyway/reference/commands/validate)
- [MyBatis-Plus ID Generator、Optimistic Locker、Logic Delete、Auto Fill](https://baomidou.com/en/guides/id-generator/)
- [MyBatis-Plus Field TypeHandler](https://baomidou.com/en/guides/type-handler/)