# MOM 持久化与数据建模规范

## 1. 适用范围与事实层次

本规范冻结 MOM 新增和实质修改数据访问代码的工程边界。当前事实基线是 PostgreSQL 17、Flyway
12.4.0、MyBatis-Plus 3.5.17、Spring Framework 7 / Spring Boot 4.1 与 Spring Authorization Server
7.1.0；其行为以对应官方资料为事实来源，本文中的命名、失败策略和分层是 MOM 项目决策。
历史实现差异见 [S02 历史例外清单](../P1.6-S02-持久化历史例外清单.md)，不得据此批量重构或复制例外。

权威决策优先级为 ADR-020、ADR-004、ADR-014 及本文；消息与事务决策由 ADR-005、ADR-009、
ADR-015、ADR-016 和事务一致性规范补充。

## 2. 数据所有权与物理命名

- V1 使用共享 PostgreSQL 数据库 `mom_platform`，每服务使用独立 `mom_<bounded-context>` Schema。
- 服务账号只拥有自身 Schema 所需权限；默认一个实例只有一个权威 DataSource 和 HikariCP。
- 业务表、Outbox 和 Inbox 共用本服务 DataSource 与事务管理器；数据源不可用时写入 fail closed。
- 禁止跨 Schema JOIN、外键、读写、共享 Mapper，禁止用 View 或只读账号静默绕过边界。
- 跨域查询使用 Query API、本地事件投影或明确查询服务；多数据源必须由 ADR 明确授权。

PostgreSQL 未限定名称时按 `search_path` 解析对象，且在可写 Schema 位于搜索路径时存在对象劫持风险；
因此部署必须设置受控 `currentSchema/search_path`，不能依赖公共可写 Schema。该描述是 PostgreSQL 事实，
服务隔离和最小权限是 MOM 决策。

## 3. 表、列、约束与类型

### 3.1 命名

- 标识符使用小写 `snake_case`，不以双引号制造大小写敏感名称。
- 新表遵循所属 Schema 已冻结的本地前缀惯例；不要求所有 Schema 共享表前缀。
- 技术字段按实际能力选用 `id`、`created_at/by`、`updated_at/by`、`version`、`deleted`。
- 领域状态时间使用 `activated_at`、`revoked_at`、`occurred_at` 等明确名称，不能以 `updated_at` 代替。
- S02 不重命名任何现有表、列、索引或约束。

当前实现事实：IAM 业务表使用 `iam_*`，SAS 官方表使用 `oauth2_*`；MDM/Integration 的平台技术表使用
`mom_outbox_event`/`mom_inbox_event`，历史探针使用 `technical_*`。这些局部惯例不会在 S02 被重命名，
新表必须在所属 bounded context 的设计中明确前缀，而非全仓机械统一。

### 3.2 主键、业务键和约束

- Java 技术主键是 `String`，PostgreSQL 是 `varchar(19)`，MyBatis-Plus 默认 `ASSIGN_ID`；JSON ID
  必须是 String。技术主键与可变业务编码分离，不以业务编码作为跨服务技术引用。
- 业务唯一性最终由数据库 Unique Constraint/Unique Index 保护；Application 预检查只改善错误信息。
- 软删除表必须决定删除后是否允许复用。允许复用时评估 Partial Unique Index；永久唯一则保持普通唯一约束。
- 不用随机删除标记拼接规避唯一设计。
- 同一 bounded context 内的 FK 按生命周期、写序、删除、性能、批量导入和历史保留逐项决定；不能以
  “微服务”作为删除全部本地完整性约束的理由。
- 稳定、简单且适合数据库兜底的不变量应评估 Check Constraint；频繁演进或复杂规则保留在 Domain。

PostgreSQL 的唯一约束会自动创建唯一 B-tree 索引；Partial Index 只覆盖满足谓词的行。这是数据库事实，
采用何种唯一语义是领域决策。

### 3.3 Java/PostgreSQL 类型

| 语义 | Java | PostgreSQL | 约束 |
|---|---|---|---|
| 全球时间点 | `Instant` | `timestamptz` | 连接会话 UTC；不以 `LocalDateTime` 表示 |
| 业务日期 | `LocalDate` | `date` | 时区/工厂日界线另行定义 |
| 金额、数量、质量、浓度、比例 | `BigDecimal` | `numeric(p,s)` | 精度和 scale 由领域范围确定，禁用 float/double |
| 布尔 | `boolean/Boolean` | `boolean` | 字段名和 true/false 语义明确 |
| 枚举 | 稳定字符串代码 | 字符列 | 默认不使用 PostgreSQL Native Enum |

JSONB 只用于真正半结构化且检索需求明确的数据，不能替代核心列、状态机或主数据建模。文件正文不默认
进入业务表；大文本、二进制和文件元数据必须分别设计。

## 4. Entity 能力模型

| 基类 | 适用能力 |
|---|---|
| `BaseIdEntity` | 仅技术主键 |
| `BaseCreatedEntity` | 追加/不可变记录，只需创建审计 |
| `BaseAuditEntity` | 可修改且需创建/更新审计，无通用逻辑删除与乐观锁 |
| `BaseEntity` | 同时需要审计、乐观锁和逻辑删除的普通可更新业务实体 |

Outbox、Inbox、库存流水、谱系事实、快照、安全审计、OAuth2/SAS 协议表、Session/Refresh 特殊状态、
复合主键关系、技术租约和状态历史不得为了形式统一继承 `BaseEntity`。Entity 不得进入 api 或公开响应；
禁止 Lombok `@Data`、敏感全字段 `toString()` 和以全字段 `equals/hashCode` 表示数据库身份。

MyBatis-Plus `MetaObjectHandler` 只在对应插入/更新路径触发。Wrapper-only Update 不会可靠获得实体填充；
MOM 的 `MomBaseMapper` 因此拒绝该形式，自定义 SQL 必须显式处理审计和版本字段。

## 5. Mapper、Repository 与 Application

- Mapper 属于 Infrastructure Persistence，默认只继承 `MomBaseMapper`；普通单表查询由 Repository 使用
  `LambdaQueryWrapper`、`LambdaUpdateWrapper`、`Page` 或受控 `last` 表达，不为框架已支持的能力重复建 XML。
- Repository 是领域/应用所需的持久化语义边界，不是 Mapper 机械别名。它隐藏 SQL、Wrapper、Page、
  Entity 和 affected rows，并把不存在、唯一冲突、版本/锁冲突转换为稳定语义。
- Application Service 的公共方法定义业务事务边界、授权和用例编排；不得把 Mapper Entity 返回 Web。
- 禁止把 MyBatis-Plus `IService`/`ServiceImpl` 当作 MOM 领域服务或 Repository 契约。
- 查询投影可使用专用 Row/View，但不能伪装成领域聚合或公开 HTTP DTO。

### 5.1 数据访问技术栈选择

1. MOM 正式业务表默认且强制使用 **MyBatis-Plus 优先** 的持久化体系。普通 Insert/Update/Delete、
   主键查询、单表等值/范围过滤、计数、固定排序和分页必须优先使用 `MomBaseMapper`、Entity、
   `LambdaQueryWrapper`/`LambdaUpdateWrapper` 与 MyBatis-Plus 插件。
2. 对 MyBatis-Plus 已能清晰表达的单表 CRUD 或查询，禁止为了“SQL 可见”“格式统一”或复制既有模式
   新增 Mapper XML、注解 SQL 或重复 Mapper 方法。XML 数量不是治理质量，重复 SQL 会扩大维护和审计面。
3. 只有在 MyBatis-Plus DSL 无法清晰、稳定地表达数据库语义时，才允许自定义 SQL，例如 JSONB 类型转换、
   CTE、窗口函数、复杂聚合投影、批量 Upsert、`SKIP LOCKED` 等。短且固定的语句可使用注解；结构复杂、
   需要复用片段或专用投影时才使用 XML，并在 Mapper Javadoc 中说明不能使用 MP 的具体原因。
4. `FOR UPDATE` 等固定尾句可通过受控 Wrapper 表达时，不应单独创建 XML；`last` 只能拼接服务端固定文本或
   已验证的非负数字，不得接收客户端标识符、表达式或原始字符串。
5. 除精确登记的协议存储、Framework 基础设施与默认关闭的技术验证设施外，正式 bounded context
   生产代码禁止直接依赖 `JdbcTemplate`、`JdbcClient`、`NamedParameterJdbcTemplate`、`SimpleJdbcInsert`
   或 `java.sql` API，不得通过改类名、封装 Helper 或自行实现 RowMapper 建立第二套持久化体系。
6. Spring Authorization Server 官方 JDBC Store 是协议特例；Outbox/Inbox 等 Framework 基础设施按其独立
   规范治理。技术探针只能使用精确类名例外，必须记录责任范围、默认关闭条件和退出 Slice。
7. 新增直接 JDBC 例外必须先有 Accepted ADR、精确类名、技术必要性、事务/审计/失败语义、测试证据和
   删除条件。不得以“SQL 复杂”“性能更高”“实现方便”或“位于 Infrastructure”为理由直接获得例外。
8. 例外不得使用包级、模块级或通配符白名单；历史例外只冻结事实，不授予新代码复制权限。

## 6. MyBatis-Plus 与自定义 SQL

1. Wrapper 条件必须由服务端字段和类型安全方法引用构建；禁止将用户输入放入 `${}` 或 SQL 尾句。
2. 动态排序先映射到服务端列白名单；客户端字段名或表达式不得直接成为 SQL 标识符。
3. MyBatis-Plus 自动生成的列映射可用于 Entity 单表查询；自定义 SQL 必须显式列名，禁止 `SELECT *`。
4. 大结果必须分页、Cursor、流式或分批处理，禁止无界加载和 N+1；批量查询/写入均定义上限。
5. 自定义 SQL 显式维护审计和版本；Update/Delete 必须有可证明的限定条件并检查 affected rows。
6. SQL 日志、异常和候选报告不得输出密码、Token、Secret、完整 SQL 参数或原始约束文本。
7. 复杂 XML SQL 使用中文说明解释非显然的一致性、锁、数据库特性和为何不能使用 MyBatis-Plus Wrapper。
8. Mapper XML、MyBatis Java 注解 SQL 和精确 JDBC 例外都必须进入静态检查；不得利用 Java Text Block
   绕过 `${}`、`SELECT *`、跨 Schema 或无条件写入审查。

静态门禁解析 XML 元素，并扫描正式 Server Java 的技术栈和可证明 SQL 候选；它能识别 `${}`、`SELECT *`、
跨 Schema 引用及显然无条件写入的一部分，但不宣称证明运行时 SQL 完整语义。Wrapper 条件完整性、索引选择、
事务竞争和 N+1 仍需 Review 与测试。

## 7. Flyway

- Schema 变更只由服务自己的 `src/main/resources/db/migration/<bounded-context>` 管理。
- 已合并执行的 Versioned Migration 不得修改或删除；新增变更使用更高版本且同路径版本唯一。
- `clean` 在正式配置禁用，`baseline-on-migrate` 默认关闭；Repeatable Migration 只用于真正可重复对象。
- 不用启动脚本复制 DDL，不让业务实例边运行边执行外部脚本式修补。
- 滚动升级采用 Expand → Migrate → Contract；删除列/表至少跨一个兼容版本。
- 大表索引、回填和数据迁移必须评估锁、耗时、磁盘、双版本兼容、回滚或补偿；未评估不得进入普通启动迁移。
- PostgreSQL 支持在事务块中执行多数 DDL，但并非所有迁移都因此安全或廉价；锁持续时间、不可事务化操作及
  Flyway 对该 Migration 的事务执行方式必须逐项核对，不能把“DDL 可回滚”当作在线迁移证明。
- 门禁以 Base/Head Git 对象比较不可变性，不以文件时间或当前工作区猜测历史。

S02 不新增、修改或删除任何业务 Migration。

## 8. 官方事实来源

- [PostgreSQL 17 Schemas](https://www.postgresql.org/docs/17/ddl-schemas.html)
- [PostgreSQL 17 Constraints](https://www.postgresql.org/docs/17/ddl-constraints.html)
- [PostgreSQL 17 Partial Indexes](https://www.postgresql.org/docs/17/indexes-partial.html)
- [PostgreSQL Date/Time 与 Numeric 类型](https://www.postgresql.org/docs/17/datatype-datetime.html)
- [Flyway Validate](https://documentation.red-gate.com/flyway/reference/commands/validate)
- [Flyway Clean Disabled](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-clean-disabled-setting)
- [MyBatis-Plus ID Generator、Optimistic Locker、Logic Delete、Auto Fill](https://baomidou.com/en/guides/id-generator/)
