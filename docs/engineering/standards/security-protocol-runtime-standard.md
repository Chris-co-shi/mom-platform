# Mini Auth、安全协议、Gateway 与 Resource Server 运行规范

- 状态：Current
- 当前基线：Mini Auth V1
- 决策权威：[ADR-040：Mini Auth 与 Redis Opaque Token 认证基线](../../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- Gateway 设计：[Mini Gateway 设计](../../architecture/Mini-Gateway设计.md)
- 替代范围：本文 2026-09-06 之前关于 JWT、Issuer、Audience、JWK、Spring Authorization Server、Session/Refresh 与 revoked sid 的 V1 运行时结论

## 1. 当前协议边界

Mini Auth V1 采用第一方账号密码登录和 Redis-backed Opaque Access Token：

```text
Client → Gateway → Business Resource Server → MomOpaqueTokenIntrospector → MomTokenStore → Redis
```

`mom-auth` 负责校验账号密码、签发和注销 Opaque Token。业务 Resource Server 负责 Token 最终认证、
`Authentication` 构建、权限检查与领域授权。Gateway 只转发 Bearer Header，不查询 Token Store，不构建
Authentication，也不维护“公开 API / 受保护 API”清单。

V1 不启用以下协议和组件：

- JWT Access Token、Issuer、Audience、JWK；
- Spring Authorization Server、OAuth2/OIDC Client；
- Refresh Token、Session、Refresh Rotation、revoked sid；
- Gateway Token introspection、用户/角色/权限缓存。

未来重新引入上述能力必须通过新 ADR，不得从历史文档或旧实现隐式恢复。

## 2. HTTP 入口矩阵

| 请求 | Gateway 行为 | Resource Server 行为 |
|---|---|---|
| 无 `Authorization` | 不判定业务认证，继续路由 | 按自身 `permitAll` 或 Resource Server 规则返回结果/401 |
| 单个合法 Bearer Header | 原样转发，不解析 Token | 通过 Opaque Token introspection 最终认证 |
| 重复或明显非法 Authorization | Gateway 返回稳定 401 | 不到达下游 |
| CORS `OPTIONS` | 由 Gateway 全局 CORS 处理，不要求 Bearer | 通常不到达业务认证链 |
| 客户端身份 Header | Gateway 清理后转发其他 Header | 不信任外部身份 Header，使用 Authentication |

Gateway 的浅层 Bearer 格式检查不是认证。业务服务不得因为请求经过 Gateway 就省略独立 Resource Server。

## 3. Gateway 运行边界

Gateway 负责：

- 显式路由、Nacos Discovery 与 Spring Cloud LoadBalancer；
- CORS 与无凭证 OPTIONS 预检；
- 基于可信真实客户端 IP 的 Redis 粗粒度限流；
- Correlation ID 基本校验、生成和传播；
- W3C Trace Context 传播；
- 清理外部身份 Header；
- Gateway 自身可预期异常。

Gateway 不负责 Token 真伪验证、最终 Permission、Role、Factory/Party Scope、Data Scope、对象归属、菜单权限
或业务状态。Gateway 必须保持 WebFlux，不得引入 Servlet/WebMVC、阻塞 I/O 或 ThreadLocal 请求上下文。

Redis RateLimiter 正常拒绝返回 429；Redis 基础设施故障返回 503 并 fail closed。429 继续使用 Spring Cloud
Gateway 当前默认响应，不为了统一 JSON 侵入框架限流实现。

## 4. CORS 与 Header 信任边界

Gateway 是浏览器 CORS 主边界。Origin 使用精确白名单，禁止 `*` 与 credentials 同时启用；允许的方法、请求
Header、响应暴露 Header 和 `maxAge` 必须显式配置。`add-to-simple-url-handler-mapping=true`，确保因 Method
Predicate 不匹配普通路由的 OPTIONS 仍能被处理。

外部请求中的以下 Header 不可信并由 Gateway 删除：

- 所有 `X-MOM-*`；
- `X-User-Id`、`X-User-Name`；
- `X-Role`、`X-Roles`、`X-Permission`、`X-Permissions`；
- `X-Factory-Id`、`X-Party-Id`。

`Authorization`、`X-Correlation-Id` 和普通业务 Header 不属于该清理集合。Correlation ID 只用于日志、追踪、
审计关联，不得作为身份、权限、幂等键或业务主键。

## 5. Resource Server

业务服务通过 `MomOpaqueTokenIntrospector` 和 `MomTokenStore` 独立验证 Opaque Token，并保证
`Authentication#getName() == userId`。`MomTokenPrincipal` 的 V1 字段仅为 `userId`、`authorities`、
`expiresAt`。业务服务继续通过 `@PreAuthorize` 与领域规则完成最终授权。

Token 不存在表示无效 Token；Redis 连接、超时、JSON 损坏或反序列化失败属于基础设施故障，不得伪装为
“Token 不存在”，更不得返回认证成功。具体 HTTP 映射遵循 Resource Server 错误模型，但认证必须 fail closed。

## 6. 配置与 Secret

- Base 不激活 Profile，Nacos Discovery 默认关闭；
- Redis、Nacos 密码默认空，只能由环境变量、Kubernetes Secret 或经 ADR 的 Secret Manager 提供；
- CORS、可信代理 CIDR、安全协议边界不允许不受控动态刷新；
- Gateway 以 `TrustedClientIpResolver` 独占代理信任判断，`server.forward-headers-strategy` 固定为 `none`，
  Spring Boot / Reactor Netty 不得在解析器之前根据 Forwarded Header 改写原始 TCP peer；
- 正式 Gateway 不直接暴露公网，只接受受控 Nginx/LB 网络入口；
- Nacos Discovery 与 Config 分离，当前不引入 Nacos Config Starter 或 Bootstrap 文件。

## 7. 历史资料说明

仓库中的 ADR-019、ADR-024 及 P1.6 S03-S10 阶段报告记录了旧 IAM/SAS/JWT 架构的设计与验收事实，仅用于
历史追溯。它们不是 Mini Auth V1 的运行手册。发生冲突时，以 ADR-040、本文和当前代码为准；不得删除历史
ADR，也不得把历史测试证据描述为当前 Opaque Token 实现的测试证据。

## 8. 官方依据

- [Spring Cloud Gateway CORS Configuration](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html)
- [Spring Cloud Gateway RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)
- [Spring Security Reactive CORS](https://docs.spring.io/spring-security/reference/reactive/integrations/cors.html)
- [Spring Security Opaque Token Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html)
