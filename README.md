# MOM Platform

面向新能源材料制造场景的工业 MOM 平台后端仓库。

> 当前状态：**V1 技术骨架与架构准备阶段**。仓库已经建立 Maven 模块骨架和文档约束，但尚未完成可用于生产的业务功能。

## 项目目标

V1 以锂电池电解液为主演示产品，完成以下最小完整闭环：

```text
供应商送货
→ 原料检验
→ 原料入库
→ 生产计划与执行
→ PCS 设备协同
→ 成品检验与放行
→ WCS 自动入库
→ 客户发运
→ 客诉与批次追溯
```

## 技术基线

- JDK 25。
- Spring Boot 4.1.x。
- Spring Framework 7.x。
- Spring Cloud 2025.1.x。
- Spring Cloud Alibaba 2025.1.x。
- PostgreSQL、Redis、RocketMQ、Nacos、Seata。
- OpenTelemetry、Tempo、Prometheus、Loki、Grafana。

具体版本由根 `pom.xml` 和 `mom-dependencies` 锁定。

## 模块

- `mom-dependencies`：统一依赖版本清单。
- `mom-framework`：平台级基础能力，不包含 MOM 业务领域。
- `mom-gateway`：统一入口、认证、Redis 限流、审计与上下文传递。
- `mom-iam-platform`：OAuth2.1/OIDC、用户、Client、角色和权限。
- `mom-mdm-platform`：组织、工厂、物料、供应商、客户和版本索引。
- `mom-mes-platform`：工单、版本快照、投料、过程记录和报工。
- `mom-wms-platform`：仓库、库位、容器、库存、预占、流水和余额。
- `mom-qms-platform`：检验、放行、不合格处置、偏差和 CAPA。
- `mom-ems-platform`：能源采集与统计。
- `mom-eam-platform`：设备台账、点检、保养、维修和备件。
- `mom-integration-platform`：外部接口、Outbox/Inbox、重试、补偿和对账。
- `mom-traceability-platform`：批次谱系、影响分析和模拟召回。
- `mom-bootstrap-tests`：跨模块架构与启动验证。

每个核心领域平台按 `api / client / server` 分层：

- `api`：DTO、命令、查询、事件契约和枚举。
- `client`：Feign 客户端及调用适配。
- `server`：领域、应用、接口和基础设施实现。

## 文档

文档文件优先使用中文命名，目录保留英文以兼顾脚本和工具兼容性。

- [文档中心](docs/README.md)
- [产品范围](docs/requirements/产品范围.md)
- [V1 需求清单](docs/requirements/V1需求清单.md)
- [非功能需求](docs/requirements/非功能需求.md)
- [V1 路线图](docs/plans/V1路线图.md)
- [Phase 01：技术骨架计划](docs/plans/Phase-01-技术骨架计划.md)
- [系统上下文](docs/architecture/系统上下文.md)
- [领域边界](docs/architecture/领域边界.md)
- [ADR 索引](docs/adr/README.md)

## 构建要求

- JDK 25。
- Maven 3.9.9 或更高版本。

```bash
mvn -B -ntp clean verify
```

当前阶段的构建主要验证 Maven Reactor、模块边界和基础测试。中间件与业务服务启动方式将在 Phase 01 实现后补充。

## 当前实施阶段

当前优先完成：

1. JDK 25 + Spring Boot 4 兼容性矩阵。
2. Gateway、IAM、MDM、Integration 最小启动闭环。
3. PostgreSQL、Redis、Nacos、RocketMQ 和 Seata 集成验证。
4. OpenTelemetry + Tempo 链路追踪。
5. Gateway Redis 多实例限流。
6. Outbox/Inbox 与消息幂等基础能力。

在 Phase 01 验收通过前，不进入大规模业务 CRUD 开发。

## 相关仓库规划

- `pcs-platform`
- `wcs-platform`
- `mom-web`
- `mom-mobile`
- `erp-simulator`
- `mom-infra`

## 开源复用原则

- 标准基础设施和协议 SDK 通过正式依赖复用。
- Pig、Yudao Cloud、JetLinks 等项目只学习机制后重构。
- MOM 核心领域、库存一致性、批次谱系、Integration Hub 和 PCS/WCS 状态机必须自研。
- 新增第三方依赖必须更新 [开源来源登记](docs/open-source/source-origin.md) 和第三方声明。