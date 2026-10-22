# mom-webmvc：HTTP 适配

**状态：多 Server 基础设施采用。** 模块提供 `Result<T>`、Correlation ID Filter、I18n 和 Code Rule 的 HTTP Controller/Advice。源码入口为 [`mom-webmvc`](../../mom-framework/mom-webmvc/)；它只负责 Web 边界，不承载业务事务。

- 返回契约：MOM 第一方业务/管理 Controller 成功使用 `Result.success(data)` 或 `Result.success()`；业务失败使用 `Result.failure(code, message[, data])`，并保持真实 HTTP 状态。`Result` 字段为 `code/message/data`，成功码为 `"0"`。Application 不返回该类型。完整规则见[HTTP API 规范](../engineering/standards/http-api-contract-standard.md)。
- 相关入口：`CorrelationIdFilter` 处理请求关联标识；I18n 管理和运行时 Controller 由 I18n Bean 条件装配；Code Rule HTTP Controller 要求 `mom.code-rule.enabled=true` 且 Resource Server 启用。Owner 权限与业务规则仍由所在服务提供。
- 限制：I18n SSE 通知器是单 JVM 本地状态，不能据此宣称跨实例实时传播。认证过滤器错误、历史不一致端点与业务 `Result<T>` 的边界见 HTTP API 规范；不要把旧端点当新接口模板。

业务模块只通过公开 Controller/Advice 契约接入，不直接调用本模块的内部自动配置和通知器实现。
