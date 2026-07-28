# Spring Boot 4.1 工程规范

## 官方来源

- Spring Boot 4.1 Reference：https://docs.spring.io/spring-boot/reference/
- Testing：https://docs.spring.io/spring-boot/reference/testing/
- Externalized Configuration：https://docs.spring.io/spring-boot/reference/features/external-config.html
- Observability：https://docs.spring.io/spring-boot/reference/actuator/observability.html
- Graceful Shutdown：https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html

## 依赖管理

- Spring Framework、Spring Security、Jackson、Reactor、Micrometer 等版本默认由 `spring-boot-dependencies` 管理。
- 禁止无依据覆盖 Boot BOM 管理的版本。
- 覆盖版本必须有官方 Issue 或发布说明、ADR、兼容性测试和回退方式。

## 应用结构

- 主应用类位于服务根包，组件扫描只覆盖本服务和明确引入的 Framework 自动配置。
- 禁止扫描整个 `io.github.chrisshi.mom`，避免跨领域 Bean 泄漏。
- Gateway 保持 WebFlux；Servlet/WebMVC 依赖不得进入 Gateway。

## 配置

- 复杂配置使用 `@ConfigurationProperties` 和校验，业务代码不得散落大量 `@Value`。
- 项目配置前缀使用受控 `mom.*` 命名空间。
- 密钥、Token、密码和生产地址不得提供可工作的仓库默认值。
- 自动配置应使用条件注解、配置元数据和明确的启停属性。

## 测试分层

- 纯单元测试不启动 Spring、不访问外部数据库、不启动容器。
- Web、JSON、配置绑定和数据访问优先使用对应 Test Slice，只加载需要的上下文。
- `@SpringBootTest` 只用于确需验证完整应用上下文的场景。
- 真实 PostgreSQL、Redis、Nacos、Seata 等测试使用 Failsafe 或独立 Smoke Workflow，不进入默认 Surefire 单元测试范围。
- Integration Test 必须使用隔离数据库或 schema、独立初始化和可重复清理；不得操作共享或 Flyway 内置系统数据。

## 运行与可观测性

- 使用 Spring Boot Actuator 暴露受控 health、readiness、liveness、metrics 和 prometheus。
- 敏感 Actuator 端点不得公网开放。
- 保持默认优雅停机，并根据部署终止窗口设置 `spring.lifecycle.timeout-per-shutdown-phase`。
- 业务观测使用 Micrometer Observation/Tracing，不直接管理 OpenTelemetry SDK 生命周期。
- 指标标签必须低基数，敏感信息不得进入日志、指标或 Span。
