# ADR-016：Seata AT 受控 PoC 与故障边界

- 状态：Accepted
- 日期：2026-07-18
- 关联决策：[ADR-009：Seata 使用边界](ADR-009-Seata使用边界.md)
- 关联文档：[集成架构](../architecture/集成架构.md)

## 1. 背景

ADR-009 已决定：本地事务是默认选择，长制造流程使用 Outbox、事件、状态机和补偿；Seata AT 只允许用于时间短、参与资源明确且能够快速回滚的同步场景。

Phase 01 仍需要验证当前固定技术组合能否真实工作：

- JDK 25；
- Spring Boot 4.1.0；
- Spring Cloud 2025.1.2；
- Spring Cloud Alibaba 2025.1.0.0；
- Apache Seata 2.5.0；
- PostgreSQL 17.7；
- OpenFeign 同步调用。

仅依赖能够编译不足以证明 AT 数据源代理、XID 传播、Undo Log、全局提交和全局回滚真实可用。

## 2. 候选方案

### 2.1 不实现运行时 PoC

优点是没有额外复杂度；缺点是无法证明 Boot 4、PostgreSQL 和 Feign 调用链兼容，只能保留纸面结论。

### 2.2 把 Seata 作为所有跨服务写入的默认方案

编码表面直观，但会放大锁持有时间、TC 可用性依赖、服务耦合和回滚语义风险，不适合制造长流程。

### 2.3 建立默认关闭、范围固定的双数据库 AT PoC

用两个独立 PostgreSQL 数据库、一个短同步 Feign 调用和明确故障点验证完整 AT 机制，同时把运行边界固化为代码、配置、ADR 和 CI。

## 3. 决策

采用方案 2.3。

### 3.1 版本与依赖

- 使用 `spring-cloud-starter-alibaba-seata`，通过 `mom-seata` 统一引入；
- 使用 Spring Cloud Alibaba 2025.1.0.0 BOM 管理的 Apache Seata 2.5.0；
- 不在本切片单独覆盖 Seata 版本，也不因 2.6.0 已发布而绕过当前兼容矩阵；
- Seata Server CI 使用官方 `apache/seata-server:2.5.0.jdk21` 镜像。

### 3.2 PoC 调用链

```text
MDM @GlobalTransactional（TM）
  → MDM PostgreSQL 本地事务（RM 1）
  → OpenFeign + XID 传播
  → Integration PostgreSQL 本地事务（RM 2）
  → 全局提交或全局回滚
```

两个服务各自使用唯一 DataSource、HikariCP、事务管理器和服务内 `undo_log`，不创建第二连接池，不跨 Schema 直接访问对方数据。

### 3.3 强制边界

- Seata 默认关闭；技术接口默认关闭；
- 全局事务超时固定为 10 秒；
- 当前 PoC 最多包含 MDM 和 Integration 两个数据库分支；
- 每个数据库写入仍由显式 Spring 本地事务定义；
- 参与者没有收到 XID 时必须 fail-fast，不允许退化为普通本地写入；
- MDM 与 Integration 观察到的 XID 不一致时必须全局回滚；
- TC 不可用或全局事务无法开始时 fail-closed，不允许绕过 Seata 后继续写库；
- 不在全局事务内进行自动重试、消息发送、休眠轮询或外部系统调用；
- 不把 Outbox、Inbox、RocketMQ、人工检验、设备命令、外部回调或完整制造流程放入 AT 全局事务；
- 回滚必须符合业务语义。对已经产生外部现实副作用的操作不得使用数据库回滚伪装补偿完成。

### 3.4 数据库迁移先于 Seata 数据源代理

Apache Seata 2.5 的 AT `DataSourceProxy` 在构造阶段会立即检查 `undo_log` 是否存在，并在缺失时 fail-fast。该检查发生在 Spring Boot Flyway 使用代理 DataSource 迁移之前，因此全新数据库不能直接以 Seata 启用状态首次启动。

MOM 固定采用以下顺序：

```text
迁移阶段：Seata disabled → Flyway migrate/validate → 停止迁移实例
运行阶段：Seata enabled → DataSourceProxy 校验 undo_log → 注册 RM → 接收流量
```

约束：

- `undo_log` 必须由服务自己的 Flyway 迁移管理，不在启动脚本中复制 DDL；
- 迁移阶段使用同一版本应用制品、同一数据库凭证和同一 Schema，但显式设置 `seata.enabled=false`；
- 迁移成功后才允许滚动启动 Seata-enabled 业务实例；
- Seata-enabled 实例保留 Flyway 校验，发现迁移漂移仍应启动失败；
- 不允许通过关闭 Undo Log 存在性检查、自动临时建表或授予业务应用跨 Schema DDL 权限来绕过该顺序；
- `mom-infra` 后续应把该顺序实现为部署前 Migration Job/Hook，并保证失败时不发布新业务 Pod。

### 3.5 注册与存储

- CI 使用 file registry 和直接 TC 地址，只用于隔离验证；
- CI 的 Seata Server 使用 file store，不代表生产高可用方案；
- 生产环境的注册中心、配置中心、TC 存储、高可用、鉴权和备份由 `mom-infra` 单独设计；
- 业务服务不得自行启动嵌入式 TC。

## 4. 失败语义

| 失败点 | 预期结果 |
|---|---|
| Flyway 迁移失败或 `undo_log` 缺失 | Seata-enabled 实例不得启动，不接收流量 |
| MDM 本地分支失败 | 不调用远端；MDM 本地事务回滚；全局事务回滚 |
| Integration 本地分支失败 | Integration 本地事务回滚；Feign 抛错；MDM 分支全局回滚 |
| Integration 成功后 MDM 抛错 | 两个已注册分支都由 TC 全局回滚 |
| TC 在事务开始前不可用 | 请求失败；两个数据库均不得产生业务行 |
| 提交或回滚过程中出现未知状态 | 不伪造成功；保留 TC、RM、数据库和日志证据，进入运维排查 |

## 5. 后果

### 正向

- 真实证明当前 Boot 4、Seata 2.5、PostgreSQL 和 Feign 组合可运行；
- 展示 TM、RM、XID、Undo Log 和二阶段回滚机制；
- 通过默认关闭和 CI 故障演练限制误用范围；
- 全新数据库迁移与 Seata 代理启动顺序明确、可自动化；
- 不影响 Outbox/Inbox 作为长流程最终一致方案。

### 负向

- 服务启动和 SQL 执行链增加数据源代理、TC 连接和 Undo Log 成本；
- TC 成为启用场景的强依赖；
- Seata-enabled 发布前增加独立数据库迁移阶段；
- AT 的数据库回滚不能替代领域补偿；
- PostgreSQL SQL 兼容范围需要随正式业务 SQL 单独验证。

## 6. 验证方式

独立 Seata CI 必须使用两套全新 PostgreSQL 17.7 和真实 Seata Server 2.5.0，验证：

1. Seata-disabled 迁移实例通过 Flyway 创建各自 Schema、技术表和 `undo_log`；
2. Seata-enabled 实例随后通过 Undo Log 检查并成功注册 TM/RM；
3. 两个分支成功提交且 XID 一致；
4. 远端成功后发起方异常，两个数据库最终均无记录；
5. 参与者本地异常，两个数据库最终均无记录；
6. 完成事务后两端 `undo_log` 被清理；
7. TC 停止后新事务 fail-closed，两个数据库均无记录；
8. 既有 Nacos、Redis、RocketMQ、PostgreSQL 和 JDK 25 CI 无回归。

## 7. 替代和退出条件

出现以下任一情况时，应移除正式业务中的 Seata 使用并回到本地事务加事件/补偿：

- Boot、Cloud 或 Seata 升级后无法通过真实 AT CI；
- 全局事务时长无法稳定控制；
- 参与者数量持续增长；
- 业务回滚不再等价于数据库回滚；
- TC 高可用和运维成本超过该同步场景收益；
- 调用链出现人工、设备、消息或外部系统等待。
