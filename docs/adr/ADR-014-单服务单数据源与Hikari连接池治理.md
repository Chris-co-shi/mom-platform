# ADR-014：单服务单数据源与 Hikari 连接池治理

- 状态：Accepted
- 日期：2026-07-18
- 决策人：项目维护者
- 关联需求：Phase 01 数据与消息基础设施
- 关联文档：[数据架构](../architecture/数据架构.md)、[ADR-004](ADR-004-PostgreSQL按服务隔离Schema.md)、[ADR-005](ADR-005-Outbox与Inbox消息一致性.md)

## 1. 背景

MOM Platform 在 V1 中使用共享 PostgreSQL 集群、按服务隔离 Schema。随着领域服务、Pod 副本数和 Outbox 发布器逐步增加，如果每个服务随意创建多个 DataSource 或沿用过大的连接池默认值，会快速消耗 PostgreSQL 连接槽位，并模糊本地事务边界。

P01-S05 将实现 Transactional Outbox。业务表与 Outbox 表必须在同一本地事务内提交，因此需要在引入 RocketMQ 之前明确数据源拓扑、连接池实现、容量预算和故障边界。

## 2. 问题

MOM 服务是否应默认支持动态多数据源，以及每个应用实例应如何配置和治理 JDBC 连接池？

## 3. 候选方案

### 方案 A：默认引入动态多数据源框架

优点：

- 可以通过注解或上下文快速切换主库、从库和外部数据库。
- 对未来读写分离或遗留系统接入看似更灵活。

缺点：

- 事务管理器、Mapper 和连接选择容易形成隐式行为。
- 读后写一致性、复制延迟和外部数据库故障会进入普通业务代码。
- Outbox 与业务写入可能误用不同 DataSource，破坏本地事务原子性。
- 每增加一个 DataSource 就增加一组连接池、健康检查和容量预算。

### 方案 B：单个服务默认一个权威 DataSource，并按例外场景显式扩展

优点：

- 本地事务边界清晰，业务表、Outbox 和 Inbox 可以使用同一事务管理器。
- 每个服务的连接数、Schema 权限和故障影响可独立计算。
- 保留 Spring Boot 官方 DataSource 自动配置和 HikariCP 指标能力。
- 只有确有外部遗留库、报表库或读副本需求时才承担多数据源复杂度。

缺点：

- 未来接入第二数据源时需要显式 Bean、Qualifier、事务管理器和 ADR。
- 不能通过一个通用注解快速切换任意数据库。

### 方案 C：Outbox 使用独立 DataSource 或独立数据库

优点：

- 可以单独扩容和维护消息表。

缺点：

- 业务写入与 Outbox 写入不再属于同一本地事务。
- 若不引入 XA/JTA，会重新出现数据库与消息状态双写窗口。
- 若引入 XA/JTA，则复杂度和运行风险高于 Transactional Outbox 本身。

## 4. 决策

采用方案 B，并拒绝方案 C：

1. 一个领域服务默认只允许一个权威 DataSource。
2. 每个应用实例默认只创建一个 HikariCP 连接池。
3. 服务只连接并读写自己的 PostgreSQL Schema。
4. 业务表、Outbox 表和 Inbox 表必须使用同一个 DataSource、同一个 `PlatformTransactionManager` 和同一个本地事务。
5. Framework 不引入动态数据源 Starter，不提供基于注解的任意数据源路由。
6. 外部遗留数据库、报表库或读副本属于例外能力，必须通过新的 ADR 明确一致性、只读、事务、健康检查和故障策略后显式配置。
7. V1 默认 Hikari 参数为：`minimumIdle=1`、`maximumPoolSize=5`、`connectionTimeout=3000ms`、`validationTimeout=2000ms`。
8. PgJDBC 默认启用 `tcpKeepAlive=true`，并通过 `ApplicationName` 标识服务连接。
9. 数据库会话继续通过 `connectionInitSql` 固定为 UTC。
10. `maxLifetime`、`keepaliveTime` 暂不覆盖 Hikari 默认值，待 PostgreSQL 代理、网络设备和基础设施超时确定后再统一调整。
11. 泄漏检测默认关闭，仅允许通过环境变量在诊断环境临时开启。

## 5. 决策理由

MOM 的库存、生产、质量和设备协同大量依赖强一致的本地状态变更。默认多数据源并不能自动提供跨库原子性，反而会把数据路由、复制延迟和事务选择扩散到业务代码。

Transactional Outbox 的主要价值是利用同一数据库本地事务消除业务写入和事件记录之间的双写窗口。因此 Outbox 不能拥有独立数据源。发布器可以使用同一连接池，但领取任务、状态更新和网络发送必须拆成短事务，避免在等待 RocketMQ 时长期占用数据库连接和行锁。

HikariCP 是 Spring Boot JDBC Starter 的默认连接池，并能通过 Actuator 暴露通用 JDBC 与 Hikari 专用指标。当前没有证据表明需要引入 Druid 或其他连接池。

## 6. 正向后果

- Outbox 与领域写入能够保持本地事务原子性。
- 服务数据源拓扑简单，事务问题容易测试和排查。
- 连接池容量可以按服务副本数计算。
- PostgreSQL `pg_stat_activity` 能通过 `application_name` 区分服务连接。
- Actuator 和 Prometheus 可以监控活动、空闲、等待和最大连接数。
- 未来多数据源需求仍可通过显式配置扩展，不影响当前领域模型。

## 7. 负向后果与技术债

- 尚未实现读副本路由和读写分离。
- 尚未建立 PgBouncer 或数据库代理基线。
- 不同领域服务引入数据库时仍需复制并按服务名调整标准配置。
- 默认 `maximumPoolSize=5` 只是 V1 起点，最终值必须由压力测试、SQL 延迟和副本数共同决定。

## 8. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 多个服务和滚动升级耗尽 PostgreSQL 连接 | 按最大副本数乘以每实例池上限计算预算，并保留运维与故障扩容连接 |
| 连接池等待导致请求堆积 | 连接获取超时固定为 3 秒，快速失败并监控 pending/timeout |
| 网络设备清理空闲 TCP 连接 | PgJDBC 启用 TCP Keepalive；基础设施明确超时后再调整 Hikari keepalive/maxLifetime |
| 外部遗留库接入破坏事务边界 | 仅在 Integration Adapter 等明确边界中引入第二 DataSource，禁止与权威库假装组成一个本地事务 |
| Outbox 发布器长期占用连接和行锁 | 领取和状态更新使用短事务，RocketMQ 网络调用在持有数据库行锁的事务之外执行 |
| 动态数据源依赖被无意加入 | AGENTS 规则和 PR 审查要求新增 DataSource 必须有 ADR 与真实集成测试 |

## 9. 实施约束

- 默认配置必须使用 `spring.datasource` 和 `spring.datasource.hikari`，保留 Spring Boot 官方自动配置。
- 普通领域服务不声明自定义 DataSource Bean。
- 默认连接池名称使用 `${spring.application.name}` 对应的稳定服务名。
- JDBC URL 必须包含所属 Schema、TCP Keepalive 和 PostgreSQL ApplicationName。
- `validationTimeout` 必须小于 `connectionTimeout`。
- 连接池最大连接数不得脱离服务最大副本数单独评审。
- Outbox Publisher 不创建第二个连接池。
- 多数据源例外必须显式命名 Bean、Mapper 包、事务管理器和健康检查，禁止依赖 ThreadLocal 魔法路由隐藏边界。

连接预算按下式评估：

```text
应用最大连接数
= Σ（服务最大副本数 × 每实例 maximumPoolSize）
+ 迁移、任务、监控和运维连接
+ 滚动升级与故障扩容预留
```

## 10. 验证方式

- 自动化测试：应用上下文只创建一个 DataSource，并验证实际类型为 HikariDataSource。
- 自动化测试：验证池名称、最小空闲、最大连接数、连接获取超时、校验超时和泄漏检测默认值。
- 集成测试：使用真实 PostgreSQL 验证 UTC 会话和 `application_name`。
- 集成测试：验证 PgJDBC URL 含 `tcpKeepAlive=true`。
- 打包应用 Smoke Test：验证 Prometheus 暴露 JDBC 与 Hikari 连接池指标。
- 故障测试：停止 PostgreSQL 后数据接口不得静默返回成功。
- 文档检查：ADR、数据架构和 AGENTS 规则保持一致。

## 11. 替代与回滚条件

出现以下情况时创建新 ADR，而不是直接修改本 ADR：

- 某个领域需要独立 PostgreSQL 集群或数据库代理。
- 有真实数据证明读副本可以显著降低主库压力，并已定义复制延迟与读后写策略。
- Integration Hub 必须直接读取无法提供 API 的外部遗留数据库。
- 引入 PgBouncer、云数据库代理或基础设施连接寿命限制，需要统一调整 Hikari 生命周期参数。
- 采用 CDC 平台替代应用 Outbox 发布器。

## 12. 参考资料

- Spring Boot 4.1 Data Access：`https://docs.spring.io/spring-boot/how-to/data-access.html`
- Spring Boot SQL Databases：`https://docs.spring.io/spring-boot/reference/data/sql.html`
- Spring Boot Actuator Metrics：`https://docs.spring.io/spring-boot/reference/actuator/metrics.html`
- HikariCP 官方仓库与配置说明：`https://github.com/brettwooldridge/HikariCP`
- PgJDBC 连接属性：`https://jdbc.postgresql.org/documentation/use/`
