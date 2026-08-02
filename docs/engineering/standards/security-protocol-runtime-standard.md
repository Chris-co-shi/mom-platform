# IAM、安全协议、Gateway 与 Resource Server 运行规范

- 状态：Current
- 生效 Slice：P1.6 S03
- 兼容权威：[P1.5 认证与授权设计基线](../../security/P1.5-认证与授权设计基线.md)、[ADR-019](../../adr/ADR-019-P1.5认证与授权闭环.md)

## 1. 适用边界

本文冻结现有协议的配置与运行边界，不删除、合并或弃用认证入口，不改变 OAuth2/OIDC 标准响应、Claims、四个 Public Client、Token/Session/Refresh 生命周期、Rotation/Reuse Detection 或 Permission。

Spring Security/SAS 的端点、FilterChain 和错误处理属于框架事实；MOM 对 Issuer、Audience、Fail Closed、入口隔离和最终授权的要求属于项目决策；下表是当前实现快照。协议长期裁决属于 S07，配置类拆分属于 S08。

## 2. 当前端点与 SecurityFilterChain 矩阵

| 路径/资源 | Chain 与顺序 | 公开性/认证 | CSRF / CORS | Session | 错误格式 | Client/限流/审计 | 后续 |
|---|---|---|---|---|---|---|---|
| `/oauth2/authorize`、`/oauth2/token`、`/oauth2/introspect`、`/oauth2/revoke`、`/oauth2/par`、设备端点 | `iamAuthorizationServerSecurityFilterChain`，`HIGHEST_PRECEDENCE`，SAS endpoints matcher | 依协议和 Client 认证 | SAS/安全链现状；不由普通 Advice 覆盖 | 授权页面可用 IAM Session；Token 协议按 SAS | OAuth2 标准错误 | 四个预置 Public Client；认证/Session 审计 | Current Required；S07 输入 |
| `/.well-known/**`、`/oauth2/jwks`、OIDC UserInfo/Logout | 同上 | Discovery/JWK 按 SAS 公开；UserInfo/Logout 按协议认证 | SAS 现状 | 按协议 | OIDC/OAuth2 标准格式 | JWK 只暴露公钥 | Current Required |
| `/api/iam/auth/login`、`password/change-required`、`refresh` | `iamDirectAuthenticationSecurityFilterChain`，Order 1 | 当前公开 | CSRF 当前禁用；Gateway CORS 边界另管 | Stateless | 当前第一方 JSON 错误 | 当前兼容 Client/限流/审计逻辑 | Compatibility Boundary；S06/S07/S09 输入 |
| `/api/iam/auth/logout` | 同上 | Bearer | CSRF 当前禁用 | Stateless | 当前 JSON | 撤销 sid 并审计 | Current Required |
| `/login`、`/password/change`、`/logout`、静态页面 | `iamLoginAndApiSecurityFilterChain`，Order 2 | 登录页公开；其余按当前认证 | 表单与 Cookie Chain 保持 Spring Security CSRF | IAM HttpSession | HTML/重定向 | IAM 页面；Cookie | Compatibility Boundary；S08 |
| `/api/iam/me` | Order 2 | 已认证 Bearer/当前链 | 当前 Chain CSRF 策略保持 | 不把 Cookie 扩展为 API BFF Session | 当前 JSON | 四 Client；读取授权上下文 | Current Required |
| `/api/iam/admin/**` | Order 2 | 认证 + 当前 Permission | 当前 Chain | 当前实现 | 已发布 `{code,message}` 兼容错误 | 管理 Client、细粒度权限、安全审计 | S09 Migration Input |
| `/actuator/health/**`、`/actuator/info` | IAM/Gateway/业务服务各自 Chain | 当前公开最小健康集合 | 不以 CORS 替代保护 | 无业务 Session | Actuator | 不依赖 revoked sid | Current Required |
| `prometheus`、`nacos-discovery`、`bindings` | Actuator 暴露配置 | 网络/部署层必须保护 | N/A | N/A | Actuator | 运维调用方 | `bindings` S04 Review |
| 技术探针 | 条件 Bean/Filter；默认关闭 | 启用时仍受已配置链与过滤器 | 当前策略 | 不创建身份协议 | 明确失败 | 仅技术验收 | P1.7 Deferred/按 Slice 删除证据 |
| 公开静态资源与 `/error` | Order 2 或 Gateway Chain | 当前 matcher | 默认安全 Header | IAM 页面可用 Session | HTML/框架错误 | 浏览器 | S08 Review |

SAS 端点以当前 `AuthorizationServerSettings` 和 Discovery 输出为准；不得由文档复制出的路径替代运行时协议元数据。任务指定的旧独立 Gateway/Resource Server 协议文件在仓库中不存在，本规范连同 P1.5 基线成为当前集中权威入口。

## 3. Issuer、JWK 与 JWT

1. Issuer 是稳定 URI；正式外部 Issuer 必须 HTTPS，不随 Pod、主机或临时端口变化。
2. Gateway 和业务 Resource Server 均验证签名、Issuer、Audience 和时间；业务服务不得只信任 Gateway。
3. JWT 算法使用允许列表；拒绝 `none`，不得由 Token Header 任意选择算法。当前签发为 RS256，S03 不改变。
4. JWK 使用稳定非空 `kid`；私钥不进入 Git、日志、Trace、错误或响应；JWK Set 只暴露公钥。
5. 测试密钥只存在测试资源/`test`；正式 Profile 启动必须验证 Issuer、私钥、公钥、`kid`、Pepper 且拒绝测试密钥与本地 Pepper。
6. 轮换必须保留旧公钥验证窗口；S03 不实施轮换，也不修改 Claim/TTL。

Spring Security 官方事实：同时配置 `issuer-uri` 与 `jwk-set-uri` 可避免启动时通过 Discovery 获取 JWK，同时仍校验 `iss`；Audience 需额外配置或验证器。MOM 现有 Decoder 使用显式 JWK URI 和 `MomJwtValidators` 校验 Issuer/Audience。

## 4. Redirect URI、Cookie、CSRF、CORS 与 Header

### 4.1 Redirect URI

必须精确匹配、禁止通配。正式 Web 使用 HTTPS；loopback 只用于本地；Native 自定义 Scheme/HTTPS App Link 按 Client 登记。变化属于协议兼容事件，S03 不改四个 Client URI。

### 4.2 Cookie

IAM Cookie 只维护 IAM 页面和基础 SSO：`HttpOnly`，Path/Domain/SameSite 显式，正式 `Secure=true`；不保存 Access/Refresh Token，不成为 Gateway API 凭证，不扩展为 BFF Session。

### 4.3 CSRF

Spring Security 对 Servlet unsafe 方法默认提供 CSRF 防护。IAM 表单/Cookie 页面必须保持防护；纯 Bearer、Stateless Resource Server 可禁用。不得全局关闭后假设所有端点均无 Cookie。第一方 JSON 链当前禁用 CSRF 是兼容现状，S03 只冻结匹配和测试。

### 4.4 CORS

正式 Origin 精确白名单，禁止 `*` 与 credentials 同时使用；预检仅开放必要方法/Header。CORS 不是认证授权，不能动态信任请求 Origin。Gateway 是浏览器跨域主边界；Spring Security 官方要求 CORS 早于安全处理，以免无 Cookie 的预检被误拒绝。

### 4.5 安全 Header

保留 Spring Security 默认安全响应 Header，按页面需求做精确覆盖。客户端输入的 `X-MOM-*`、用户、Role、Permission、Factory/Party 身份 Header 均不可信；Gateway 清理后，业务服务仍以 JWT 为权威。Bearer/Cookie 不得记录。

## 5. Gateway

Gateway 负责 JWT 协议校验、Issuer/Audience/时间、revoked sid、Client 粗粒度入口、CORS、路由、限流、Correlation/Trace、清除伪造 Header并原样转发 Bearer。它不负责最终 Permission、Factory/Party 对象归属、业务状态、菜单或偏好。

- 保持 WebFlux，不引入 Servlet/WebMVC。
- 不签发 Token、不改 Claim、不把 Header 当服务身份。
- 401 表示缺失/无效认证，403 表示已认证但入口被拒；Redis revoked sid 失败对受保护 API 返回 503 并 Fail Closed。
- 健康检查不被 revoked sid 阻断；技术探针默认关闭；路由/发现故障不得返回伪成功。

## 6. 业务 Resource Server

正式业务服务必须再次验证 JWT Issuer、Audience、时间和 revoked sid（或未来经决策的等价机制），并执行最终 Permission、Factory、Party、对象归属和业务审计。`/internal/**` 不能只依赖网络不可达；服务身份协议未冻结前不得信任固定 Header。

当前只有 MDM、WMS、QMS、Integration 的 `phase02` Profile 显式启用共享 Servlet Resource Server；S03 不为其他骨架批量启用。当前共享实现完成 JWT/Issuer/Audience，业务级 revoked sid 覆盖仍需逐服务调用方证据，登记为 S06/S08 输入，不在 S03 改生产行为。

## 7. Actuator 与技术端点

| 端点 | Base/生产决策 |
|---|---|
| health/liveness/readiness | 可暴露最小详情；不得泄露拓扑、凭据、栈 |
| info/prometheus | 仅在部署网络/认证保护下开放；指标不得含敏感或高基数标签 |
| nacos-discovery/bindings | 技术运维端点；必须显式需要且受保护 |
| env/configprops/loggers/heapdump/threaddump/mappings | 不得无保护公开 |
| shutdown | 禁止 |

技术探针与 Actuator 分离，默认不注册；健康探测不能触发业务写入或依赖用户 Session。

## 8. 401/403 与协议错误边界

Gateway/Resource Server 使用 Spring Security AuthenticationEntryPoint/AccessDeniedHandler 语义；OAuth2/OIDC 标准端点保留 SAS 标准错误，不经普通 ControllerAdvice 包装。IAM Admin 已发布错误模型保持兼容。任何依赖故障不得转换为 HTTP 200 或业务成功。

## 9. 官方依据

- [Spring Security 7 Resource Server JWT](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html)
- [Spring Security 7 CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)
- [Spring Security 7 Reactive CORS](https://docs.spring.io/spring-security/reference/7.0/reactive/integrations/cors.html)
- [Spring Security 7 Response Headers](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/headers.html)
- [Spring Authorization Server Configuration Model](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [Spring Authorization Server Protocol Endpoints](https://docs.spring.io/spring-authorization-server/reference/protocol-endpoints.html)
