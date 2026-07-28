# Spring Cloud 2025.1 工程规范

## 官方兼容基线

Spring Cloud 官方兼容矩阵说明：`2025.1.x` 支持 Spring Boot `4.0.x`，从 Spring Cloud `2025.1.2` 开始支持 Spring Boot `4.1.x`。

当前 MOM 组合 `Spring Cloud 2025.1.2 + Spring Boot 4.1.0` 属于官方支持组合。

官方来源：

- Spring Cloud 项目页与兼容矩阵：https://spring.io/projects/spring-cloud/
- Spring Cloud 2025.1.2 发布说明：https://spring.io/blog/2026/06/11/spring-cloud-2025-1-2-aka-oakwood-has-been-released/
- Spring Cloud Commons：https://docs.spring.io/spring-cloud-commons/reference/

## 依赖与兼容

- 所有 Spring Cloud 组件版本由 `spring-cloud-dependencies` Release Train BOM 管理。
- 禁止为 Gateway、OpenFeign、LoadBalancer、CircuitBreaker、Config 等组件单独指定版本。
- 禁止关闭或绕过 Spring Cloud Compatibility Verifier。
- 升级 Boot 或 Cloud 必须先核对官方兼容矩阵，再执行真实基础设施回归。

## 服务发现与调用

- 服务发现、负载均衡和实例选择由 Spring Cloud 抽象及受控实现负责，业务代码不得手工操作注册中心实例列表。
- OpenFeign 必须配置连接超时、读取超时和有限重试；错误解码不得吞掉远端失败。
- 无可用实例、超时、熔断和降级必须有明确的 fail-open/fail-closed 语义。
- Fallback 不得伪造业务成功，不得把远程写失败转换成成功响应。

## Gateway

- Gateway 必须保持 Reactor/WebFlux 模型。
- 禁止在 Event Loop 中执行 JDBC、阻塞文件 I/O、阻塞式 HTTP 或长时间 CPU 任务。
- 鉴权、限流、路由和观测 Filter 必须保持短执行路径，并明确顺序和失败策略。

## 配置与测试

- 配置导入使用 Config Data 机制；不得恢复旧 Bootstrap 引导模式。
- 普通单元测试关闭服务发现和远程配置，不启动 Nacos。
- 服务注册、发现、服务名路由、Feign、负载均衡和熔断结论必须通过独立 Smoke Test 验证。
