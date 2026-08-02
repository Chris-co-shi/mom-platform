# MOM Platform 文档中心

本目录是 `mom-platform` 的需求、计划、架构、安全协议和架构决策权威入口。

> P1.5 S00～S12 已完成并合并。P1.6 S00～S17 已完成，S15-F 已完成探针退出；S16-A 已完成 MyBatis-Plus Repository 抽象治理，S16-B 已完成 IAM Admin 分层与领域模型治理；ADR-023/025/026/027/028/029/030 保持 Accepted。S18 尚未开始。

## 文档使用原则

1. 需求文档回答“系统必须提供什么能力”，不写具体框架实现。
2. 计划文档回答“按什么顺序实现、如何验收”。
3. 架构文档描述当前系统整体结构、边界和协作方式。
4. ADR 记录关键架构选择、候选方案、理由、后果与替代条件。
5. 代码与文档冲突时，必须先确认决策是否已变化，禁止静默偏离。
6. 同一事实只保留一个权威来源，其他文档使用链接引用。
7. 计划能力不得描述为已实现能力。

## 当前权威阶段

- Phase 01：基础技术骨架已完成。
- P1.5：**Completed / Merged**，S00～S12 全部完成。
- P1.6：S00 **Completed under accepted local-development risk**，S01～S16 **Completed**，S15-F **Completed**，S16-A **Completed**，S16-B **Completed**，S17 **Completed with Deferred items**，S18～S19 Not Started。
- 当前 Slice：S17 已完成 Application Catalog、WEB/MOBILE Navigation Draft、不可变发布快照、Rollback、Runtime 权限过滤、ETag/304、Flyway V8 与 IAM V10 Catalog Permission；Permission 发布期权威校验、缓存/通知和 Web/Mobile 正式接入精确 Deferred。S18 未启动。
- S19-A：Mobile 服务端 Logout、正式 Redirect/App Link、mom-mobile 完整检查、L4/L6 和 mom-infra 环境证据统一在最终集成阶段回收；当前仍为 Open/Deferred。
- Phase 02：**Not Started**，业务垂直切片未因 P1.6 治理而启动。
- Android Keystore、HTTPS App Link 与真机强杀恢复：S19-A 与 Phase 02 Mobile 正式联调前置验收项。

## 当前认证协议权威

- [ADR-024：PC JSON 与 Mobile PKCE/OIDC 双通道（Accepted）](adr/ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md) 是当前认证协议、内部认证权威和 Token 签发职责的最高决策来源。
- [ADR-019：P1.5 认证与授权闭环](adr/ADR-019-P1.5认证与授权闭环.md) 已被 ADR-024 替代，继续保留历史设计与实施价值。
- [P1.5 认证与授权设计基线](security/P1.5-认证与授权设计基线.md) 继续保留 P1.5 历史实施证据；与 PC Runtime 冲突的统一 PKCE、Web Token 恢复和页面跳转条款以 ADR-024 为准。

## P1.5 认证与授权

- [P1.5 认证与授权设计基线](security/P1.5-认证与授权设计基线.md)
- [P1.5 认证与授权闭环实施计划](plans/P1.5-认证与授权闭环计划.md)
- [P1.5 实施进度](plans/P1.5-实施进度.md)
- [CurrentActor 与数据审计基础](architecture/CurrentActor与数据审计.md)
- [IAM 数据库与领域模型](architecture/IAM数据库与领域模型.md)
- [安全架构](architecture/安全架构.md)
- [ADR-019：P1.5 认证与授权闭环（Superseded by ADR-024）](adr/ADR-019-P1.5认证与授权闭环.md)
- [ADR-024：PC JSON 与 Mobile PKCE/OIDC 双通道（Accepted）](adr/ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md)

## 需求文档

- [产品范围](requirements/产品范围.md)
- [V1 需求清单](requirements/V1需求清单.md)
- [非功能需求](requirements/非功能需求.md)
- [领域术语表](requirements/领域术语表.md)
- [非 V1 范围](requirements/非V1范围.md)

## 实施计划

- [计划索引](plans/README.md)
- [P1.6：IAM 与 System 平台治理计划](plans/P1.6-IAM与System平台治理计划.md)
- [P1.6：实施进度](plans/P1.6-实施进度.md)
- [P1.6：工程规范覆盖与缺口清单](engineering/P1.6-工程规范覆盖与缺口清单.md)
- [P1.6 S02：持久化历史例外清单](engineering/P1.6-S02-持久化历史例外清单.md)
- [P1.6 S03：安全配置历史例外清单](engineering/P1.6-S03-安全配置历史例外清单.md)
- [P1.6 S04：测试与 CI 历史例外清单](engineering/P1.6-S04-测试与CI历史例外清单.md)
- [P1.6 S05：国际化与个性化现状清单](engineering/P1.6-S05-国际化与个性化现状清单.md)
- [P1.6 S06：IAM 端点、调用方与职责审计](engineering/P1.6-S06-IAM端点调用方与职责审计.md)
- [P1.6 S08：IAM 配置与第一方认证核心重构报告](engineering/P1.6-S08-IAM配置与第一方认证核心重构报告.md)
- [P1.6 S09：IAM 管理、错误模型与 Token Adapter 整理报告](engineering/P1.6-S09-IAM管理错误模型与TokenAdapter整理报告.md)
- [P1.6 S10：IAM 全协议与跨仓库安全回归封板报告](engineering/P1.6-S10-IAM全协议与跨仓库安全回归封板报告.md)
- [P1.6 S11：数据所有权现状与迁移边界报告](engineering/P1.6-S11-数据所有权现状与迁移边界报告.md)
- [P1.6 S12：System 平台技术骨架报告](engineering/P1.6-S12-System平台技术骨架报告.md)
- [P1.6 S13：System 类型化参数能力报告](engineering/P1.6-S13-System类型化参数能力报告.md)
- [P1.6 S14：System 非权威通用字典能力报告](engineering/P1.6-S14-System非权威通用字典能力报告.md)
- [P1.6 S15-A：动态国际化真实调用方证据审计](engineering/P1.6-S15-A-动态国际化真实调用方证据审计.md)
- [P1.6 S15-B：System 动态国际化后端能力报告](engineering/P1.6-S15-B-System动态国际化后端能力报告.md)
- [P1.6 S15-D：CRUD、多表关联、表结构与无外键全仓审计](engineering/P1.6-S15-D-CRUD多表关联表结构与无外键全仓审计报告.md)
- [P1.6 S15-E：服务端 Package 结构与目录治理审计](engineering/P1.6-S15-E-服务端包结构与目录治理审计报告.md)
- [P1.6 S15-F：MDM 技术探针退出与验证归位](engineering/P1.6-S15-F-MDM技术探针退出与验证归位报告.md)
- [P1.6 S16：System 用户偏好与视图设置能力报告](engineering/P1.6-S16-System用户偏好与视图设置能力报告.md)
- [P1.6 S16-A：MyBatis-Plus Repository 抽象治理报告](engineering/P1.6-S16-A-MyBatis-Plus-Repository抽象治理报告.md)
- [P1.6 S16-B：IAM Admin 分层与领域模型治理报告](engineering/P1.6-S16-B-IAM-Admin分层与领域模型治理报告.md)
- [P1.6 S17：System 应用目录、导航与权限引用能力报告](engineering/P1.6-S17-System应用目录导航与权限引用能力报告.md)
- [P1.6 S00：配置凭据暴露与轮换清单](security/P1.6-S00-配置凭据暴露与轮换清单.md)
- [V1 路线图](plans/V1路线图.md)
- [Phase 01：技术骨架计划](plans/Phase-01-技术骨架计划.md)
- [Phase 01：完成报告](plans/Phase-01-完成报告.md)
- [P1.5：认证与授权闭环实施计划](plans/P1.5-认证与授权闭环计划.md)
- [P1.5：实施进度](plans/P1.5-实施进度.md)
- [V1 垂直切片计划](plans/V1垂直切片计划.md)

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
- [ADR-025：IAM、System、MDM、WMS、EAM 数据所有权边界（Accepted）](adr/ADR-025-IAM-System-MDM-WMS-EAM数据所有权边界.md)
- [ADR-026：MOM 业务表禁止物理外键与关联完整性策略（Accepted）](adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)
- [ADR-027：服务端包结构与基础设施适配器分层（Accepted）](adr/ADR-027-服务端包结构与基础设施适配器分层.md)
- [ADR-028：MyBatis-Plus Repository 抽象与领域仓储边界（Accepted）](adr/ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md)
- [ADR-029：IAM Admin 分层与领域模型边界（Accepted）](adr/ADR-029-IAM-Admin分层与领域模型边界.md)
- [ADR-030：System 应用目录、导航发布与 IAM 权限引用边界（Accepted）](adr/ADR-030-System应用目录导航发布与IAM权限引用边界.md)

## 垂直切片

- [VS-01：原料到成品自动入库](vertical-slices/VS-01-material-to-finished-goods.md)

## 开源合规

- [开源来源登记](open-source/source-origin.md)
- [第三方声明](../THIRD-PARTY-NOTICES.md)