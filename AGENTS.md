# MOM Platform AI 与工程协作规则

本文件适用于仓库根目录及全部子模块。任何人工开发者、AI 编码工具或自动化 Agent 在修改代码前都必须遵守。

## 1. Java 中文注释是强制要求

所有新增或实质修改的 Java 代码必须包含完整、准确、可维护的中文注释，不得只保留英文模板注释，也不得用注释掩盖未实现能力。

### 1.1 类与接口

每个新增或实质修改的类、接口、枚举、记录类型都必须使用中文 Javadoc 说明：

- 该类型解决什么问题；
- 所属模块和架构边界；
- 允许依赖与禁止依赖的方向；
- 线程安全、并发、一致性或事务语义；
- 外部基础设施不可用时的失败策略；
- 不能从代码本身直接看出的设计取舍。

### 1.2 公共方法与关键私有方法

公共方法必须使用中文 Javadoc 说明参数、返回值、异常、幂等性和副作用。关键私有方法必须使用中文注释解释非显然算法、资源释放、降级和异常处理逻辑。

简单的 getter、setter、构造器或代码本身已充分表达含义的局部变量，不要求堆砌无意义注释。

### 1.3 注释质量

禁止以下注释：

- 逐行翻译代码；
- 与实现不一致的过期描述；
- “TODO 后续实现”却让当前代码表现为已完成；
- 仅写“工具类”“配置类”“处理方法”等无信息内容；
- 大段复制开源项目原注释。

## 2. 开源参考边界

- 优先使用官方依赖、官方文档和官方源码验证框架行为；
- Pig、Yudao Cloud、JetLinks、OpenWMS 等仅用于研究机制和领域概念；
- 允许学习后使用 MOM 自有接口重新实现；
- 禁止批量复制源码、改包名或隐去许可证；
- 新增第三方依赖必须更新开源来源和许可证记录。

## 3. 架构边界

- `*-api` 只定义跨模块契约，不依赖 WebMVC、数据访问或具体 Server；
- `*-client` 只封装调用契约，不依赖提供方 Server；
- 领域 `*-server` 不直接依赖其他领域的 `*-server`；
- Gateway 必须保持 WebFlux，禁止引入 Servlet/WebMVC；
- PCS、WCS 保持独立仓库和部署边界；
- MES、WMS、QMS、库存事实、批次谱系、Integration Hub 等 MOM 核心能力必须自主建模。

## 4. 数据访问约束

- Java 技术主键统一使用 `String`，数据库使用 `varchar(19)`；禁止把 Snowflake 或 64 位整数 ID 作为 JSON Number 暴露给前端；
- MyBatis-Plus 默认主键策略使用 `ASSIGN_ID`，业务编码、工单号、批次号等领域标识必须独立建模；
- 实体继承必须按能力选择：仅主键使用 `BaseIdEntity`，需要审计使用 `BaseAuditEntity`，同时需要乐观锁和逻辑删除的普通业务表才使用 `BaseEntity`；
- 中间表、日志表、流水表、Outbox/Inbox、快照表不得为了统一形式强制继承完整 `BaseEntity`；复合主键表可以完全不继承；
- `BaseEntity.deleted` 使用布尔逻辑删除语义：`false` 有效、`true` 已删除；物理清理、归档和唯一键复用必须由领域迁移单独设计；
- 时间字段使用 `Instant`，PostgreSQL 使用 `timestamptz`，数据库连接会话统一为 UTC；
- 普通领域服务默认只能有一个权威 `DataSource` 和一个 HikariCP 连接池；禁止无 ADR 引入动态数据源 Starter、`AbstractRoutingDataSource` 或基于 ThreadLocal 的隐式路由；
- 业务表、Outbox 表和 Inbox 表必须使用同一 `DataSource`、同一事务管理器和同一本地事务；Outbox Publisher 不得创建第二个连接池；
- 默认 HikariCP 基线为 `minimumIdle=1`、`maximumPoolSize=5`、`connectionTimeout=3000ms`、`validationTimeout=2000ms`，调整时必须结合服务最大副本数重新计算 PostgreSQL 连接预算；
- PgJDBC 连接必须启用 TCP Keepalive，并使用稳定的 `ApplicationName` 便于在 `pg_stat_activity` 中识别服务连接；
- `maxLifetime`、`keepaliveTime` 只有在数据库代理、网络设备或基础设施连接寿命明确后才能覆盖默认值；泄漏检测默认关闭，仅限诊断环境临时开启；
- 外部遗留数据库、报表库或读副本属于多数据源例外，必须新增 ADR，并显式定义 Bean、Mapper、事务、只读、一致性、健康检查和故障策略；
- Mapper 不得通过 `IService/ServiceImpl` 直接升级为领域服务契约，事务边界应由显式 Application Service 定义；
- Lombok 仅用于消除 getter、setter、构造器等机械代码，不得使用 `@Data` 自动生成实体 `equals/hashCode/toString`，避免触发懒加载、递归引用或错误身份语义；
- 新增 Flyway 迁移后不得修改已经合并执行过的历史迁移文件，结构变更必须增加新版本迁移。

## 5. 消息与最终一致性约束

- Spring Cloud Stream 只作为 Binding、消息转换和 Broker 适配层；不得把 Binder 抽象当作本地事务、可靠状态或业务幂等实现；
- 跨服务事件必须使用版本化、Broker 无关的事件信封，至少包含 `eventId`、事件类型、版本、聚合标识、发生时间、生产服务和关联标识；
- 事件正文使用明确 JSON，禁止 Java 原生序列化；Payload、日志、Trace 和 `last_error` 不得包含 Token、密钥或未脱敏敏感数据；
- 事件 ID 在首次写入 Outbox 时生成，所有发布重试必须复用同一个 ID 和负载；
- 业务写入与 Outbox INSERT 必须同一 PostgreSQL 本地事务提交或回滚，禁止在业务事务中直接调用 RocketMQ；
- Outbox 领取必须使用数据库原子语义、短事务、租约与 CAS；RocketMQ 网络调用必须在提交领取事务、释放数据库连接和行锁后执行；
- StreamBridge 返回成功只表示传输被 Binding/Broker 接受，不表示消费者业务完成；发送后状态更新失败时必须允许重复发布，并依靠消费幂等承受；
- Producer 的 Binder 内部发送重试默认关闭，由 Outbox 持久化 RETRY/DEAD 状态统一控制，避免多层重试乘法；
- Consumer 使用稳定消费者组；Spring Cloud Stream 通用 `maxAttempts` 默认设为 1，Broker 重新消费和 DLQ 由 RocketMQ 配置控制；
- 消费者业务写入与 Inbox 记录必须同一本地事务；业务异常必须回滚 Inbox，使重新投递仍可处理；
- Inbox 唯一约束只解决事件级重复，库存、工单、质量和设备命令仍必须使用领域状态机、条件更新和业务唯一约束；
- Outbox DEAD 与 RocketMQ 消费 DLQ 是不同故障面，必须分别监控、告警和处理；
- 不允许根据 HTTP 参数、事件内容或用户输入无限创建动态 Binding、Topic 或消费者组；正式名称必须来自受控配置；
- 新增消息能力必须真实验证正常发布、重复投递、Broker 中断恢复、消费者失败重试和 DLQ，不得只使用内存 Test Binder 得出兼容性结论。

## 6. Seata 分布式事务约束

- 本地事务和 Outbox/Inbox 最终一致是默认方案；不得因为引入 `mom-seata` 就把跨服务写操作默认改为 Seata；
- `@GlobalTransactional` 只允许用于时间短、参与服务与数据库明确、数据库回滚符合业务语义的同步场景；新增场景必须有 ADR 或在现有 ADR 中明确获准；
- 全局事务内禁止等待人工、设备、消息、外部回调、长轮询、休眠和无界重试；完整制造流程必须使用事件、状态机、对账和人工补偿；
- 当前 AT 基线的单次全局事务超时不得超过 10 秒，参与数据库分支默认不得超过两个；放宽任一限制必须新增 ADR 和故障测试；
- 每个 RM 仍必须使用显式 Spring 本地事务，业务写入与本服务 `undo_log` 共用唯一 DataSource、事务管理器和连接池；
- 全新数据库或新增 Seata Flyway 迁移必须先运行 `seata.enabled=false` 的独立 Migration Job，再启动 Seata-enabled 业务实例；不得关闭 Undo Log 检查、在启动脚本复制 DDL 或让业务 Pod 边代理边初始化；
- Seata 默认关闭，技术接口默认关闭；服务不得自行启动嵌入式 TC，也不得在 TC 不可用时降级为普通本地写入；
- 参与者没有收到 XID、上下游 XID 不一致或全局事务无法开始时必须 fail-closed；禁止伪造成功或吞掉全局事务异常；
- Feign XID 传播由 Spring Cloud Alibaba Seata 负责，业务代码不得手工复制协议 Header，除非官方集成失效且已有新的 ADR；
- AT 回滚只处理数据库状态，不能撤销已经发生的设备动作、人工决策、文件发送或外部系统副作用；此类场景必须使用补偿而不是 AT；
- 不允许在 Seata 全局事务中直接发送 RocketMQ，也不允许用 Seata 替代 Outbox、Inbox、幂等、DEAD/DLQ 和对账；
- 新增或升级 Seata 必须使用真实 TC 和两个独立 PostgreSQL 数据库验证迁移先行、提交、参与者失败、远端成功后回滚、Undo Log 清理和 TC 中断，不得仅验证应用启动。

## 7. Redis 约束

- Key 必须具有统一命名空间，禁止直接拼接含个人信息或敏感业务数据的原始值；
- 默认使用字符串或明确的 JSON 格式，禁止依赖 Java 原生序列化；
- 所有幂等键、锁、临时状态必须设置 TTL；
- 原子语义必须由单条 Redis 命令或 Lua 脚本保证，禁止“先查再写”；
- 必须明确 Redis 不可用时采用 fail-open 还是 fail-closed；
- 分布式锁必须使用唯一持有者标识并安全释放，幂等占位不得伪装成分布式锁。

## 8. 测试与提交

- 新增基础设施能力必须包含单元测试或真实中间件 Smoke Test；
- GitHub Actions 必须执行 JDK 25 下的 `mvn -B -ntp clean verify`；
- 中间件兼容性结论必须来自真实测试，不得仅凭依赖能够编译；
- PR 描述必须写明范围、架构边界、失败策略、验证结果和未完成项；
- CI 未通过不得合并。
