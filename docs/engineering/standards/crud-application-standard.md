# MOM CRUD 与应用服务规范

- 状态：Accepted
- 生效范围：所有正式 bounded context
- 决策关联：[ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 分层职责

`Web → Application → Domain Port ← Infrastructure Repository → Mapper` 是写用例的标准方向。Web 只做 HTTP 绑定、认证接入、Bean Validation、DTO 转换和状态码；Application 负责业务授权、事务、幂等、引用校验和用例编排；Domain 维护不变量并定义 Port；Infrastructure 隐藏 Entity、Wrapper、MyBatis Page、SQL 与底层异常；Configuration 只装配。

Controller 禁止依赖 Mapper/Repository、声明事务、协调多 Repository 或返回 Entity。Application 禁止直接依赖 Mapper/Entity；Repository 不得机械复制 Mapper，也不得暴露 Wrapper、Page、Entity 或 affected rows。

## 2. 生命周期用例

- Create：校验业务唯一性和引用，依赖数据库 Unique 最终兜底；客户端重试风险存在时定义幂等键和返回重放语义。
- Update：只接受允许变化的字段，携带 Version；affected rows 为 0 必须区分不存在与并发冲突。
- 状态迁移：使用显式领域动作和允许迁移矩阵，不能退化为任意状态字段更新。
- Disable：阻止新业务使用但保留历史；必须定义对既有引用和授权的影响。
- Delete：默认不是 Disable 的别名；执行引用保护、保留期和审计检查，普通请求禁止无界物理清理。
- Archive：迁移到受控历史生命周期，保留可追踪、完整性验证和恢复/查询入口。

禁止以通用 `BaseCrudController`、`BaseCrudService`、泛型 Repository、MyBatis-Plus `IService/ServiceImpl` 统一所有领域生命周期。

## 3. 模型边界与校验

Request/Response DTO 属于 Web 或稳定 API 契约；Command/Query 属于 Application；领域对象属于 Domain；Entity 与 Mapper 属于 Infrastructure；Projection/Row 属于专用查询边界。禁止在层之间复用 Entity 省略映射。

Bean Validation 只处理协议形状、长度和格式，Application/Domain 必须再次保证权限、引用、唯一性、状态机和并发规则。客户端不得提交技术 ID 生成策略、审计字段、逻辑删除位或服务器拥有的状态。

## 4. MyBatis-Plus 默认路径

普通单表 Insert、按主键读取、等值/范围过滤、计数、固定排序、分页、Update、逻辑删除优先使用：

- `MomBaseMapper<Entity>`；
- MyBatis-Plus Entity 映射与 `ASSIGN_ID`；
- 项目统一 `MetaObjectHandler`；
- `LambdaQueryWrapper` / `LambdaUpdateWrapper`；
- MyBatis-Plus `Page` 仅限 Infrastructure 内部；
- 按能力选择 `BaseIdEntity`、`BaseAuditEntity`、`BaseEntity`。

MyBatis-Plus 能清晰表达的操作禁止新增 XML、注解 SQL、重复 Mapper 方法、JdbcTemplate/JdbcClient/NamedParameterJdbcTemplate/SimpleJdbcInsert、`java.sql` 或第二套 JDBC Repository。Wrapper-only Update 可能跳过实体自动填充；涉及审计/version 时应传 Entity 或显式写全并检查行数，不得静默漏填。

自定义 SQL 仅用于 DSL 不适合的数据库特性或有界多表投影，必须参数化、显式列、说明必要性、处理审计/version、检查 affected rows，并有 PostgreSQL 证据。

## 5. 事务、幂等与并发

写事务默认位于 Application 公共方法，传播为 `REQUIRED`。多表业务写、Outbox INSERT 必须按用例要求共享同一 DataSource 和事务；事务内禁止 RocketMQ、设备动作、人工等待和无界远程调用。

幂等键保持原始字节语义，不 Trim/改大小写/归一化；占位与结果有 TTL，Redis 故障策略明确，但数据库业务唯一约束和状态机仍是最终防线。乐观锁通过 Entity + `@Version` 或明确条件更新完成；冲突返回稳定 409，不泄露 SQL/约束名。

所有写入必须检查 affected rows。批量操作定义条目数、单项/整体原子性、重复项语义、锁顺序、超时和失败结果；禁止无界批量和循环远程 N+1。

## 6. 分页、排序和查询

分页请求必须有服务端最大 page size、稳定且唯一的次级排序；MyBatis `Page` 不进入 Web/API。排序字段由服务端枚举映射为固定列，禁止客户端原始 SQL 表达式。大数据量场景评估 Keyset/Cursor，不在 Java 内存中加载全量后分页。

单表列表可以由 Repository 返回领域摘要或 Application View；多表组合列表必须进入专用 Query Service/Mapper/Projection，遵守多表关联规范。

## 7. 审计与错误模型

CurrentActor 与统一 MetaObjectHandler 填充审计；缺少可信 Actor 的业务写 fail closed。自定义 SQL 显式写审计字段。公开错误稳定区分 400 校验、404 不存在、409 唯一/版本/状态冲突、503/504 依赖故障，禁止回显 SQL、连接信息、约束原文、Token 或 Secret。

## 8. 验收证据

每个 CRUD Slice 至少覆盖：成功、Bean Validation、唯一冲突、引用不存在、乐观冲突、affected rows、删除/停用/归档保护、幂等重放、批量上限、事务回滚、分页与排序、审计填充、PostgreSQL 特性。规范文件存在不等于验收完成，必须检查最终实现实际采用了上述技术路径。
