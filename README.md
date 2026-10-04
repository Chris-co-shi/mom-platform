<div align="center">

# MOM Platform

### 面向新能源材料制造的开源工业 MOM 平台

以真实制造业务为主线，逐步构建 MDM、MES、WMS、QMS、EMS、EAM、Integration 与 Traceability 等能力，并保持每一阶段架构可解释、可验证、可演进。

<p>
  <a href="https://github.com/Chris-co-shi/mom-platform/actions/workflows/ci.yml">
    <img alt="CI" src="https://github.com/Chris-co-shi/mom-platform/actions/workflows/ci.yml/badge.svg?branch=main">
  </a>
  <img alt="Java" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white">
  <img alt="Spring Cloud" src="https://img.shields.io/badge/Spring%20Cloud-2025.1-6DB33F?logo=spring&logoColor=white">
</p>

[文档中心](docs/README.md) · [ADR-040：Mini Auth](docs/adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) · [ADR-042：渐进式分层](docs/adr/ADR-042-MOM渐进式分层与对象模型.md) · [工程规范](docs/engineering/standards/)

</div>

---

> [!IMPORTANT]
> 当前架构原则以 **ADR-042** 为准：简单业务默认采用 `controller → application → infrastructure`，只有真实复杂度出现后才升级 Domain、Port / Adapter。仓库事实优先于历史计划文档；ADR-041 已被 ADR-042 替代，不再作为当前包结构依据。

## 当前状态

当前仓库已经不再停留在“Auth 骨架”阶段：

- Mini Auth 已落地 User / Role / Permission 管理、关系维护、用户名密码登录、Redis-backed Opaque Token 与 Logout；
- Gateway 已承担 Bearer 边缘检查、`X-MOM-*` Header 清洗、路由与本地限流，最终认证和授权仍由 Resource Server 负责；
- `mom-framework` 已提供统一分页、数据访问、安全、缓存、内部 OpenFeign 等基础能力；
- MDM 已进入真实主数据建设，当前包含工厂结构、仓库结构、Location、UOM、Material Category 与 Material 等第一阶段能力；
- MDM 父子主数据的创建、启用与停用已通过 PostgreSQL 行锁和固定锁序处理关键并发竞态；
- 架构、持久化、安全、工程规范通过 `mom-architecture-tests` 与 GitHub Actions 持续约束。

## 项目愿景

`MOM Platform` 面向新能源材料制造场景，以锂电池电解液作为主要业务样例，重点验证：

- MES、WMS、QMS 等 bounded context 的边界与协作；
- 原料、半成品、成品之间的批次谱系与追溯；
- 库存事实、余额、预占、冻结、移动和对账；
- 生产工单、投料、过程记录和报工；
- 质量检验、放行、不合格处置与 LIMS 协同；
- PCS、WCS / AGV 等设备侧异步命令、状态机、失败恢复和人工接管；
- SAP、LIMS 等外部系统的幂等、重试、补偿和对账；
- 可解释的认证授权、审计和可观测性。

## 技术基线

| 层次 | 技术选型 |
|---|---|
| Java | JDK 25 |
| 应用框架 | Spring Boot 4.1.x、Spring Framework 7.x |
| 微服务 | Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.x |
| 安全 | Spring Security、Resource Server、Redis-backed Opaque Token |
| 数据库 | PostgreSQL |
| 数据访问 | MyBatis-Plus、Flyway |
| 缓存 | Redis / Caffeine |
| 内部同步 RPC | Spring Cloud OpenFeign、LoadBalancer |
| 消息与一致性 | RocketMQ、Outbox / Inbox、幂等、补偿；Seata 仅按真实场景使用 |
| 注册与配置 | Nacos |
| 可观测性 | Micrometer、OpenTelemetry、Prometheus、Loki、Tempo、Grafana |
| 测试 | JUnit 5、Testcontainers、ArchUnit |

> 具体依赖版本以根 `pom.xml` 与 `mom-dependencies` 为唯一权威来源。

## 仓库结构

```text
mom-platform
├── mom-dependencies
├── mom-framework
│   ├── mom-core
│   ├── mom-data
│   ├── mom-security
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

## 渐进式分层

新增简单业务默认使用 Level 1：

```text
controller / web
        ↓
application
        ↓
infrastructure
```

职责约束：

- `controller / web`：HTTP / API 协议适配、Bean Validation、Request / Response 转换；
- `application`：业务用例、事务、关系编排、业务校验与授权入口；
- `infrastructure`：数据库、Redis、HTTP / Feign、消息、文件等具体技术实现。

Level 1 允许 Application 直接依赖本 bounded context 的 Mapper、Entity 或具体 Infrastructure 组件。项目不为了形式上的依赖倒置预先创建 Repository Port、Adapter、Application Interface 或一对一 Converter。

出现复杂状态机、持续增长的不变量、多个真实外部实现或需要隔离的外部失败语义时，再按 ADR-042 升级：

```text
Level 2：controller / web → application → domain + infrastructure
Level 3：application / domain → port ← infrastructure adapter
```

### Mini Auth 当前结构

```text
io.github.chrisshi.mom.auth
├── controller
├── application
│   ├── AuthenticationApplication
│   ├── UserApplication
│   ├── RoleApplication
│   └── PermissionApplication
└── infrastructure
    ├── entity
    ├── mapper
    ├── query          # 仅真实复杂查询需要时出现
    └── configuration
```

依赖方向固定为：

```text
controller → application → infrastructure
```

## Mini Auth V1

当前认证授权基线：

- 授权模型：`User → Role → Permission`；
- 用户通过第一方用户名密码登录；
- Access Token 使用高熵随机 Opaque Token，不使用 JWT；
- Token 认证快照存储在 Redis；
- Gateway 不解析 Token、不查询 Redis，只负责边缘协议检查和路由；
- 业务服务作为 Resource Server 独立验证 Token；
- `@PreAuthorize` 与领域规则共同构成最终授权边界；
- MOM 内部同步 OpenFeign 传播当前请求的原始 Bearer Credential，由目标服务重新验证；
- 外部 SAP、LIMS、PCS、AGV 调用不得复用内部用户 Bearer；
- Logout 删除当前 Token Store 记录，实现当前 Token 即时失效；
- Redis 不可用时受保护 API Fail Closed。

V1 当前不建设 Spring Authorization Server、JWT、Refresh Token、OIDC、PKCE、OAuth Client 管理、机器身份或通用 Factory / Party Scope 安全框架。

### Auth 数据模型

```text
Schema: mom_auth

├── auth_user
├── auth_role
├── auth_permission
├── auth_user_role
└── auth_role_permission
```

业务关系表遵守 ADR-026，不建立物理外键；引用完整性由 Application、本地事务、唯一/检查约束、索引和测试共同保证。

## 分页契约

普通控制面分页统一使用强类型请求：

```text
POST /资源/search
        ↓
PageQuery<XxxPageParams>
        ↓
Application
        ↓
PageAdapter / Mapper
        ↓
PageResult<T>
```

`params`、`pageNo`、`pageSize` 均必须显式提供；无过滤条件时使用明确空参数对象 `{}`。最大 page size 由 `mom.data.pagination.max-page-size` 控制，超限请求在 SQL 执行前拒绝。

大量流水、事件、审计与追溯数据不强制使用 Offset Pagination，应根据真实访问模式评估 Keyset / Cursor。

## MDM 当前范围

当前 MDM Level 1 主数据包括：

```text
Plant
  └── Workshop
       └── ProductionLine
            └── Workstation

Plant
  └── Warehouse
       └── WarehouseArea

LocationType + Location
Dimension + UomCategory + Uom + ConversionRule
MaterialCategory + Material
```

父级停用当前不做级联写入；创建或重新启用直接子级时要求父级有效，并通过 PostgreSQL 行锁避免父级停用与子级写入交叉提交。后续 MES / WMS 消费这些数据时，需要区分记录自身状态与整条父链的业务有效性。

## 强制边界

- `*-api` 不暴露数据库 Entity、Mapper 或具体 Server 实现；
- Controller 不直接访问 Mapper / Entity；
- 一个业务服务不得直接写入另一个 bounded context 的数据库；
- PostgreSQL 数据所有权按服务 / bounded context 保持清晰；
- `mom-framework` 不承载 MES、WMS、QMS 等领域规则；
- `mom-data` 不依赖 `mom-security`；
- `mom-openfeign` 只服务 MOM 内部同步 RPC；
- SAP、LIMS、PCS、AGV 等外部系统通过 Integration Adapter、RestClient / WebClient 或厂商 SDK 接入；
- Domain、Repository Port、Adapter、CQRS 等模式必须由真实复杂度触发，不做架构预付费。

## 验证

本地完整验证：

```bash
mvn -B -ntp clean verify
```

仓库还提供：

```bash
bash scripts/codex-doctor.sh
bash scripts/codex-verify-changed.sh
bash scripts/codex-mvn-test.sh clean verify
```

GitHub Actions 已包含主 CI 与工程基线校验。架构规范如果不能通过自动测试或代码事实验证，不应只停留在文档中。

## 文档导航

| 分类 | 文档 |
|---|---|
| 总览 | [docs/README.md](docs/README.md) |
| 当前分层 | [ADR-042：MOM 渐进式分层与对象模型](docs/adr/ADR-042-MOM渐进式分层与对象模型.md) |
| Auth 安全 | [ADR-040：Mini Auth 与 Redis Opaque Token](docs/adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) |
| Auth 数据与分层 | [Mini Auth 数据库与代码分层](docs/architecture/Mini-Auth数据库与代码分层.md) |
| HTTP 契约 | [HTTP API Contract Standard](docs/engineering/standards/http-api-contract-standard.md) |
| CRUD 规范 | [CRUD Application Standard](docs/engineering/standards/crud-application-standard.md) |

## 架构原则

1. **可控优先**：核心模块、Bean、依赖和调用链必须能够解释和验证。
2. **业务优先**：领域边界不由数据库表或通用 CRUD 框架反向定义。
3. **事实优先**：库存、批次、质量结果等关键业务数据以明确事实和生命周期建模。
4. **服务端授权**：Gateway 的边缘检查不能替代业务服务的真实认证与授权。
5. **一致性有边界**：优先使用本地事务和数据库约束；跨域一致性再使用消息、幂等、补偿和对账。
6. **集成有边界**：内部 RPC 与外部系统调用分离。
7. **可观测优先**：HTTP、RPC、MQ、任务和设备命令需要可关联追踪。
8. **按需演进**：只为已经出现的复杂度支付架构成本。
