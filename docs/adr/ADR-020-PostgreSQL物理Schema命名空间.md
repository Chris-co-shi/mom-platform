# ADR-020：PostgreSQL 物理 Schema 命名空间

- 状态：Accepted
- 日期：2026-07-28
- 关联决策：[ADR-004](ADR-004-PostgreSQL按服务隔离Schema.md)、[ADR-014](ADR-014-单服务单数据源与Hikari连接池治理.md)

## 1. 背景

ADR-004 已冻结“共享 PostgreSQL、每服务独立 Schema”的数据所有权边界，但其示例使用
`iam`、`mdm`、`integration`。当前 IAM、MDM 和 Integration 的运行配置、Flyway 默认 Schema
分别是 `mom_iam`、`mom_mdm`、`mom_integration`。继续保留两套示例会使新服务、授权脚本和
运维手册产生歧义。

## 2. 决策

V1 共享数据库名为 `mom_platform`。每个服务的物理 Schema 使用
`mom_<bounded-context>`：

| 服务 | 物理 Schema |
|---|---|
| IAM | `mom_iam` |
| MDM | `mom_mdm` |
| MES | `mom_mes` |
| WMS | `mom_wms` |
| QMS | `mom_qms` |
| EMS | `mom_ems` |
| EAM | `mom_eam` |
| Integration | `mom_integration` |
| Traceability | `mom_traceability` |
| 后续 System | `mom_system` |

该裁决只补充并更新 ADR-004 的物理命名示例，不改变其核心决策：一个共享 PostgreSQL 集群、
每服务独立 Schema、每服务只读写自身 Schema、禁止跨 Schema JOIN/外键/写入、Flyway 由各服务
独立管理。

## 3. 理由

- 与已部署的 IAM、MDM、Integration 配置和迁移保持一致；
- 通用名称不再与同一集群中的其他应用冲突；
- Schema 名称直接表达 MOM 平台归属；
- 无需数据库重命名或兼容迁移。

## 4. 后果与边界

- 新服务首次创建 Schema 时直接采用本 ADR 的名称。
- 服务账号只获得自身 Schema 所需权限；只读账号或 View 不得静默绕过领域边界。
- 跨领域查询仍使用 Query API、本地事件投影或明确查询服务。
- 当前实现不执行 Schema Rename，不新增或修改 Flyway Migration，也不修改运行配置。
- 若未来拆分数据库，必须新增 ADR；逻辑数据所有权不因物理部署变化而改变。

## 5. 验证

- 核对 DataSource `currentSchema`、Flyway `default-schema/schemas` 与迁移路径。
- 静态门禁拒绝生产 Migration 引用其他 MOM 服务 Schema。
- 部署验收验证服务账号无法读写其他 Schema。
