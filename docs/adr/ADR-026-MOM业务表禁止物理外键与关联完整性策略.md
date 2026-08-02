# ADR-026：MOM 业务表禁止物理外键与关联完整性策略

- Status：Accepted
- Date：2026-07-30
- Decision owner：Chris
- Scope：MOM 自主控制的业务表、关系表、流水表、快照表和平台表

## Context

MOM 采用单库多 Schema、每服务独立 `mom_<bounded-context>` Schema 的演进方式。物理外键会把删除、迁移、发布顺序和大表维护隐式绑定到数据库 DDL，无法表达停用、归档、历史保留、快照和跨服务最终一致等领域语义。既有持久化规范曾允许“同一 bounded context 内逐项决定 FK”，该口径不足以形成一致的建模与门禁结果。

Chris 已将“业务表禁止物理外键”明确指定为 P1.6 项目决策，本 ADR 对该决定收口，不再把是否禁用外键作为开放问题。

## Decision

1. MOM 自主业务表、关系表、流水表、快照表和平台表禁止 `FOREIGN KEY`、`REFERENCES`、`ON DELETE CASCADE`、`ON UPDATE CASCADE`，同一服务、Schema、bounded context 或聚合也不例外。
2. 禁止跨 Schema JOIN、读写、共享 Mapper、View 绕界和物理外键。跨服务只保存稳定 ID/Reference、必要快照或可重建的本地只读 Projection。
3. 关联完整性由 Application Service 的引用校验、受控删除/停用/归档检查、Unique/Check、查询索引、本地事务、乐观锁、affected rows、集成测试、迁移前后孤儿检查和定期对账共同保证。
4. 多表写入由 Application Service 编排并位于明确本地事务；数据库 Trigger 不得隐式创建、修改、级联删除业务数据。
5. 已发布 Versioned Migration 不修改。现存业务 FK 使用更高版本 Migration 显式 `DROP CONSTRAINT`，保留或补齐与真实查询匹配的索引，并在应用层和 PostgreSQL IT 中补足完整性证据。
6. 约束命名统一使用 `pk_<table>`、`uk_<table>_<semantic>`、`ck_<table>_<semantic>`、`ix_<table>_<query_semantic>`。

本决策明确推翻并收紧 `persistence-data-modeling-standard.md` 中“同一 bounded context 内逐项决定 FK”的旧表述。

## Alternatives

| 方案 | 结论 | 原因 |
|---|---|---|
| 同域允许 FK、跨域禁止 | 拒绝 | 删除和发布仍被物理级联/DDL 顺序绑定，规则不一致 |
| 所有关系使用 FK 与级联 | 拒绝 | 无法表达历史保留、停用、归档、补偿和跨服务边界 |
| 仅靠应用代码、不设数据库约束 | 拒绝 | 不能抵御并发唯一冲突、状态非法值和漏校验 |
| 无物理 FK + 应用/约束/事务/测试/对账组合 | 接受 | 边界清晰，并能按领域生命周期治理完整性 |

## Consequences

正面后果是服务和 Schema 边界明确、删除与归档语义显式、迁移和大表维护不受隐式级联影响。代价是 Application 必须承担引用与删除保护，删除 FK 后需补齐索引、孤儿诊断和真实 PostgreSQL 测试；仅依赖代码 Review 不再足够。

## Exceptions

例外只允许官方协议或第三方框架无法改变的表，以及经 Accepted ADR 精确授权的基础设施协议表。例外必须精确到 Migration 文件和表，记录官方来源、不可移除原因、测试证据和退出条件；禁止包、模块、目录、文件通配符白名单。

当前精确例外仅为 Spring Authorization Server 官方协议存储结构；其具体 Migration/表必须由无外键门禁的精确清单逐文件登记。该例外不授权 IAM 自主业务表复制物理 FK。

## Migration Strategy

1. 盘点全部正式 Flyway Migration，区分自主业务表、官方协议表、Framework 表、测试 Fixture 和历史文件。
2. 不改写已发布 Migration；为现存业务 FK 新增更高版本 Migration。
3. 删除 FK 前执行孤儿查询并阻断非零结果；必要时先修复数据。
4. `ALTER TABLE ... DROP CONSTRAINT ...` 后保留或新增查询所需索引。
5. 在 Application 补引用存在性、删除/停用/归档保护、幂等和并发控制。
6. 采用 Expand → Migrate → Contract，评估锁、时长、磁盘、双版本兼容和恢复路径。

## Verification

- 静态门禁解析正式 Migration，忽略注释和字符串，阻断四类 FK 语法并验证精确例外。
- Schema 门禁验证命名、主键类型、时间/精度类型、注释、跨 Schema 和版本不可变性。
- PostgreSQL IT 验证空库迁移、升级迁移、引用不存在、删除保护、事务回滚、孤儿数为零和索引存在。
- 定期诊断查询或对账监控关键关系，Projection 延迟或权威服务不可用时按安全/业务语义 fail closed。

## Rollback / Compensation

移除 FK 的 Migration 不通过修改历史文件回滚。若上线后发现遗漏，先停止相关写入，使用孤儿诊断定位影响，以补偿脚本修复数据，并通过新的更高版本 Migration 增补约束或索引；不得静默恢复物理 FK。应用版本回滚必须保持数据库向后兼容，破坏性 Contract 仅在旧版本退出后执行。

## 与现有持久化规范的关系

本 ADR 是物理外键决策的最高项目依据；`persistence-data-modeling-standard.md` 继续定义 Schema、Entity、Mapper、Repository 与 Flyway 通则，`crud-application-standard.md` 定义应用完整性和事务，`multi-table-association-query-standard.md` 定义关联与查询，`database-schema-design-standard.md` 定义表结构。冲突时以本 ADR 的“业务表全面禁止物理 FK”决定为准。
