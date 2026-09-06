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
  <img alt="Status" src="https://img.shields.io/badge/Status-Mini%20Auth%20V1-0969DA">
</p>

[文档中心](docs/README.md) · [Mini Auth V1 基线](docs/security/P1.5-认证与授权设计基线.md) · [ADR-040](docs/adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) · [ADR-041](docs/adr/ADR-041-Mini-Auth简化三层包结构.md) · [V1 路线图](docs/plans/V1路线图.md)

</div>

---

> [!IMPORTANT]
> 当前项目正在以“先恢复可控性，再逐步演进”为原则收敛第一版架构。`mom-security` 的 Redis Opaque Token / Resource Server 基础链已经完成；Gateway 已收敛为 Bearer 边缘检查、Header 清洗、路由与 Gateway 本地限流；`mom-openfeign` 已收敛为 MOM 内部同步 RPC 的 Correlation ID 与原始 Bearer 传播基础设施；`mom-auth-platform` 已建立模块、Schema、Flyway 核心表和管理员初始化脚本，下一步进入 User / Role / Permission / Login / Token / Logout 业务代码。

## 🌟 项目愿景

`MOM Platform` 面向新能源材料制造场景，以锂电池电解液为主演示产品，重点验证：

- 跨 MES、WMS、QMS 的业务边界与一致性。
- 原料、半成品、成品之间的多对多批次谱系。
- 库存事实流水、实时余额、预占与锁定。
- PCS/WCS 异步命令、状态机、故障恢复和人工接管。
- 外部系统接入、幂等、补偿、对账和链路追踪。
- 简洁、可解释的认证授权与最终业务授权边界。
- 三节点 k3s 下的部署、扩缩容、滚动升级和故障演练。

## 🔐 Mini Auth V1

当前权威结论：

- 最小授权模型：`User → Role → Permission`。
- 用户通过第一方用户名密码登录。
- Access Token 使用高熵随机 Opaque Token，不使用 JWT。
- Token 认证快照存储在 Redis。
- Redis Key 使用 `mom:token:{SHA-256(rawToken)}`。
- Token Principal 只包含 `userId`、`authorities`、`expiresAt`。
- Gateway 做 Bearer 形态检查和 `X-MOM-*` Header 清洗，不查 Redis、不解析 Token。
- 合法 `Authorization: Bearer <opaque-token>` 由 Gateway 原样转发。
- 业务服务独立作为 Resource Server，通过 `OpaqueTokenIntrospector` 校验 Token。
- MOM 内部同步 OpenFeign 调用全局传播当前请求的原始 Bearer Credential，由目标 Resource Server 再次验证。
- 没有当前 Servlet 请求时，Feign 不生成 Authorization；后台 SYSTEM/SERVICE 身份等真实需求出现后再扩展。
- `mom-openfeign` 只用于 MOM 内部同步 RPC，SAP、LIMS、PCS、AGV 等外部系统调用不得复用该模块。
- 最终业务权限由 `@PreAuthorize` 与业务领域规则共同决定。
- Logout 直接删除 Token Store 记录，实现即时失效。
- Redis 不可用时受保护 API Fail Closed。

当前 V1 明确不建设 Spring Authorization Server、JWT、Refresh Token、Session、OIDC、PKCE、OAuth Client 管理、机器身份或 Factory/Party Scope 通用安全框架。

## 🧩 核心能力

| 能力域 | V1 关注点 | 权威模块 |
|---|---|---|
| 身份与权限 | User、Role、Permission、密码认证、Opaque Token、Logout | `mom-auth-platform` / `mom-security` |
| 平台配置与体验 | 参数、字典、国际化、用户偏好、应用目录 | `mom-system-platform` |
| 主数据 | 集团、工厂、物料、人员与基础主数据 | `mom-mdm-platform` |
| 生产执行 | 工单、版本快照、投料、过程记录、报工 | `mom-mes-platform` |
| 仓储库存 | 库位、容器、批次、预占、流水、余额、对账 | `mom-wms-platform` |
| 质量管理 | 检验、放行、不合格处置、偏差、CAPA | `mom-qms-platform` |
| 系统集成 | 外部接口、Outbox/Inbox、重试、补偿、对账 | `mom-integration-platform` |
| 批次追溯 | 正向追溯、反向追溯、影响分析、模拟召回 | `mom-traceability-platform` |
| 平台治理 | Gateway、Security、OpenFeign、Cache、审计、链路追踪 | `mom-gateway` / `mom-framework` |

## 🛠️ 技术基线

| 层次 | 技术选型 |
|---|---|
| Java 运行时 | JDK 25 |
| 应用框架 | Spring Boot 4.1.x、Spring Framework 7.x |
| 微服务体系 | Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.x |
| 身份认证 | Spring Security Resource Server、Opaque Token、Redis Token Store |
| 内部同步 RPC | Spring Cloud OpenFeign、Spring Cloud LoadBalancer |
| 数据存储 | PostgreSQL，按服务独立 Schema |
| 缓存 | Caffeine + Redis |
| 消息与一致性 | RocketMQ、Outbox/Inbox、幂等、Seata 按真实场景使用 |
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
│   ├── mom-openfeign
│   ├── mom-cache
│   └── ...
├── mom-gateway
├── mom-auth-platform
│   ├── mom-auth-api
│   └── mom-auth-server
├── mom-system-platform
├── mom-mdm-platform
├── mom-mes-platform
├── mom-wms-platform
├── mom-qms-platform
├── mom-ems-platform
├── mom-eam-platform
├── mom-integration-platform
├── mom-traceability-platform
└── mom-architecture-tests
```

Gateway 的 Redis 限流实现属于 Gateway 本地基础设施，不再维护独立 `mom-rate-limit` Framework Module。

Mini Auth V1 当前采用简化三层：

```text
io.github.chrisshi.mom.auth
├── controller      对外 API / HTTP
├── service         业务用例、聚合与事务
└── infrastructure  数据库/MyBatis-Plus 与技术实现
```

```text
controller → service → infrastructure
```

`service` 就是当前业务聚合层，不再额外创建 `application` / `domain` 顶层包，也不为了形式化依赖倒置创建没有真实替换价值的接口。详见 [ADR-041](docs/adr/ADR-041-Mini-Auth简化三层包结构.md)。

### 强制依赖原则

- `mom-auth-api` 只暴露对外契约，不暴露数据库 Entity、Mapper 或 Repository。
- Auth Controller 不直接访问 Mapper/Entity。
- 业务服务不能直接访问其他服务的 Repository / Entity。
- 跨服务同步调用通过 `*-client` + `mom-openfeign`，异步协作按真实业务需要使用事件/MQ。
- `mom-openfeign` 只允许用于 MOM 内部同步 RPC；第三方/外部系统调用使用 Integration Adapter、RestClient/WebClient 或厂商 SDK。
- PostgreSQL 每服务保持清晰数据所有权，禁止跨域写入。
- `mom-framework` 不包含 MES、WMS、QMS 等业务规则。
- `mom-data` 不依赖 `mom-security`。
- Token 持久化通过 `MomTokenStore`，Auth 不直接知道 Redis Key / JSON / TTL。
- PCS、WCS、LIMS 等外部系统保持独立系统边界。

## 🗄️ Mini Auth 当前数据模型

```text
Schema: mom_auth

V1__create_auth_core_tables.sql
├── auth_user
├── auth_role
├── auth_permission
├── auth_user_role
└── auth_role_permission

V2__seed_platform_admin.sql
└── admin → PLATFORM_ADMIN
```

关系表遵守 ADR-026，不建立物理外键。完整说明见 [Mini Auth 数据库与代码分层](docs/architecture/Mini-Auth数据库与代码分层.md)。

## 🚀 快速开始

### 环境要求

- JDK 25
- Maven 3.9.9+
- Git

```bash
mvn -B -ntp clean verify
```

当前仓库仍处于架构收敛和业务闭环建设阶段；文档不得把尚未重新验证的旧 P1.5/P1.6 能力描述为当前已完成能力。

## 📚 文档导航

| 分类 | 入口 | 说明 |
|---|---|---|
| 总览 | [文档中心](docs/README.md) | 当前权威文档与历史资料入口 |
| 安全 | [Mini Auth V1 基线](docs/security/P1.5-认证与授权设计基线.md) | 当前认证授权权威协议 |
| 安全 | [安全架构](docs/architecture/安全架构.md) | Gateway、Token、Resource Server 与服务传播边界 |
| 架构 | [Mini Auth 数据库与代码分层](docs/architecture/Mini-Auth数据库与代码分层.md) | Auth Schema、核心表与三层结构 |
| 审计 | [CurrentActor 与数据审计](docs/architecture/CurrentActor与数据审计.md) | SecurityContext 到数据审计的稳定契约 |
| 决策 | [ADR-040](docs/adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) | 当前认证运行时决策 |
| 决策 | [ADR-041](docs/adr/ADR-041-Mini-Auth简化三层包结构.md) | Mini Auth 包结构决策 |

## 🗺️ 当前路线图

| 阶段 | 目标 | 状态 |
|---|---|---|
| 基础骨架 | JDK 25 + Boot 4、core/data/security 等基础能力 | ✅ 已完成主要收敛 |
| Mini Auth Security | Opaque Token、TokenStore、Introspector、Resource Server | ✅ 当前轮完成 |
| Gateway Security | Bearer 边缘检查、Header 清洗、本地限流与统一错误出口 | ✅ 当前轮完成 |
| Internal RPC | OpenFeign Correlation ID + Bearer 传播、LoadBalancer、Micrometer | ✅ 当前轮完成 |
| Mini Auth Persistence | `mom_auth`、5 张核心表、管理员初始化 | ✅ 骨架完成 |
| Mini Auth Business | User、Role、Permission、Login、Token 签发、Logout | 🚧 下一步 |
| Phase 02 | 供应商送货、来料检验、PDA 入库、库存闭环 | ⏳ 未启动 |

## 🧠 架构原则

1. **可控优先**：第一版每个核心模块、Bean、依赖和调用链都必须能够解释和验证。
2. **领域优先**：业务边界不能由数据库表或通用 CRUD 框架反向定义。
3. **服务端授权**：Gateway 的 Bearer 形态检查不能替代业务服务真实 Token 认证和最终授权。
4. **事实优先**：库存、批次和质量结果以不可重复的业务事实为基础。
5. **异步可恢复**：长流程按真实需要使用消息、幂等、重试、补偿和对账。
6. **集成有边界**：内部 OpenFeign 与外部系统调用技术边界分离，外部系统不继承内部用户 Bearer。
7. **可观测优先**：HTTP、服务间调用、MQ、任务和设备命令需要可关联追踪。
8. **按需演进**：不为尚不存在的 OAuth/OIDC、SSO、Refresh、SYSTEM/SERVICE 身份等需求提前建设复杂基础设施。

---

<div align="center">

**MOM Platform — 让工业业务边界、系统集成、安全授权与故障恢复成为可复用的工程能力。**

</div>