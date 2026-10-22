# mom-logging：日志依赖聚合

**状态：Gateway 装配；无自有运行时 Java API。** [`pom.xml`](../../mom-framework/mom-logging/pom.xml) 只声明 `mom-core` 依赖；`src/main/java` 无实现，也没有统一 Logger、Appender 或日志格式自动配置。

业务服务继续使用既有日志配置。不能因为依赖此模块就宣称日志已结构化、带 Trace ID 或已导出；实际格式、隐私和采集在目标服务及部署环境核查，见[可观测性架构](../architecture/可观测性架构.md)。
