<div align="center">

# MOM Platform

### 面向新能源材料制造的开源工业 MOM 平台

从供应商送货、质量检验、生产执行、设备协同，到自动仓储、客户发运与批次追溯，构建一套可部署、可演示、可持续演进的工业软件底座。

<p>
  <a href="https://github.com/Chris-co-shi/mom-platform/actions/workflows/ci.yml">
    <img alt="CI" src="https://github.com/Chris-co-shi/mom-platform/actions/workflows/ci.yml/badge.svg?branch=main">
  </a>
  <img alt="Java" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white">
  <img alt="Spring Cloud" src="https://img.shields.io/badge/Spring%20Cloud-2025.1-6DB33F?logo=spring&logoColor=white">
  <img alt="Status" src="https://img.shields.io/badge/Status-P1.5%20Security%20Baseline-2563EB">
</p>

[文档中心](docs/README.md) · [P1.5 设计基线](docs/security/P1.5-认证与授权设计基线.md) · [P1.5 实施计划](docs/plans/P1.5-认证与授权闭环计划.md) · [V1 路线图](docs/plans/V1路线图.md) · [ADR](docs/adr/README.md)

</div>

---

> [!IMPORTANT]
> Phase 01 已完成 **V1 基础技术骨架**，但完整 IAM、Authorization Server、Gateway/业务服务认证授权、用户授权 Session、Web/Mobile 登录仍未实现。当前阶段为 **P1.5：认证与授权闭环，S00 设计基线**。

## 🌟 项目愿景

`MOM Platform` 面向新能源材料制造场景，以锂电池电解液为主演示产品，重点验证：

- 跨 MES、WMS、QMS 的业务边界与一致性。
- 原料、半成品、成品之间的多对多批次谱系。
- 库存事实流水、实时余额、预占与锁定。
- PCS/WCS 异步命令、状态机、故障恢复和人工接管。
- 外部系统接入、幂等、补偿、对账和链路追踪。
- 统一认证、授权、Factory/Party Scope 与安全审计。
- 三节点 k3s 下的部署、扩缩容、滚动升级和故障演练。

### V1 端到端业务闭环

```text
供应商送货
    ↓
原料检验 ──→ 不合格处置
    ↓
原料入库
    ↓
生产计划与工单
    ↓
PCS 投料 / 混合 / 灌装
    ↓
成品检验与放行
    ↓
WCS 自动入库
    ↓
客户发运
    ↓
客诉 / 正反向追溯 / 模拟召回
```

## 🧭 系统全景

```mermaid
flowchart LR
    Internal[INTERNAL / MOM Admin / PDA]
    Supplier[SUPPLIER / Supplier Portal]
    Customer[CUSTOMER / Customer Portal]
    ERP[ERP Simulator]

    Gateway[MOM API Gateway]

    subgraph MOM[MOM Platform]
        IAM[IAM / OAuth2 / OIDC]
        MDM[MDM]
        MES[MES]
        WMS[WMS]
        QMS[QMS]
        INT[Integration Hub]
        TRACE[Traceability]
    end

    Internal --> Gateway
    Supplier --> Gateway
    Customer --> Gateway
    ERP --> Gateway
    Gateway --> IAM
    Gateway --> MDM
    Gateway --> MES
    Gateway --> WMS
    Gateway --> QMS
    Gateway --> INT
    MES --> TRACE
    WMS --> TRACE
    QMS --> TRACE
```

## 🔐 P1.5 认证与授权

当前权威结论：

- 用户类型：`INTERNAL`、`SUPPLIER`、`CUSTOMER`。
- Public Client：`mom-admin-web`、`mom-supplier-web`、`mom-customer-web`、`mom-mobile-pda`。
- Authorization Code + PKCE S256 + OpenID Connect。
- 不建设 BFF；Gateway API 使用 Bearer Access Token。
- Access Token 为 10 分钟 JWT；Refresh Token 为 Opaque Token。
- Web Token 只存在内存；Mobile Refresh Token 使用 Android 安全存储。
- RBAC 使用 User → Role → Permission；P1.5 只实现 Factory Scope 与 Party Scope。
- Gateway 负责协议与粗粒度入口安全，业务服务负责最终授权。

完整协议见 [P1.5 认证与授权设计基线](docs/security/P1.5-认证与授权设计基线.md)。

## 🧩 核心能力

| 能力域 | V1 关注点 | 权威模块 |
|---|---|---|
| 身份与权限 | OAuth/OIDC、PKCE、用户、角色、权限、Factory/Party Scope、Session | `mom-iam-platform` |
| 主数据 | 集团、工厂、物料、供应商、客户、版本索引 | `mom-mdm-platform` |
| 生产执行 | 工单、版本快照、投料、过程记录、报工 | `mom-mes-platform` |
| 仓储库存 | 库位、容器、批次、预占、流水、余额、对账 | `mom-wms-platform` |
| 质量管理 | 检验、放行、不合格处置、偏差、CAPA | `mom-qms-platform` |
| 系统集成 | 外部接口、Outbox/Inbox、重试、补偿、对账 | `mom-integration-platform` |
| 批次追溯 | 正向追溯、反向追溯、影响分析、模拟召回 | `mom-traceability-platform` |
| 平台治理 | Gateway、Redis 限流、审计、链路追踪 | `mom-gateway` / `mom-framework` |

## 🛠️ 技术基线

| 层次 | 技术选型 |
|---|---|
| Java 运行时 | JDK 25 |
| 应用框架 | Spring Boot 4.1.x、Spring Framework 7.x |
| 微服务体系 | Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.x |
| 身份认证 | Spring Authorization Server、OAuth 2.0/OIDC、PKCE（P1.5 实现中） |
| 数据存储 | PostgreSQL，按服务独立 Schema |
| 缓存与限流 | Redis、分布式令牌桶 |
| 消息与一致性 | RocketMQ、Outbox/Inbox、幂等、Seata |
| 注册与配置 | Nacos |
| 可观测性 | Micrometer、OpenTelemetry、Tempo、Prometheus、Loki、Grafana |
| 部署环境 | 三节点 k3s |
| 测试体系 | JUnit 5、Testcontainers、ArchUnit |

> 具体版本以根目录 `pom.xml` 和 `mom-dependencies` 为唯一权威来源。

## 🏗️ 仓库结构

```text
mom-platform
├── mom-dependencies
├── mom-framework
│   ├── mom-core
│   ├── mom-security
│   ├── mom-data
│   └── ...
├── mom-gateway
├── mom-iam-platform
├── mom-mdm-platform
├── mom-mes-platform
├── mom-wms-platform
├── mom-qms-platform
├── mom-ems-platform
├── mom-eam-platform
├── mom-integration-platform
├── mom-traceability-platform
└── mom-bootstrap-tests
```

每个核心领域平台统一分为 `*-api`、`*-client`、`*-server`。

### 强制依赖规则

- `*-api` 不暴露数据库 Entity、Mapper 或 Repository。
- `*-server` 禁止依赖其他领域的 `*-server`。
- 跨领域同步调用通过 `*-client`，异步协作通过领域事件。
- PostgreSQL 每服务独立 Schema，禁止跨 Schema JOIN 和跨域写入。
- `mom-framework` 不得包含 MES、WMS、QMS 等业务规则。
- `mom-data` 不得依赖 `mom-security`。
- PCS 与 WCS 保持独立仓库和独立部署边界。

## 🚀 快速开始

### 环境要求

- JDK 25
- Maven 3.9.9+
- Git

### 验证 Maven Reactor

```bash
mvn -B -ntp clean verify
```

当前命令验证构建、模块依赖、基础测试、兼容性与架构门禁；不代表 P1.5 安全 E2E 已完成。

## 📚 文档导航

| 分类 | 入口 | 说明 |
|---|---|---|
| 总览 | [文档中心](docs/README.md) | 全部文档导航与维护规则 |
| 安全 | [P1.5 设计基线](docs/security/P1.5-认证与授权设计基线.md) | 跨仓库认证授权权威协议 |
| 计划 | [P1.5 实施计划](docs/plans/P1.5-认证与授权闭环计划.md) | S00～S12、职责矩阵与 DoD |
| 计划 | [V1 路线图](docs/plans/V1路线图.md) | V1 阶段和交付目标 |
| 计划 | [Phase 01 技术骨架](docs/plans/Phase-01-技术骨架计划.md) | 已完成基础与明确边界 |
| 架构 | [安全架构](docs/architecture/安全架构.md) | 安全架构导航 |
| 架构 | [系统上下文](docs/architecture/系统上下文.md) | MOM 与用户、ERP、PCS、WCS 的关系 |
| 决策 | [ADR 索引](docs/adr/README.md) | 所有关键架构决策及状态 |

## 🗺️ 当前路线图

| 阶段 | 目标 | 状态 |
|---|---|---|
| Phase 01 | JDK 25 + Boot 4 基础技术骨架与观测闭环 | ✅ 基础完成 |
| P1.5 | 认证与授权闭环 | 🚧 S00 设计基线 |
| Phase 02 | 供应商送货、来料检验、PDA 入库、库存闭环 | ⏳ 等待安全门禁 |
| Phase 03 | 生产工单、PCS 协同、半成品与成品批次 | ⏳ 计划中 |
| Phase 04 | 成品放行、WCS 入库、客户发运、追溯和召回 | ⏳ 计划中 |

## 🔗 MOM 项目仓库族

| 仓库 | 职责 |
|---|---|
| `mom-platform` | MOM 后端、安全协议与通用 Framework |
| `mom-web` | 管理端、供应商门户、客户门户及 Web Runtime |
| `mom-mobile` | Android PDA、扫码、Mobile Auth 与离线命令 |
| `pcs-platform` | 生产设备协同、协议适配和状态机 |
| `wcs-platform` | 自动仓储调度、运输任务和设备恢复 |
| `erp-simulator` | ERP/SAP 接口与异常场景模拟 |
| `mom-infra` | k3s、中间件、可观测性和部署脚本 |

## 🧠 架构原则

1. **领域优先**：业务边界不能由数据库表或通用 CRUD 框架反向定义。
2. **服务端授权**：前端体验控制、请求 Header 和 Gateway 粗粒度判断不能替代业务服务最终授权。
3. **事实优先**：库存、批次和质量结果以不可重复的业务事实为基础。
4. **异步可恢复**：长流程默认使用消息、幂等、重试、补偿和对账。
5. **集成有边界**：外部系统统一通过 Gateway 与 Integration Hub 接入。
6. **可观测优先**：HTTP、Feign、MQ、任务和设备命令必须可以关联追踪。
7. **原型先行**：Web 与 PDA 开发前必须完成流程、状态和原型设计。
8. **开源合规**：标准依赖直接使用，机制学习后重构，领域内核自主实现。

---

<div align="center">

**MOM Platform — 让工业业务边界、系统集成、安全授权与故障恢复成为可复用的工程能力。**

</div>
