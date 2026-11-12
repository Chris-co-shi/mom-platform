# PostgreSQL 与 MySQL 部署

每个含 Flyway 的服务产物包含两种 JDBC 驱动及 Flyway 数据库模块。启动时设置 `MOM_DATABASE_VENDOR=postgresql` 或 `MOM_DATABASE_VENDOR=mysql`。六个服务使用一致的选择值，变更后重启服务。

PostgreSQL 使用原有 `POSTGRES_*` 变量和 `db/migration/<owner>`。MySQL 使用 `db/migration-mysql/<owner>`。首次启用 MySQL 初始化全新数据库及种子。已有业务数据的搬迁需要独立执行计划。

## MySQL 配置样例

以 WMS 为例。其他服务用自己的 `owner` 和账号。

```dotenv
MOM_DATABASE_VENDOR=mysql
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=mom_wms
MYSQL_USERNAME=mom_wms
# MYSQL_PASSWORD 由部署环境的秘密管理注入
```

可用 `MYSQL_URL` 指定完整 JDBC 地址。其数据库与 `MYSQL_DATABASE` 应一致。用 `MYSQL_MINIMUM_IDLE`、`MYSQL_MAXIMUM_POOL_SIZE` 等变量调整连接池。应用和 Flyway 使用同一数据源，连接池固定服务的默认数据库、UTC 会话和 `READ COMMITTED`。

由 DBA 在 MySQL 8.4 实例预建 `mom_auth`、`mom_system`、`mom_mdm`、`mom_qms`、`mom_wms`、`mom_integration`，字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_bin`。服务账号只授权自身库。运行初始迁移至少需要本库 SELECT、INSERT、UPDATE、DELETE、CREATE、ALTER、DROP、INDEX、TRIGGER 权限。开启二进制日志的实例由 DBA 配置 `log_bin_trust_function_creators=ON`，以便 System 和 WMS 的局部账号创建业务保护触发器。[MySQL CREATE TRIGGER](https://dev.mysql.com/doc/refman/8.4/en/create-trigger.html)

## Flyway 失败恢复

1. 停止失败服务的发布或重启循环，记录厂商、目标库、失败版本和 SQL 错误，备份当前库。
2. 检查 `flyway_schema_history`、已创建对象和已写数据。MySQL DDL 可能隐式提交，单个版本失败后也要检查部分成功。
3. 对经确认无业务数据的初装库，使用 DBA 流程重建目标库后重跑。已有数据的库先通过备份恢复或经审查的补偿 SQL 恢复到可重试状态。
4. 修复残留并复核 Flyway 记录后，才允许通过运维流程执行 `repair`。校验迁移历史和约束，重启并确认业务冒烟通过。

## 验证

```bash
CODEX_VERIFY_LEVEL=verify bash scripts/codex-verify-changed.sh
bash .github/scripts/database-vendors-smoke.sh
```

冒烟脚本使用构建好的 `*-exec.jar` 和临时 Docker 数据库。它验证每个服务在两个厂商上的首次启动，以及 MySQL 重启。
