# MOM Platform 文档中心

本目录是 `mom-platform` 的需求、计划、架构、安全协议和架构决策权威入口。

> 文档变更使用与具体 Work/Slice 对应的任务分支。P1.5 S02 使用 `feat/p15-s02-iam-schema-domain`；历史分支不再是后续变更的唯一分支。

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
- P1.5：S00、S01 已合并；S02 IAM Schema 与领域模型已完成并进入 PR Review。
- Phase 02：业务垂直切片仍为计划阶段；安全闭环完成前不得标记为可安全上线。

## P1.5 认证与授权

- [P1.5 认证与授权设计基线](security/P1.5-认证与授权设计基线.md)
- [P1.5 认证与授权闭环实施计划](plans/P1.5-认证与授权闭环计划.md)
- [P1.5 实施进度](plans/P1.5-实施进度.md)
- [CurrentActor 与数据审计基础](architecture/CurrentActor与数据审计.md)
- [IAM 数据库与领域模型](architecture/IAM数据库与领域模型.md)
- [安全架构](architecture/安全架构.md)
- [ADR-019：P1.5 认证与授权闭环](adr/ADR-019-P1.5认证与授权闭环.md)

## 需求文档

- [产品范围](requirements/产品范围.md)
- [V1 需求清单](requirements/V1需求清单.md)
- [非功能需求](requirements/非功能需求.md)
- [领域术语表](requirements/领域术语表.md)
- [非 V1 范围](requirements/非V1范围.md)

## 实施计划

- [V1 路线图](plans/V1路线图.md)
- [Phase 01：技术骨架计划](plans/Phase-01-技术骨架计划.md)
- [Phase 01：完成报告](plans/Phase-01-完成报告.md)
- [P1.5：认证与授权闭环实施计划](plans/P1.5-认证与授权闭环计划.md)
- [P1.5：实施进度](plans/P1.5-实施进度.md)
- [V1 垂直切片计划](plans/V1垂直切片计划.md)

## 技术架构

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

## 垂直切片

- [VS-01：原料到成品自动入库](vertical-slices/VS-01-material-to-finished-goods.md)

## 开源合规

- [开源来源登记](open-source/source-origin.md)
- [第三方声明](../THIRD-PARTY-NOTICES.md)
