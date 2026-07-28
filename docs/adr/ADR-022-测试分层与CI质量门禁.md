# ADR-022：测试分层与 CI 质量门禁

- 状态：Accepted
- 日期：2026-07-29
- 决策 Slice：P1.6 S04

## 背景

Phase 01 已形成单元测试、Spring 组件测试、Testcontainers、打包 Smoke 和真实基础设施验收，但命名、Maven 生命周期和 CI 失败面尚未统一。原 Nacos/Redis 组合 Job 在 Nacos 容器启动失败时会阻断 Redis 幂等与限流证据，且 Docker 测试以 `*IntegrationTest` 命名进入 Surefire。

## 决策

MOM 采用 L0～L6 证据层级，并坚持低层证据不能替代高层结论：

| 层级 | 证据 | 执行边界 |
|---|---|---|
| L0 | Enforcer、编译、静态门禁、ArchUnit、脚本正反例 | 不证明运行时集成 |
| L1 | 无 Spring、Docker、网络的纯单元测试 | Surefire，`*Test`/`*Tests` |
| L2 | ContextRunner、MockMvc/WebTestClient、Slice、Mock Adapter | Surefire，不宣称真实中间件 |
| L3 | 模块与真实 PostgreSQL/Redis 等基础设施集成 | Failsafe + Testcontainers，`*IT`/`*ITCase` |
| L4 | 实际 `*-exec.jar`、真实端口和关键入口 | 独立打包 Smoke |
| L5 | Nacos、Redis 多实例、RocketMQ、Seata、可观测栈 | 独立基础设施验收 Job |
| L6 | platform/web/mobile/infra 跨仓库 E2E | S04 只定义；IAM 全协议进入 S10 |

Surefire 3.5.4 只执行 `*Test`、`*Tests`；Failsafe 3.5.4 执行 `*IT`、`*ITCase`，绑定 `integration-test` 与 `verify`。普通 `mvn test` 不要求 Docker，`mvn verify` 才进入 L3。

主 CI 将 Nacos Discovery、Redis Idempotency、Redis Rate Limit 拆为三个并行、独立结论。Scope Detector 输出独立基础设施范围；首次 PR、不可达旧 Head、force-push 风险和手动 `all` 使用完整范围，长期 PR 的可达 synchronize 使用 Previous Head → New Head。

## 质量门槛

每次 PR 必须执行 Engineering Baseline、Fast Reactor、架构及静态门禁；基础设施范围按变化条件执行。`skipped` 必须如实记录。手动 `all` 执行全部范围。S04 最新 Head 必须通过三项拆分 Smoke 和主 CI，历史 Nacos readiness 豁免不再适用。

## 取舍

- 不设置无证据的全仓覆盖率百分比，不在 S04 引入 JaCoCo、Sonar、Mutation Testing 或 Spring Cloud Contract。
- 仍被真实 Smoke 使用的技术探针保持默认关闭并精确登记，不为消除例外删除或改造为业务 API。
- Test Binder、Mock、H2、Bean 创建和编译结果只证明相应层级，不能提升为真实基础设施结论。

## 官方依据

- [Maven Surefire Test Selection](https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html)
- [Maven Failsafe Usage](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html)
- [Testcontainers JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/)
- [GitHub Actions Workflow Syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [Nacos 3.1 Docker Quick Start](https://nacos.io/en/docs/v3.1/quickstart/quick-start-docker/)

## 后果

测试文件必须按证据层级命名；新增 Docker 测试不能混入 Surefire。基础设施 Job 必须有 timeout、cleanup 和独立失败 Artifact。S05 只有在本 ADR 的 S04 CI 门槛全部通过后才可开始。
