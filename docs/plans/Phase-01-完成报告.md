# Phase 01：技术骨架完成报告

- 阶段状态：Completed
- 完成日期：2026-07-19
- 基线：JDK 25、Spring Boot 4.1.0、Spring Cloud 2025.1.2、Spring Cloud Alibaba 2025.1.0.0

## 1. 结论

Phase 01 已完成。MOM Platform 当前具备进入 Phase 02 业务垂直切片所需的构建、服务治理、安全、数据访问、可靠消息、受控分布式事务和可观测性基础能力。

结论来自真实打包应用和真实中间件 CI，不是仅依据依赖能够编译或 Spring Bean 能够创建。

## 2. 已交付能力

### 2.1 工程与架构

- Maven Reactor 与统一 BOM；
- JDK 25 编译、单元测试、集成测试和 Enforcer；
- Framework、API、Client、Server 依赖方向；
- ArchUnit 架构边界；
- Java 中文 Javadoc 与第三方依赖治理规则。

### 2.2 服务、安全与治理

- Gateway WebFlux 路由；
- Nacos 注册发现；
- OAuth2 Resource Server 最小闭环；
- 用户、Client、工厂和关联上下文；
- 统一 401、403、429 和 5xx；
- Redis 分布式限流和幂等占位。

### 2.3 数据与事务

- PostgreSQL 每服务独立 Schema；
- Flyway 迁移；
- MyBatis-Plus 与 String ID 实体基线；
- 单服务单 DataSource、单 HikariCP；
- UTC 会话、TCP Keepalive、ApplicationName 与连接预算；
- Seata AT 两数据库受控短事务 PoC。

### 2.4 可靠消息

- 业务写入与 Outbox 同一本地事务；
- 租约式 Outbox 领取与 CAS；
- Spring Cloud Stream + RocketMQ；
- Inbox 消费幂等；
- 重复投递、Broker 中断恢复、消费重试和 DLQ。

### 2.5 可观测性

- Micrometer Observation/Tracing；
- Spring Boot OpenTelemetry Starter；
- Gateway、HTTP、OpenFeign、Outbox 与 RocketMQ Consumer Trace；
- OpenTelemetry Collector → Tempo；
- Actuator → Prometheus；
- Alloy → Loki；
- Grafana 三数据源 Provisioning；
- `mom-platform-overview` 平台总览；
- Trace-to-Logs、Trace-to-Metrics 与日志 Trace 跳转；
- 服务不可用、HTTP 5xx 和 Hikari 连接池告警基线。

## 3. 真实验证矩阵

| 工作流 | 验证内容 |
|---|---|
| CI | JDK 25 `clean verify`、架构边界、Nacos、Redis、PostgreSQL、Flyway、Hikari |
| Messaging CI | Outbox、RocketMQ、Inbox、重复投递、Broker 恢复、重试、DLQ |
| Seata CI | 两数据库全局提交、参与者失败、远端成功后回滚、Undo Log、TC 中断 |
| Observability CI | W3C Trace、Gateway、OpenFeign、MDM、Collector、Tempo、Collector 中断 |
| Observability Stack CI | Prometheus、Alloy、Loki、Grafana、三类信号代理查询、真实服务宕机告警 |

## 4. 关键故障场景

已自动化验证：

- Redis 不可用；
- PostgreSQL 不可用；
- RocketMQ Broker 中断与恢复；
- 同一消息重复投递；
- 消费者持续失败进入 DLQ；
- Seata TC 中断；
- OpenTelemetry Collector 中断；
- MDM 服务停止后 Prometheus 告警 firing。

## 5. 关键架构决策

- 本地事务与 Outbox/Inbox 是跨服务一致性的默认方案；
- Seata 仅允许受控短同步事务，不进入长制造流程；
- 普通领域服务只有一个权威 DataSource；
- Trace ID 不作为业务主键、权限主体或幂等键；
- Prometheus 与 Loki 只索引低基数标签；
- 观测后端故障不得改变业务事务或消息语义；
- Grafana 核心数据源和 Dashboard 使用代码 Provisioning。

## 6. 已知未完成项

以下内容不是 Phase 01 缺陷，而是明确留给部署或后续业务阶段：

- k3s 生产式观测栈部署；
- 持久卷、对象存储、长期保留与多副本；
- Alertmanager 通知渠道和值班策略；
- MES、WMS、QMS 领域 Dashboard；
- 业务 SLI/SLO；
- 完整 IAM 管理页面；
- 生产级高可用 Nacos、Redis、PostgreSQL、RocketMQ 和 Seata 集群。

## 7. Phase 02 启动建议

Phase 02 应从“供应商送货 → 收货 → 来料检验 → 合格入库”最小垂直切片开始，而不是先批量实现主数据 CRUD。

首个业务切片必须同时完成：

- 领域模型和状态机；
- PostgreSQL 迁移；
- 幂等请求；
- 本地事务与 Outbox；
- 消费 Inbox；
- Trace、日志和低基数业务指标；
- 重复提交、异常回滚和消息重复场景；
- 可通过 Gateway 重复演示的端到端验收。
