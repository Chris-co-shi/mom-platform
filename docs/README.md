# MOM Platform 文档中心

本目录是 `mom-platform` 的需求、计划、架构、安全协议和架构决策权威入口。

> 当前代码收敛工作以 `fix/mini-auth` 为事实来源。认证与授权已经从旧的完整 IAM / Authorization Server 方案收敛为 Mini Auth V1：`mom-security` 基础链已完成，下一步进入 `mom-auth` 的 User / Role / Permission / Login / Token / Logout。

## 文档使用原则

1. 需求文档回答“系统必须提供什么能力”，不写具体框架实现。
2. 计划文档回答“按什么顺序实现、如何验收”。
3. 架构文档描述当前系统整体结构、边界和协作方式。
4. ADR 记录关键架构选择、候选方案、理由、后果与替代条件。
5. 代码与文档冲突时，必须先确认决策是否已变化，禁止静默偏离。
6. 同一事实只保留一个权威来源，其他文档使用链接引用。
7. 计划能力不得描述为已实现能力。
8. 历史 P1.5/P1.6 文档可以保留实施证据，但不得继续覆盖当前 Mini Auth V1 的认证运行时语义。

## 当前权威阶段

- Phase 01：基础技术骨架已完成。
- 当前代码收敛分支：`fix/mini-auth`。
- `mom-core`：CurrentActor V1 已收敛。
- `mom-data`：数据基础设施 V1 已收敛。
- `mom-security`：Redis Opaque Token、TokenStore、Introspector、Servlet Resource Server 自动配置已完成当前轮收敛。
- `mom-auth`：下一阶段，负责 User、Role、Permission、密码认证、Login、Token 签发和 Logout。
- Phase 02：业务垂直切片尚未因本次安全收敛自动启动。

## 当前认证协议权威

当前 V1 认证运行时的权威顺序：

1. [ADR-040：Mini Auth 与 Redis Opaque Token 认证基线](adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
2. [P1.5 认证与授权设计基线（Mini Auth V1）](security/P1.5-认证与授权设计基线.md)
3. [安全架构](architecture/安全架构.md)
4. [CurrentActor 与数据审计基础](architecture/CurrentActor与数据审计.md)

历史决策：

- [ADR-024：PC JSON 与 Mobile PKCE/OIDC 双通道](adr/ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md) 保留历史价值，但 V1 认证运行时已被 ADR-040 替代。
- [ADR-019：P1.5 认证与授权闭环](adr/ADR-019-P1.5认证与授权闭环.md) 保留历史设计与实施证据。

当前 V1 不使用 JWT、Refresh Token、Session、Spring Authorization Server、OIDC 或 OAuth Client 管理。

## Mini Auth V1

- [ADR-040：Mini Auth 与 Redis Opaque Token 认证基线](adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- [P1.5 认证与授权设计基线](security/P1.5-认证与授权设计基线.md)
- [安全架构](architecture/安全架构.md)
- [CurrentActor 与数据审计基础](architecture/CurrentActor与数据审计.md)

## 需求文档

- [产品范围](requirements/产品范围.md)
- [V1 需求清单](requirements/V1需求清单.md)
- [非功能需求](requirements/非功能需求.md)
- [领域术语表](requirements/领域术语表.md)
- [非 V1 范围](requirements/非V1范围.md)

## 实施计划与历史工程报告

- [计划索引](plans/README.md)
- [V1 路线图](plans/V1路线图.md)
- [Phase 01：技术骨架计划](plans/Phase-01-技术骨架计划.md)
- [Phase 01：完成报告](plans/Phase-01-完成报告.md)
- [P1.5：认证与授权闭环实施计划](plans/P1.5-认证与授权闭环计划.md)
- [P1.5：实施进度](plans/P1.5-实施进度.md)
- [P1.6：IAM 与 System 平台治理计划](plans/P1.6-IAM与System平台治理计划.md)
- [P1.6：实施进度](plans/P1.6-实施进度.md)
- [V1 垂直切片计划](plans/V1垂直切片计划.md)

P1.5/P1.6 的旧认证实施报告继续保留为历史资料；若与 ADR-040 或当前安全基线冲突，以 ADR-040 为准。

## 技术架构

- [CRUD 与应用服务规范](engineering/standards/crud-application-standard.md)
- [多表关联与查询规范](engineering/standards/multi-table-association-query-standard.md)
- [数据库表结构设计规范](engineering/standards/database-schema-design-standard.md)
- [服务端 Package 与目录架构规范](engineering/standards/package-directory-architecture-standard.md)
- [系统上下文](architecture/系统上下文.md)
- [服务与容器架构](architecture/服务与容器架构.md)
- [模块边界](architecture/module-boundaries.md)
- [领域边界](architecture/领域边界.md)
- [数据架构](architecture/数据架构.md)
- [CurrentActor 与数据审计基础](architecture/CurrentActor与数据审计.md)
- [IAM 数据库与领域模型](architecture/IAM数据库与领域模型.md)
- [集成架构](architecture/集成架构.md)
- [安全架构](architecture/安全架构.md)
- [可观测性架构](architecture/可观测性架构.md)
- [部署架构](architecture/部署架构.md)

## 架构决策

- [ADR 索引](adr/README.md)
- [ADR 模板](adr/ADR-模板.md)
- [ADR-040：Mini Auth 与 Redis Opaque Token 认证基线（Accepted）](adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- [ADR-025：IAM、System、MDM、WMS、EAM 数据所有权边界](adr/ADR-025-IAM-System-MDM-WMS-EAM数据所有权边界.md)
- [ADR-026：MOM 业务表禁止物理外键与关联完整性策略](adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)
- [ADR-027：服务端包结构与基础设施适配器分层](adr/ADR-027-服务端包结构与基础设施适配器分层.md)
- [ADR-028：MyBatis-Plus Repository 抽象与领域仓储边界](adr/ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md)
- [ADR-029：IAM Admin 分层与领域模型边界](adr/ADR-029-IAM-Admin分层与领域模型边界.md)
- [ADR-030：System 应用目录、导航发布与 IAM 权限引用边界](adr/ADR-030-System应用目录导航发布与IAM权限引用边界.md)

## 安全与凭据

- [P1.6 S00：配置凭据暴露与轮换清单](security/P1.6-S00-配置凭据暴露与轮换清单.md)
- [IAM 内置管理员初始化](security/IAM内置管理员初始化.md)
- [IAM 内置管理员恢复](security/IAM内置管理员恢复.md)

这些旧 IAM 运维文档是否继续适用于 Mini Auth，需要在 `mom-auth` 实现阶段逐项复核，不得直接假设全部有效。

## 垂直切片

- [VS-01：原料到成品自动入库](vertical-slices/VS-01-material-to-finished-goods.md)

## 开源合规

- [开源来源登记](open-source/source-origin.md)
- [第三方声明](../THIRD-PARTY-NOTICES.md)

## 文档维护

- [文档维护约定](文档维护约定.md)

当前规则仍要求文档修改通过固定文档分支完成；架构决策变化通过新 ADR 记录，不静默覆盖历史 ADR。
