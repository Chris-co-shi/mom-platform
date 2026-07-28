# 测试策略与证据分层规范

- 状态：Current
- 生效 Slice：P1.6 S04
- 决策：[ADR-022](../../adr/ADR-022-测试分层与CI质量门禁.md)

## 1. 分层与命名

L0 静态/编译、L1 单元、L2 组件、L3 模块集成、L4 打包 Smoke、L5 真实基础设施验收、L6 跨仓库 E2E 是递增证据，不可互相替代。L1/L2 使用 `*Test`、`*Tests`；需要 Docker 或真实模块基础设施的 L3 使用 `*IT`、`*ITCase`。脚本式 L4/L5 不伪装为 JUnit 测试。

## 2. 快速测试

L1 不启动 Spring Context、Docker或网络，时间、随机数可控制。L2 可使用 `ApplicationContextRunner`、MockMvc、WebTestClient、Spring Slice 和 Mock Adapter，但必须限制 Context 数量。普通 `mvn test` 在无 Docker、中间件和公网时可重复执行。

测试不得依赖顺序、共享可变全局状态、默认 Locale/时区/Charset或任意 sleep。时间优先注入 Clock，异步使用有界条件轮询；端口动态分配，测试数据使用唯一前缀。全局并行默认关闭，只有资源隔离与线程安全有证据时局部开启。

## 3. 替身与真实证据

- Mock：验证编排、调用、错误映射，不证明协议。
- Fake：必须显式标识并描述与生产实现的差异。
- Spring Cloud Stream Test Binder：只证明 Binding、转换和应用路径，不证明 RocketMQ Retry、DLQ、Ordering 或网络恢复。
- H2：不作为 PostgreSQL SQL 兼容证据；Repository SQL 优先 PostgreSQL Testcontainers。
- Bean 创建：不证明 Redis/Nacos/Seata 故障策略。

## 4. 契约测试

契约测试覆盖 DTO 序列化、状态码、错误 code、安全要求、Feign 请求/响应、事件 Envelope，以及 OAuth2/OIDC 标准错误不被 MOM 包装。没有稳定 OpenAPI 产物时不伪造契约 Diff；Spring Cloud Contract 等待真实消费者/提供方需求。

## 5. 覆盖率

不设无依据的“全仓 80%”。新领域规则、安全/并发/事务/错误路径、公开契约和 Bug Fix 必须有测试。覆盖率只用于发现盲区，不能以无断言测试刷高；S04 不引入 JaCoCo。

## 6. 失败证据

失败报告只包含请求状态、容器状态、有界日志和测试报告，不输出 Secret、Token 或 Authorization。相同代码状态下不得无分析重复运行同一失败超过两次。
