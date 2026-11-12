# ADR-046 PostgreSQL 与 MySQL 部署兼容

- 状态：Accepted
- 日期：2026-11-09
- 关联：[ADR-020](ADR-020-PostgreSQL物理Schema命名空间.md)、[持久化规范](../engineering/standards/persistence-data-modeling-standard.md)

同一服务产物支持 PostgreSQL 17 与 MySQL 8.4，默认 PostgreSQL。一次部署为所有服务选择同一种数据库。每个服务使用一个数据源及本地事务。PostgreSQL 在 `mom_platform` 内按服务分 Schema，MySQL 在同一实例按服务分 Database。两种部署都以 `mom_<owner>` 为默认命名，保持原有服务数据归属和权限边界。

## 迁移基线

PostgreSQL 的现有版本历史继续生效。MySQL 基线来自在空 PostgreSQL 17 实例执行全部已发布生产迁移后的有效结构与必要种子。

| Owner | PostgreSQL 基线末版本 | MySQL 基线 |
|---|---:|---:|
| Auth | 25 | 2 |
| System | 29 | 2 |
| MDM | 134 | 2 |
| QMS | 6 | 2 |
| WMS | 17 | 2 |
| Integration | 101 | 1（空结构基线） |

MySQL V1 建立有效结构，V2 写入必要种子。后续结构或种子变更在同一 PR 提供两库版本迁移，在 PR 描述及本服务迁移说明记录版本对应。每个目录分别递增并保留已发布内容。

## 持久化差异

- 保留 PostgreSQL 专用 SQL，在 Mapper 方言和现有 JDBC 存储内选择 MySQL 实现。MySQL 使用 InnoDB、`utf8mb4_0900_bin`、UTC `datetime(6)`、`READ COMMITTED` 和等精度 Decimal。
- WMS 以持久锁行承接原事务锁键。锁行只提供互斥，在提交或回滚时释放行锁。库存余额和分录仍在业务事务内一起写入。
- WMS 的 `InventoryCountScopeTypeHandler` 仅在 MyBatis 边界映射 PG 数组与 MySQL JSON。`WmsMysqlDuplicateKey` 仅解析驱动异常以识别目标唯一键。两类精确例外允许使用所需的 `java.sql` 类型，由双库库存集成测试验证，待数据库驱动映射和唯一键元数据可由现有框架直接提供时移除。
- 编码规则和 Outbox/Inbox 的数据库写入沿用宿主 DataSource 和本地事务。Outbox 允许新实例领取已过期的 CLAIMED 记录，并以租约所有者校验挡住旧实例更新。

部署参数和故障恢复步骤见 [数据库部署说明](../engineering/database-vendors.md)。
