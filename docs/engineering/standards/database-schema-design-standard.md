# MOM 数据库表结构设计规范

- 状态：Accepted
- 数据库基线：PostgreSQL 17 / Flyway 12
- 决策关联：[ADR-026](../../adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

## 1. 所有权、Schema 与命名

数据库为 `mom_platform`，每服务独占 `mom_<bounded-context>` Schema。表、列和约束使用小写 `snake_case`，禁止双引号大小写标识符、跨 Schema JOIN/读写/View/FK。表名前缀遵循所属上下文现有惯例。

约束与索引统一命名：`pk_<table>`、`uk_<table>_<semantic>`、`ck_<table>_<semantic>`、`ix_<table>_<query_semantic>`。名称表达业务/查询语义，不以自动生成名进入正式 Migration。

## 2. 表类型与生命周期

设计前标记普通业务表、关系表、事实/流水、快照/历史、Outbox/Inbox、投影、协议/基础设施表。每类必须说明创建者、可变性、状态迁移、停用、逻辑/物理删除、归档、保留期、重建或冲销方式。关系、流水、快照和技术表不得为了统一形式强制继承完整 `BaseEntity`。

## 3. 键、字段与类型

- MOM 技术主键：Java `String`、PostgreSQL `varchar(19)`、MyBatis-Plus `ASSIGN_ID`；业务 Code/单号独立建模。
- 业务唯一性由命名 Unique Constraint/Index 兜底，并明确逻辑删除后是否允许复用。
- 字段长度、`numeric(p,s)` 精度/scale 必须来自领域范围和增长假设，不使用无依据默认。
- Null 表示未知/不适用；默认值只用于确定语义，不用空串、0 或 epoch 隐藏缺失。
- 技术时间点使用 `timestamptz`/Java `Instant`，连接 UTC；业务日期使用 `date`，禁止无时区 `timestamp` 表示技术时间。
- 金额、数量、比例使用 `numeric(p,s)`/`BigDecimal`，禁止 `float`、`real`、`double precision`。
- 状态使用稳定字符串并通过 Check 或受控状态表约束；version 为非负整数并有 `ck_<table>_version_non_negative`。

## 4. Entity 能力与审计

按能力选择：仅技术 ID 使用 `BaseIdEntity`；需要创建/修改审计使用 `BaseAuditEntity`；同时需要乐观锁和逻辑删除的普通可变业务表使用 `BaseEntity`。不需要的能力不机械加列。审计字段由可信 Actor/MetaObjectHandler 填充；事实表使用自身发生时间、来源和不可变语义。

逻辑删除仅用于可恢复、默认隐藏且唯一性策略明确的普通实体；事实、流水、快照、Outbox/Inbox 和协议状态使用自身生命周期。物理删除与归档需保留期、批量上限、锁风险、审计、恢复和引用保护。

## 5. 约束、关联与索引

业务表全面禁止物理 FK 和级联。关联列仍须有明确命名、类型与权威来源，完整性由 Application 校验、受控删除、Unique/Check、事务、乐观锁、IT、孤儿诊断和对账保证。

索引必须映射到具体查询的过滤、JOIN、排序和覆盖需求。禁止机械为所有 `_id` 创建单列索引；组合索引考虑最左前缀、选择性、写放大和重复索引。部分/表达式/GIN 索引必须说明适用谓词与运维成本。

## 6. 注释、JSONB、大文本与敏感信息

正式业务表必须 `COMMENT ON TABLE`，技术 ID、业务键、关联列、状态、金额/数量、时间、版本、删除和敏感列必须 `COMMENT ON COLUMN`。注释描述语义和单位，不复制字段名。

JSONB 仅用于真实半结构化且演进/检索明确的数据，核心业务字段、状态机、权限和引用不得藏入 JSONB；定义 schema/version、大小上限和索引策略。大文本说明最大尺寸、读取路径、压缩/外部存储和保留期。密码、Token、Secret 不进入普通业务表；个人/商业敏感字段说明最小化、加密/脱敏、访问与清理。

## 7. 数据量、归档与 Migration

设计记录必须估算初始量、日增、峰值、保留期、最大单聚合集合和查询范围，决定分区/归档/批处理，不提前无依据分区。

Flyway 只管理本服务 Schema；已发布 Versioned Migration 不修改/删除。滚动变更采用 Expand → Migrate → Contract。大表加列、回填、类型变更、唯一约束和索引评估锁级别、扫描、磁盘、超时、并发写、`CREATE INDEX CONCURRENTLY` 与 Flyway 事务限制；提供可验证的恢复/补偿方案。

## 8. 自动证明与人工 Review

门禁可证明命名、显式 PK/UK/CK/索引、技术 ID、时间/精度类型、version Check、注释、跨 Schema、版本不可变和无业务 FK。字段长度合理性、索引有效性、真实基数、生命周期和业务完整性只能输出精确 Review Candidate，并由设计记录、PostgreSQL IT、孤儿查询和 EXPLAIN 证据关闭。
