# Mini Gateway 设计

- 状态：Current
- 适用版本：Mini Auth V1，Spring Boot 4.1.x，Spring Cloud 2025.1.x
- 实现模块：`mom-gateway`
- 认证决策：[ADR-040：Mini Auth 与 Redis Opaque Token 认证基线](../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- 安全运行规范：[Mini Auth、安全协议、Gateway 与 Resource Server 运行规范](../engineering/standards/security-protocol-runtime-standard.md)

## 1. 目标与非目标

Mini Gateway 为浏览器、PDA 和外部调用方提供一个可控的响应式入口：

```text
Client
  ↓
Trusted Nginx
  ↓
mom-gateway
  ├── Route / Discovery / LoadBalancer
  ├── CORS
  ├── Trusted Client IP / Rate Limit
  ├── Correlation ID / W3C Trace
  ├── Internal Header Sanitizing
  └── Bearer Header Forwarding
         ↓
Business Resource Server
  ├── Opaque Token Authentication
  └── @PreAuthorize / Domain Authorization
```

本设计只封板当前单层 `Nginx → Gateway` 拓扑。它不是动态路由管理、灰度发布、WAF、API Key、服务身份、
用户配额或通用授权平台。Gateway 不成为第二个 Auth。

## 2. 组件与处理顺序

| 组件 | 顺序/位置 | 职责 | 失败语义 |
|---|---:|---|---|
| `InternalHeaderSanitizingGlobalFilter` | `HIGHEST_PRECEDENCE + 10` | 清除外部身份 Header | 纯内存处理，无降级分支 |
| `BearerTokenGlobalFilter` | `HIGHEST_PRECEDENCE + 20` | 检查已存在 Authorization 的重复/明显非法格式 | 401 `invalid_bearer_token` |
| `CorrelationIdGlobalFilter` | `HIGHEST_PRECEDENCE + 30` | 校验、生成并传播 Correlation ID | 非法输入替换为 UUID |
| 路由 `RequestRateLimiter` | Route Filter | 按真实客户端 IP 使用 Redis Token Bucket | 正常超限 429；Redis 故障 503 |
| Reactive LoadBalancer | 路由阶段 | 解析 `lb://service` 并选择实例 | 无实例/连接失败不伪造成功 |
| 业务 Resource Server | 下游服务 | Opaque Token 最终认证与授权 | 认证失败 401，授权失败 403 |

全链路保持 Reactor/WebFlux；Filter 不执行 JDBC、阻塞网络或文件 I/O，不使用 Servlet API 或 ThreadLocal 请求上下文。

## 3. 路由契约

| Gateway 入口 | 下游服务 | 改写规则 | 说明 |
|---|---|---|---|
| `POST /auth/login` | `lb://mom-auth-server` | `StripPrefix=1` → `/login` | 独立、较严格 IP 限流 |
| `/auth/**` | `lb://mom-auth-server` | `StripPrefix=1` | Auth 常规 API |
| `/api/system/**` | `lb://mom-system-server` | 不改写 | 与 System Controller 的 `/api/system` 前缀一致 |
| `/api/integration/**` | `lb://mom-integration-server` | `StripPrefix=1` → `/integration/**` | Integration API |

Nacos Discovery 在 Base 默认关闭，由部署环境显式启用。服务名路由依赖 Nacos 与 LoadBalancer 均健康；发现或
连接失败不能降级为业务成功。

## 4. CORS

Gateway 使用 Spring Cloud Gateway 原生全局 CORS，不创建自定义 CORS Filter：

```yaml
spring.cloud.gateway.server.webflux.globalcors:
  add-to-simple-url-handler-mapping: true
  cors-configurations:
    '[/**]':
      allowed-origins: ${MOM_GATEWAY_CORS_ALLOWED_ORIGINS:http://localhost:5173}
      allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
      allowed-headers: [Authorization, Content-Type, X-Correlation-Id]
      exposed-headers: [X-Correlation-Id]
      allow-credentials: false
      max-age: 3600
```

正式环境必须通过 `MOM_GATEWAY_CORS_ALLOWED_ORIGINS` 配置明确 Origin，不使用通配 Origin。OPTIONS 不要求
Bearer；不在白名单中的 Origin 不获得跨域授权。

## 5. 真实客户端 IP

### 5.1 信任输入

`mom.gateway.client-ip.trusted-proxies`（环境变量 `MOM_GATEWAY_TRUSTED_PROXIES`）配置允许生成
`X-Forwarded-For` 的 Nginx/LB IPv4 CIDR。Base 为空，表示默认不信任任何代理。非法或 IPv6 CIDR 会让
Gateway 启动失败，避免错误信任边界静默生效。

Mini Gateway V1 使用 `TrustedClientIpResolver` 独立管理客户端 IP 信任边界，
`server.forward-headers-strategy` 固定为 `none`。禁止 Spring Boot 或 Reactor Netty 在该解析器执行前根据
`Forwarded` / `X-Forwarded-*` 改写 `request.remoteAddress`；解析器必须看到原始 TCP peer，才能判断实际连接
Gateway 的节点是否属于 `trusted-proxies`。这是 MOM 的安全边界决策，不依赖框架在不同部署环境中的默认值。

### 5.2 最终算法

```text
读取 TCP remoteAddress
  ├── 缺失                       → clientIp = unknown
  ├── 不属于 trusted-proxies     → 忽略 XFF，clientIp = remoteAddress
  └── 属于 trusted-proxies
       ├── XFF 是单个合法 IPv4    → clientIp = XFF
       └── XFF 缺失/空/非法/多值   → clientIp = remoteAddress
```

直接 IPv6 客户端使用 TCP IPv6 地址作为 Key，但本版不支持 IPv6 trusted-proxy CIDR，也不接受 XFF 中的 IPv6。
本版不推导多层代理链；包含逗号或重复 Header 的 XFF 一律回退 TCP 对端。

限流 Key 固定为 `ip:<clientIp>`。Gateway 不读取 Principal，也不 introspect Opaque Token。该 Key 只提供边缘
抗滥用保护：企业 NAT 下多个用户可能共享一个 Bucket，普通业务阈值应比登录接口宽松。

### 5.3 Nginx 契约

Nginx 必须覆盖公网客户端提交的转发 Header，不得把原始 XFF 拼接到当前单层信任链：

```nginx
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
```

Gateway 正式环境不直接暴露公网，网络策略只允许受信 Nginx/LB 访问。CIDR 配置与网络策略共同组成信任边界，
不能只依靠应用内 Header 解析。

Mini Gateway V1 的 Nginx/LB → Gateway 可信代理链必须使用 IPv4。单机 Nginx 不得在需要可信代理解析时依赖
`proxy_pass http://localhost:20000`，因为 `localhost` 可能解析为 `::1`；IPv4 CIDR 不会匹配该 TCP peer，
Gateway 将忽略 XFF 并把所有请求归入 `ip:::1` Bucket。单机部署应明确使用：

```nginx
proxy_pass http://127.0.0.1:20000;
```

其他部署也必须确保 Nginx/LB 到 Gateway 的 TCP peer 是 `MOM_GATEWAY_TRUSTED_PROXIES` 所覆盖的 IPv4 地址。
此限制不影响 Direct IPv6 Client：未经过可信代理的 IPv6 对端仍可直接作为 `remoteAddress` 和 Rate Limit Key。

## 6. Bearer 与身份 Header

无 Authorization 时，Gateway 继续路由并让下游决定该 API 是公开还是需要认证。存在单个格式合法的 Bearer
Header 时，Gateway 原样转发；重复 Header、非 Bearer Scheme、空 Token 或包含空白的 Token 返回稳定 401。
这一步不证明 Token 有效。

Gateway 删除所有 `X-MOM-*`，以及 `X-User-Id`、`X-User-Name`、`X-Role(s)`、`X-Permission(s)`、
`X-Factory-Id`、`X-Party-Id`。`Authorization`、`X-Correlation-Id` 与普通业务 Header 继续传播。业务服务只从
Resource Server 建立的 Authentication 获取可信身份。

## 7. Correlation 与 Trace

`X-Correlation-Id` 最长 64 字符，只允许字母、数字、点、下划线、冒号和连字符；缺失或非法时生成 UUID，并写入
下游请求与客户端响应。它只用于日志与排障关联。W3C `traceparent` 由 Spring Boot、Micrometer 和 Gateway
响应式 HTTP 链传播，业务代码不手工拼接 Trace Header。

## 8. 配置清单

| 环境变量 | 默认值 | 生产要求 |
|---|---|---|
| `NACOS_DISCOVERY_ENABLED` | `false` | 显式启用并验证 Nacos 健康 |
| `NACOS_PASSWORD` | 空 | 从 Secret 来源注入 |
| `REDIS_PASSWORD` | 空 | 从 Secret 来源注入 |
| `MOM_GATEWAY_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 改为受控正式 Origin |
| `MOM_GATEWAY_TRUSTED_PROXIES` | 空 | 配置真实 Nginx/LB IPv4 CIDR |

CORS 与可信代理属于安全关键配置，不支持不受控动态刷新。Base 不激活 Profile，也不保存可工作的基础设施密码。

## 9. 验证与已知限制

自动测试覆盖：合法/非法 CORS 预检、无 Bearer OPTIONS、Bearer 缺失/合法/重复/非法、身份 Header 大小写清理、
Correlation ID 输入保护、直连/可信代理/不可信代理/伪造与异常 XFF、不同客户端 Bucket、IPv6 直连、非法 CIDR
启动前失败、Redis RateLimiter fail closed，以及 WebFlux 架构约束。

当前已知限制：

- 429 保留 Spring Cloud Gateway 默认响应，未统一为 MOM JSON；
- 不支持多层代理链推导；
- 不支持 IPv6 trusted-proxy CIDR 或 XFF IPv6；
- 单元/组件测试不替代真实 Nginx、Nacos 与 Redis 部署验收。

## 10. 官方依据

- [Spring Cloud Gateway CORS Configuration](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html)
- [Spring Cloud Gateway RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)
- [Spring Cloud LoadBalancer](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html)
