# MOM 模块分层与 Server 包结构规范

- 状态：Accepted
- 生效范围：`mom-platform` 根 Reactor 及后续新增领域模块
- 首次冻结：P1.6 S01
- 关联决策：[ADR-002：仓库与模块边界](../../adr/ADR-002-仓库与模块边界.md)

## 1. 规范口径

本文冻结 MOM 的模块职责、依赖方向和 Server 内部分层。它是 **MOM 项目决策**，不是 Spring 官方强制目录结构。已有代码不因目录名不同自动判定为错误；新代码必须遵守依赖语义，历史偏差按第 8 节渐进处理。

本规范不要求每个领域机械创建空的 `api/client/server` 模块，也不要求 Server 预先创建所有推荐包。只有出现真实调用方或职责时才创建对应模块和包。

## 2. Maven 模块职责

### 2.1 `*-api`

`*-api` 只承载真实跨模块、跨服务调用方需要的稳定契约：请求/响应 DTO、稳定枚举和调用方必须共享的接口语义。

强制规则：

- 不为三模块对称而创建无调用方 Marker API；
- 不依赖提供方 `*-server`；
- 不依赖 Spring MVC、WebFlux、Servlet、MyBatis/MyBatis-Plus、JDBC、数据库驱动或 Spring Boot 自动配置；
- 不包含 Entity、Mapper、Repository、Controller、`Configuration` 或 `AutoConfiguration`；
- 不暴露 MyBatis-Plus `Page`、数据库分页对象、内部领域聚合或基础设施异常；
- DTO 默认使用明确、不可变的 `record` 或不可变 class；技术 ID 对外使用 `String`；
- 时间使用 `Instant`，JSON 契约为 ISO-8601 UTC；枚举使用稳定字符串值；
- Jackson 注解仅在已经存在真实序列化兼容需求时允许，必须在类型注释或契约文档解释原因。

Jakarta Validation 裁决：`*-api` 默认不放 `jakarta.validation` 注解，避免为纯契约模块引入实现无关但非必要的校验依赖，也避免把某一提供方的协议校验误当成所有调用方的业务规则。只有多个真实调用方确实共享同一协议约束、依赖经评审后，才可在 `*-api` 添加 Validation API；提供方仍必须在 Web Adapter 执行校验，Application/Domain 不得信任调用方已校验。

### 2.2 `*-client`

`*-client` 是调用提供方的出站 HTTP/OpenFeign 等适配器：

- 必须依赖对应 `*-api`，不得依赖提供方 `*-server` 或其 Application Service；
- 负责传输配置、连接/读取超时、协议编解码和受控错误映射；
- 不拥有或复制提供方业务规则、Entity、Mapper、Repository；
- 网络失败必须显式暴露为调用方可处理的失败，不得伪造本地成功；
- 禁止无限重试；写请求默认不做无条件重试；
- 不把远程调用伪装成本地事务，也不承诺远端业务与本地写入原子提交。

Spring Cloud OpenFeign 提供客户端配置、错误解码和超时能力，但具体超时、重试与 fail-open/fail-closed 是 MOM 项目决策，详见后续 S03 调用方规范。

### 2.3 `*-server`

`*-server` 是独立可部署运行时：

- 可以依赖本领域 `*-api`；不得直接依赖其他领域的 `*-server`；
- 跨服务同步调用只依赖对方 `*-api`/`*-client`，异步调用依赖版本化事件契约；
- 业务 Controller 不直接依赖 Mapper/Repository；
- Application Service 不把数据库 Entity 返回给 Web；
- 数据库、Redis、Feign、消息和外部系统适配位于 Infrastructure；
- Spring Boot Bean 装配位于 `configuration`/`autoconfigure` 边界；
- 不得把本领域实现为了“方便复用”上移到 `mom-framework`。

### 2.4 `mom-framework`

`mom-framework` 只承载稳定、无业务归属、可复用的技术机制：

- 不承载 IAM、System、MDM、MES、WMS、QMS 等领域模型或业务规则；
- 不承载某个 Controller 为减少几行代码抽出的 Request/Response DTO；
- 不作为全局杂物模块；
- 新增能力必须至少存在两个真实调用方，或属于经 ADR/阶段决策确认的平台级技术基线；
- Framework 不得反向依赖领域 `*-server`。

## 3. Server 推荐包结构

```text
<bounded-context>
├── web
├── application
│   ├── command
│   ├── query
│   ├── service
│   └── model
├── domain
│   ├── model
│   ├── service
│   └── port
├── infrastructure
│   ├── persistence
│   ├── client
│   ├── messaging
│   └── cache
└── configuration
```

`interfaces.rest` 是当前 MDM/Integration 使用的等价入站 Adapter 命名；新模块默认使用 `web`。不为改名而批量搬包。`autoconfigure` 仅用于真正的自动配置；普通服务内装配优先使用 `configuration`。

依赖方向：

```text
Web → Application → Domain
Infrastructure → Domain Port / Application Port
Configuration → 装配 Web、Application、Domain 与 Infrastructure
Domain ↛ Web / Infrastructure / Spring MVC / MyBatis
```

## 4. 分层职责

### 4.1 Web

负责 HTTP 参数绑定、认证主体/安全上下文接入、Bean Validation、Request DTO 到 Command/Query 的转换、HTTP 状态与 Response DTO、协议异常映射。

禁止直接调用 Mapper/Repository、声明事务、拼 SQL、执行业务状态机、返回 Entity、实现缓存，或在 Controller 中编排多个跨服务写操作。

### 4.2 Application

负责用例编排、Spring 本地事务边界、认证后的业务授权、Domain 与 Port 调用、幂等协调，以及返回 Application View/Result。

禁止依赖 `HttpServletRequest`、`ServerWebExchange`、Controller、Mapper Entity 或其他领域 Server；网络重试不能替代补偿、对账和状态机。

### 4.3 Domain

负责领域状态、不变量、值对象、策略、领域服务及 Repository/外部能力 Port。

禁止依赖 Spring MVC/WebFlux、Servlet、MyBatis/MyBatis-Plus、JDBC、Feign、Redis Template、Controller DTO 和 Infrastructure。`@Transactional` 默认位于 Application，而非 Domain。

### 4.4 Infrastructure

负责 Mapper、Repository 实现、数据库 Entity、Feign/HTTP、Redis、消息与外部系统 Adapter，并实现 Domain/Application Port。

底层异常必须转换为 Application 可理解的稳定失败语义；API 不得包含完整 SQL、数据库约束原文、凭据、内网连接信息或底层堆栈。

### 4.5 Configuration

只负责 Bean 装配、条件配置、`ConfigurationProperties`、`AutoConfiguration` 和 Framework/Adapter 连接，不承载业务用例。

## 5. 自动门禁

S01 的 `mom-architecture-tests` 使用两类可执行检查：

1. 基于 XML 语义解析 Reactor POM，验证 api/client/server/Gateway 直接依赖边界；
2. 使用 ArchUnit 分析已编译字节码，验证 Domain/Application/Controller/API/Gateway 的代码依赖边界。

门禁不以 Shell 正则扫描 Java import，不使用宽泛包排除。空模块只检查 POM；有实现的模块同时检查字节码。历史例外只允许按精确文件和责任登记，不能用 `ignoreDependency` 隐藏新违规。

## 6. Review 清单

- 新契约是否有真实跨模块调用方；
- api/client 是否意外依赖 server 或 Web/持久化实现；
- Controller 是否只做协议适配；
- Application 是否拥有用例和事务边界；
- Domain 是否保持框架无关；
- Infrastructure 异常是否已脱敏并转换；
- Framework 能力是否满足两个调用方或平台基线条件；
- 新例外是否精确登记了文件、原因、负责人 Slice 和移除条件。

## 7. 未来模块

`mom-system-platform` 仅允许在 S11 数据所有权 ADR 完成并经 Review 后，于 S12 创建。本规范不预先决定其最终模块形态，也不构成提前创建授权。

## 8. 当前历史例外

以下偏差是 S01 的准确基线，不代表推荐写法：

| 文件 | 当前原因 | 处理 Slice |
|---|---|---|
| `mom-iam-platform/mom-iam-server/.../admin/IamAdminController.java` | P1.5 已发布 Controller 位于 `admin` 包，并直接使用 Application View 与 Service 内嵌 Command；为保持公开契约不搬包 | S08 抽取应用服务时保持行为整理；S09 收敛 Admin Web/错误 DTO |
| `mom-iam-platform/mom-iam-server/.../admin/IamAdminService.java` | 管理用例、Request Command、规范化和部分审计上下文集中在单类 | S08/S09 按职责渐进拆分，不改公开字段 |
| `mom-iam-platform/mom-iam-server/.../web/IamDirectAuthenticationController.java` | 第一方安全端点仍含手工校验、内嵌 DTO、设备名静默截断，并直接读取 `IamUserEntity` 建立审计主体 | S08 只允许行为保持抽取并移除 Controller→Entity；协议裁决前不得改认证行为 |
| `mom-mdm-platform/mom-mdm-server/.../interfaces/rest/**`、`mom-integration-platform/mom-integration-server/.../interfaces/rest/**` | Phase 01 技术探针使用 `interfaces.rest` 命名 | 技术探针保留；新增正式 API 遵循本规范，不做纯改名迁移 |
| `mom-mdm-platform/mom-mdm-server/.../application/MdmDataProbeService.java` | Phase 01 PostgreSQL 探针直接依赖 Mapper/Entity | S04 决定技术探针的测试归属并移除 Application 例外 |
| `mom-mdm-platform/mom-mdm-server/.../application/MdmOutboxProbeService.java` | Phase 01 Outbox 探针直接构造持久化 Entity | S04 决定技术探针的测试归属并移除 Application 例外 |
| `mom-mdm-platform/mom-mdm-server/.../application/MdmSeataAtLocalParticipantService.java` | 受控 Seata PoC 直接使用 `JdbcTemplate` | S04 保留真实 PoC 证据并决定 Adapter 归位方式 |
| `mom-mdm-platform/mom-mdm-server/.../application/MdmSeataAtProbeService.java` | 受控 Seata PoC 直接依赖 Web Request/Response DTO | S04 保留协议行为并移除 Application→Web 依赖 |
| `mom-integration-platform/mom-integration-server/.../application/IntegrationSeataAtParticipantService.java` | 受控 Seata 参与者直接使用 `JdbcTemplate` | S04 保留真实 PoC 证据并决定 Adapter 归位方式 |
