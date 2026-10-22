# mom-tracing：当前 Trace 快照

**状态：多服务装配；未确认业务代码直接调用。** 模块提供 `CurrentTraceContext.snapshot()`、`TraceContextSnapshot` 和 Tracer 自动配置。源码入口为 [`mom-tracing`](../../mom-framework/mom-tracing/)。

- `snapshot()` 只读取当前 Span/Tracer 的标识；无活动上下文时返回空快照，不主动创建 Span，也不生成业务 Correlation ID。
- 依赖 OpenTelemetry Starter 与 Observation，但 Maven 依赖或 Bean 存在不证明 OTLP 后端可达或跨服务链路完整。运行环境需核查采样、传播和导出配置。
- Trace ID 只用于排障，不得作权限、幂等或业务关联键；敏感内容不得进入 Span 属性。运行边界见[可观测性架构](../architecture/可观测性架构.md)。
