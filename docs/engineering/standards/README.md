# MOM Platform 官方工程规范索引

本目录将官方版本事实与 MOM 项目决策分开记录。修改相关技术栈前，先阅读来源政策，再阅读对应版本规范。

- `official-source-policy.md`：官方证据优先级、版本声明和升级规则；
- `jdk-25-engineering-standard.md`：Java 25、Preview、内部 API、Virtual Thread、JFR；
- `spring-boot-4.1-engineering-standard.md`：BOM、配置、测试分层、Actuator、可观测性；
- `spring-cloud-2025.1-engineering-standard.md`：Release Train、兼容验证、Gateway、OpenFeign、服务发现；
- `spring-cloud-alibaba-2025.1-engineering-standard.md`：官方矩阵、MOM 已验证组合、Nacos Config、Seata、RocketMQ。
- `module-layering-standard.md`：api/client/server/framework 职责、Server 分层、依赖方向与历史例外；
- `http-api-contract-standard.md`：路径、DTO、校验、错误、状态码、分页、排序与过滤；
- `api-evolution-idempotency-standard.md`：HTTP 幂等、OpenAPI、兼容、弃用与删除条件。
- `persistence-data-modeling-standard.md`：数据所有权、命名、类型、Entity、Mapper/Repository、SQL 与 Flyway；
- `transaction-consistency-standard.md`：本地事务、Outbox/Inbox、Seata 与 SAS JDBC Store 特殊边界；
- `audit-concurrency-lifecycle-standard.md`：并发控制、三类审计、Actor、删除、归档与保留。
- `configuration-profile-secret-standard.md`：配置来源、Profile、Secret、Nacos 与动态刷新边界；
- `security-protocol-runtime-standard.md`：IAM 端点矩阵、FilterChain、Gateway、Resource Server 与 Actuator；
- `outbound-http-client-standard.md`：OpenFeign 位置、超时、重试、错误映射、凭证与 Fallback；
- `redis-key-ttl-failure-standard.md`：Redis Key、TTL、序列化、原子性、故障矩阵与低阶幂等边界。
- `testing-strategy-standard.md`：L0～L6 测试证据、替身、契约和覆盖率边界；
- `maven-test-lifecycle-standard.md`：Surefire/Failsafe 3.5.4、命名、生命周期与 Codex 日志；
- `testcontainers-smoke-acceptance-standard.md`：Testcontainers、打包 Smoke 与真实基础设施验收；
- `ci-scope-quality-gate-standard.md`：增量/完整范围、条件 Job、手动验收与质量门禁。
- `localization-locale-standard.md`：BCP 47 支持列表、选择回退、资源与错误国际化边界；
- `timezone-date-time-standard.md`：UTC、用户/Factory/设备时区、业务日期、DST 与 API 时间契约；
- `number-money-rounding-standard.md`：Decimal String、BigDecimal、金额、比例和舍入边界；
- `measurement-unit-standard.md`：MDM 单位所有权、UCUM、量纲、换算与历史快照；
- `user-preference-standard.md`：System 偏好所有权、覆盖层级、授权隔离和 S16 进入条件。

本索引只负责导航，强制规则以根目录 `AGENTS.md` 和对应规范正文为准。
