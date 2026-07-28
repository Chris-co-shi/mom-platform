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

本索引只负责导航，强制规则以根目录 `AGENTS.md` 和对应规范正文为准。
