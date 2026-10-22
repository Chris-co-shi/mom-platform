# mom-observation：Actuator 依赖聚合

**状态：依赖模块，无自有运行时 Java API。** [`pom.xml`](../../mom-framework/mom-observation/pom.xml) 聚合 `mom-core` 与 Spring Boot Actuator；`src/main/java` 无实现。业务模块不能从本模块寻找统一埋点 Service。

若目标服务需要 Observation，应核查其 Actuator 配置、具体埋点、导出器与运行环境。真正的指标名称和 Trace 快照入口分别见 [mom-metrics](mom-metrics.md)、[mom-tracing](mom-tracing.md)；监控验收见[可观测性架构](../architecture/可观测性架构.md)。
