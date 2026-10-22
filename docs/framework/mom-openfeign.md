# mom-openfeign：服务调用的请求上下文传播

**状态：`*-client` 装配采用。** 本模块仅有 `MomFeignAutoConfiguration`，提供 Feign 请求拦截器自动配置；不定义业务 Client、服务发现规则或远端 API。源码入口为 [`mom-openfeign`](../../mom-framework/mom-openfeign/)。

- 接入：仅用于 MOM 内部同步 HTTP 调用，业务调用方依赖所属 Owner 的 `*-client` 契约。Feign 拦截器传播当前 `X-Correlation-Id`；当前 Servlet 请求有 Bearer 且目标请求未显式设置 Authorization 时，传播原始 Bearer。服务端仍由 Resource Server 验证，不能假设传播即授权。无请求上下文时不会签发服务身份。
- 失败与限制：本模块不保证网络重试、幂等、降级、事务或结果补偿；远端 401/403、超时和不可用由调用方按用例处理。写请求默认不自动重试。

不得将该拦截器用于 SAP、LIMS、PCS、AGV 等外部系统调用，避免泄露当前用户凭证。

强制的超时、凭证、错误映射与 Fallback 约束见[出站 HTTP 规范](../engineering/standards/outbound-http-client-standard.md)。
