# 模块边界

- 状态：Current
- 当前事实来源：`fix/mini-auth`

## 1. 依赖方向

```text
mom-core
  ↑
framework capabilities
  ↑
domain api / client / server
  ↑
gateway and bootstrap tests
```

`mom-core` 保持纯 Java 基础契约，不依赖 Spring MVC/WebFlux、数据库或具体业务领域。

## 2. 强制规则

1. `*-api` 只包含跨模块契约，不暴露数据库 Entity、Mapper 或 Repository。
2. `*-client` 只依赖对应 `*-api` 与内部调用基础设施，不拥有业务数据。
3. `*-server` 实现领域能力，禁止依赖其他领域的 `*-server`。
4. 跨领域同步调用通过 `*-client`；异步协作按真实业务需要通过领域事件/MQ，不把所有协作都强制改成同步 RPC。
5. PostgreSQL 每服务独立 Schema，禁止跨 Schema JOIN 和跨域写入。
6. `mom-framework` 不得包含 MES、WMS、QMS 等业务规则。
7. PCS、WCS 等外部控制系统保持独立系统边界，不进入业务领域内部模型。

## 3. `mom-openfeign` 边界

`mom-openfeign` 是 MOM **内部同步 HTTP RPC** 的共享基础设施，不是通用外部 HTTP Client。

当前职责：

```text
Spring Cloud OpenFeign
+ Spring Cloud LoadBalancer
+ Feign Micrometer
+ X-Correlation-Id 传播
+ 当前 Servlet 请求 Bearer Authorization 原样传播
```

明确不负责：

```text
Token 解析/校验/Redis 查询
SYSTEM/SERVICE Token 生成
全局 Retry
全局 CircuitBreaker/Fallback
外部系统认证
X-MOM-USER/ROLE/PERMISSION 传播
```

内部 `*-client` 可以依赖 `mom-openfeign`。SAP、LIMS、PCS、AGV 等第三方/外部系统调用不得复用 `mom-openfeign`；应在 Integration/Infrastructure Adapter 中使用 RestClient、WebClient 或厂商 SDK，并单独处理认证。

无当前 Servlet 请求时，Feign 不生成 Authorization。后台任务、Scheduler、MQ Consumer 等机器调用的 SYSTEM/SERVICE 身份在真实需求出现后再设计。

## 4. Gateway 与限流边界

`mom-gateway` 自己拥有 Gateway 专属的：

- Bearer 边缘检查；
- `X-MOM-*` 清洗；
- 路由与服务发现；
- Redis RateLimiter 的 Fail-Closed 包装；
- RequestIdentity KeyResolver；
- Gateway 异常响应。

原独立 `mom-rate-limit` Module 已取消。Gateway 专属的 WebFlux/RateLimiter 实现不再放入 `mom-framework`。

Gateway 不承担真实 Token 认证与最终业务授权；目标业务服务仍独立作为 Resource Server。

## 5. `mom-core` 错误契约

`mom-core.error.ErrorCode` 只提供纯 Java 契约：

```text
code
messageKey
defaultMessage
```

其中 `messageKey` 只为未来国际化预留。V1 不在 Core 建设 `MessageSource`、Locale Resolver、HTTP Status、MVC/WebFlux 异常框架或 JSON 序列化。

具体错误枚举归各模块所有，例如 `GatewayErrorCode implements ErrorCode`；禁止建立包含所有领域错误的巨大 `GlobalErrorCode`。

## 6. Mini Auth 精确例外

正式复杂 bounded context 默认仍可按 Web / Application / Domain / Infrastructure 边界组织；Mini Auth V1 根据 ADR-041 精确采用：

```text
io.github.chrisshi.mom.auth
├── controller
├── service
└── infrastructure
```

```text
controller → service → infrastructure
```

不默认创建 Repository Port、一对一 Adapter 或其他形式化抽象。

## 7. System Platform 当前边界

`mom-system-platform` 继续拥有平台参数、字典、国际化资源与用户偏好等平台配置能力，但旧 IAM/JWT/revoked-sid 描述不再作为当前安全协议。

System 不拥有 User/Role/Permission、Token、Authentication 或 Authorization 权威数据；这些由 Mini Auth / `mom-security` 负责。

System Client 若未来增加真实内部同步接口，遵循 `*-api + *-client + mom-openfeign` 边界；当前没有必要为占位 Client 提前增加远程调用实现。

## 8. 数据与安全隔离

- `mom-data` 不依赖 `mom-security`。
- TokenStore 是安全权威存储能力，不复用普通 Cache 语义。
- 业务服务不得访问其他服务的 Repository、Mapper、Entity 或 Schema。
- 外部系统不得直接访问领域服务数据库或内部服务接口。
- 用户 Bearer 只能沿 MOM 内部同步调用链传播，不进入第三方系统调用。

POM XML 语义门禁与 ArchUnit 规则位于 `mom-architecture-tests`。