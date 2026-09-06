# MOM 模块分层与 Server 包结构规范

- 状态：Accepted
- 生效范围：`mom-platform` 根 Reactor 及后续新增领域模块
- 首次冻结：P1.6 S01
- 当前架构决策：[ADR-042：MOM 渐进式分层与对象模型](../../adr/ADR-042-MOM渐进式分层与对象模型.md)
- 关联决策：[ADR-002：仓库与模块边界](../../adr/ADR-002-仓库与模块边界.md)

## 1. 规范口径

本文冻结 MOM 的模块职责、依赖方向和 Server 内部分层。它是 **MOM 项目决策**，不是 Spring 官方强制目录结构。

MOM 当前不再要求所有 bounded context 一开始就套用完整 Clean Architecture / Hexagonal Architecture。默认原则是：

> **简单开始，按真实业务和技术复杂度升级。**

已有代码不因为采用更严格的 Domain/Port/Adapter 结构而被判定为错误，也不要求为了统一目录做无业务收益的降级搬包。新代码必须遵守依赖语义，并避免为了架构形式创建空接口、空包或只做转发的包装层。

本规范同样不要求每个领域机械创建空的 `api/client/server` 模块。只有出现真实调用方或职责时才创建对应模块和包。

## 2. Maven 模块职责

### 2.1 `*-api`

`*-api` 只承载真实跨模块、跨服务调用方需要的稳定契约：请求/响应 DTO、稳定枚举和调用方必须共享的接口语义。

强制规则：

- 不为三模块对称而创建无调用方 Marker API；
- 不依赖提供方 `*-server`；
- 不依赖 Spring MVC、WebFlux、Servlet、MyBatis/MyBatis-Plus、JDBC、数据库驱动或 Spring Boot 自动配置；
- 不包含 Entity、Mapper、Repository、Controller、`Configuration` 或 `AutoConfiguration`；
- 不暴露 MyBatis-Plus `Page`、数据库分页对象、内部领域聚合或基础设施异常；
- 契约模型默认使用明确、不可变的 `record` 或不可变 class；技术 ID 对外使用 `String`；
- 时间使用 `Instant`，JSON 契约为 ISO-8601 UTC；枚举使用稳定字符串值；
- Jackson 注解仅在已经存在真实序列化兼容需求时允许，必须在类型注释或契约文档解释原因。

Jakarta Validation 裁决：`*-api` 默认不放 `jakarta.validation` 注解。只有多个真实调用方确实共享同一协议约束、依赖经评审后，才可在 `*-api` 添加 Validation API；提供方仍必须在 HTTP 入站边界执行校验，Application/Domain 不得信任调用方已校验。

### 2.2 `*-client`

`*-client` 是调用提供方的出站 HTTP/OpenFeign 等适配器：

- 必须依赖对应 `*-api`，不得依赖提供方 `*-server` 或其 Application；
- 负责传输配置、连接/读取超时、协议编解码和受控错误映射；
- 不拥有或复制提供方业务规则、Entity、Mapper、Repository；
- 网络失败必须显式暴露为调用方可处理的失败，不得伪造本地成功；
- 禁止无限重试；写请求默认不做无条件重试；
- 不把远程调用伪装成本地事务，也不承诺远端业务与本地写入原子提交。

### 2.3 `*-server`

`*-server` 是独立可部署运行时：

- 可以依赖本领域 `*-api`；不得直接依赖其他领域的 `*-server`；
- 跨服务同步调用只依赖对方 `*-api`/`*-client`，异步调用依赖版本化事件契约；
- Controller 不直接依赖 Mapper/Repository/Entity；
- Application 不把数据库 Entity 直接暴露为 HTTP/API 契约；
- 数据库、Redis、Feign、消息和外部系统实现位于 Infrastructure；
- 不得把本领域实现为了“方便复用”上移到 `mom-framework`。

### 2.4 `mom-framework`

`mom-framework` 只承载稳定、无业务归属、可复用的技术机制：

- 不承载 IAM、System、MDM、MES、WMS、QMS 等领域模型或业务规则；
- 不承载某个 Controller 为减少几行代码抽出的 Request/Response；
- 不作为全局杂物模块；
- 新增能力必须至少存在两个真实调用方，或属于经 ADR/阶段决策确认的平台级技术基线；
- Framework 不得反向依赖领域 `*-server`。

## 3. Server 渐进式结构

### 3.1 Level 1：默认结构

新增简单业务默认：

```text
<bounded-context>
├── controller / web
├── application
└── infrastructure
```

依赖方向：

```text
Controller / Web → Application → Infrastructure
```

Level 1 中，Application 允许直接依赖本 bounded context 的 Mapper、Entity 或具体 Infrastructure 组件。不得为了形式上的 DIP 强制创建 Repository Port、Repository Adapter、Application Service 接口或一对一代理。

### 3.2 Level 2：引入 Domain

当状态机、业务不变量、复杂生命周期或跨实体规则已经真实出现时：

```text
<bounded-context>
├── controller / web
├── application
├── domain
└── infrastructure
```

Domain 一旦出现，必须保持对 HTTP、MyBatis、JDBC、Feign、Redis 等技术实现无关。

### 3.3 Level 3：引入 Port / Adapter

当存在真实可替换实现、多个外部系统、不可控依赖隔离或 ORM/SDK 污染时，再增加 Port/Adapter：

```text
Controller / Web → Application → Domain / Port ← Infrastructure Adapter
```

ADR-027、ADR-028 在这一层级继续有效，但不再被解释为所有 bounded context 第一版必须预付的结构成本。

## 4. 分层职责

### 4.1 Controller / Web

负责：

- HTTP 参数绑定；
- 认证主体/安全上下文接入；
- Bean Validation；
- Request/Response 协议模型；
- HTTP 状态与协议异常映射；
- 调用 Application。

禁止：

- 直接调用 Mapper/Repository；
- 直接使用数据库 Entity 作为 API 模型；
- 声明业务事务；
- 拼 SQL；
- 执行业务状态机；
- 编排多个持久化组件或跨服务写操作。

### 4.2 Application

Application 是所有层级都存在的业务用例层，负责：

- 用例编排；
- Spring 本地事务边界；
- 认证后的业务授权；
- 引用校验、幂等和关系编排；
- 调用 Domain（存在时）；
- 调用 Infrastructure 或 Port；
- 产生 View/Result。

Level 1 中允许 Application 直接依赖本服务 Infrastructure；Level 2/3 若已经建立 Domain/Port 边界，则应遵守该 bounded context 已接受的更严格规则。

Application 禁止依赖 `HttpServletRequest`、`ServerWebExchange`、Controller 或其他领域 `*-server`。

### 4.3 Domain（按需）

Domain 只在存在真实领域模型时创建，负责领域状态、不变量、值对象、策略、领域服务等。

Domain 禁止依赖 Spring MVC/WebFlux、Servlet、MyBatis/MyBatis-Plus、JDBC、Feign、Redis Template、Controller DTO 和 Infrastructure。`@Transactional` 默认位于 Application。

### 4.4 Infrastructure

Infrastructure 负责具体技术实现：

- Entity、Mapper、SQL；
- Redis；
- Feign/HTTP；
- 消息；
- 文件/对象存储；
- 复杂查询 QueryMapper；
- Level 3 所需 Adapter 实现。

底层异常不得以 SQL、约束原文、连接信息、凭据或堆栈形式泄露给公开 API。

### 4.5 Configuration（按需）

只有真实 Bean 装配、`ConfigurationProperties`、条件配置或 AutoConfiguration 需要时才创建 `configuration`。不为了目录对称提前增加第四或第五个顶层包。

## 5. 对象模型

新增代码遵守 ADR-042 的 **3 + 1** 对象语义：

- `Request / Response`：HTTP 或稳定 API 契约；
- `Entity`：数据库持久化模型；
- `View`：Application 查询/展示结果；
- `Row / Projection`：仅复杂 SQL 与最终 View 明显不同时按需出现。

不把 `POJO` 作为架构角色；新增代码不使用 `DO`、`PO`、`BO`、`VO` 作为默认分层后缀。`DTO` 可以作为概念描述，但类名优先表达真实用途。

跨过一层不等于必须创建一个新对象。`Command`、`Query`、`Converter`、`Assembler` 仅在真实解耦、复用或转换逻辑出现时增加。

## 6. 查询与 Aggregate 术语

多表 JOIN 返回结果默认属于 `View`、`Row` 或 `Projection`，不因为关联了多张表就称为 DDD Aggregate。

DDD Aggregate 只表达业务一致性和事务边界。简单读优先使用 Mapper；复杂本地多表读才进入 QueryMapper；只有写行为需要维护领域不变量时才加载 Domain Aggregate。

## 7. 自动门禁原则

`mom-architecture-tests` 应验证真正稳定的依赖边界，而不是强迫所有服务目录完全对称：

1. API/Client/Server Maven 依赖边界继续强制；
2. Controller 不得直接访问 Mapper/Repository/Entity；
3. Domain 一旦存在，必须保持技术实现无关；
4. 已通过独立 ADR 进入 Level 2/3 的 bounded context，可以保留更严格的专用 ArchUnit 规则；
5. Level 1 Application 不因直接依赖本服务 Mapper/Entity 而被判定为架构违规；
6. 历史例外只允许精确登记，不能用宽泛白名单掩盖新问题。

## 8. Review 清单

- 新模块是否真的需要 `api/client`，还是只是为了对称；
- 新类是否解决了真实业务或技术边界；
- 是否因为“跨层”机械创建 Command/Converter/Repository；
- Controller 是否只做协议适配；
- Application 是否拥有用例和事务边界；
- Domain 是否因真实不变量而存在，而非目录占位；
- Port/Adapter 是否对应真实替换边界；
- 多表结果是否被错误命名为 Aggregate；
- Infrastructure 异常是否已脱敏；
- Framework 能力是否满足两个真实调用方或平台基线条件。

## 9. 历史代码与演进

已有 IAM/System/MDM/Integration 等代码中已经通过 ADR 冻结的 Domain、Repository Port、Adapter 结构继续有效，不要求为了 ADR-042 反向简化。

Mini Auth 是当前 Level 1 的明确采用者，包结构以 ADR-042 和 `package-directory-architecture-standard.md` 为准。

未来某个 bounded context 从 Level 1 升级到 Level 2/3 时，应在真实复杂度出现后通过 ADR 或明确 Review 记录触发原因，而不是预先创建占位结构。