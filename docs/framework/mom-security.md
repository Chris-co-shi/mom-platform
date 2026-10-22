# mom-security：Opaque Token 与 Resource Server

**状态：Auth 与业务 Server 基础设施采用。** 模块提供 `MomTokenStore`、Redis 实现、`MomOpaqueTokenIntrospector`、当前 Actor 适配和 Servlet Resource Server 自动配置。源码入口为 [`mom-security`](../../mom-framework/mom-security/)；业务服务中的 `@PreAuthorize` 仍由所属服务定义。

- 接入：启用 `mom.security.resource-server.enabled`，核查 Redis Token Store、自定义 `SecurityFilterChain`、公开路径和方法权限。Gateway 仅转发请求，业务 Resource Server 必须最终校验 Token 与权限；自定义过滤器链可能影响默认链装配。
- 生命周期：Auth 负责签发/撤销 Redis Opaque Token；Resource Server 的 introspector 读取 Token Store 并构造 Principal。签发时权限快照与刷新语义按[安全运行规范](../engineering/standards/security-protocol-runtime-standard.md)执行。
- 失败：Token 不存在、过期或存储不可用不能放行。认证/授权失败由 Security Filter Chain 的边界处理，不由普通 ControllerAdvice 覆盖；这类协议响应须与业务 `Result<T>` 契约分别核查。

**禁止绕过：** 不以 Gateway 检查、前端按钮可见性或自建 Token 解析替代 Resource Server。Token 原文、凭证和权限敏感信息不得写入日志、Trace 或消息。
