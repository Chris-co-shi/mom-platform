# MOM 审计、并发与数据生命周期规范

## 1. 并发控制选择

### 1.1 乐观锁

适用于冲突概率可控、更新短、调用方可重试的聚合。更新条件必须包含 ID + Version，成功后推进版本；
affected rows 为 0 表示不存在或冲突，Application 必须按用例证据区分并转换为稳定错误，HTTP 目标为 409。
不得忽略更新行数。

### 1.2 唯一约束

业务唯一键、幂等业务键和不可重复关系由数据库唯一约束兜底。预检查不能替代约束；唯一冲突映射为稳定
业务错误，不返回约束名、SQL 或底层异常文本。

### 1.3 悲观锁与 `SKIP LOCKED`

悲观锁仅用于高冲突、短临界区、锁定行数可控的场景。必须固定锁顺序、设置超时、测试死锁/超时，
且持锁期间不调用外部网络。不得以全表锁解决普通竞争。

`FOR UPDATE SKIP LOCKED` 适用于 Outbox/Inbox 技术领取、调度任务和可由其他 Worker 处理的队列；不适用于
必须立即反馈“资源被占用”的普通用户操作。PostgreSQL 在 `READ COMMITTED` 下每条命令读取命令开始前
已提交快照，行锁和跳锁改变并发领取行为但不提供业务幂等；这些是数据库事实与 MOM 使用边界。

### 1.4 Redis

Redis 锁、SET-NX 和幂等 Guard 可减少重复执行，但不能成为库存、工单、质量或权限正确性的唯一保障。
每个能力必须声明 Redis 不可用时 fail-open/fail-closed；数据库约束和领域状态机仍是最终防线。

## 2. 三类审计不可混用

### 2.1 数据审计

普通可变实体按能力使用 `created_at/by`、`updated_at/by`。CurrentActor 与 MetaObjectHandler 提供填充，
客户端不得提交或覆盖这些字段；自定义 SQL 必须显式写入。

### 2.2 安全审计

登录、登出、密码变更、权限变更、Session 撤销、账号锁定和管理操作使用独立安全审计记录。安全审计
不是 `updated_by` 的替代品，也不得记录密码、Token 或 Secret。

### 2.3 领域事实与状态历史

库存流水、批次谱系、质量结果、设备命令和关键状态变化使用不可变事实、状态历史、冲销或更正记录，
不能只依赖通用更新时间还原历史。

## 3. Actor 规则

- `USER`、`ADMIN`、`SYSTEM` 显式区分；业务写入缺少 Actor 默认 fail closed。
- 不回退到 `SYSTEM`、`0` 或 `anonymous`。
- MQ、定时、Outbox 和清理任务显式建立稳定 SYSTEM Code。
- 异步线程不自动继承 ThreadLocal Actor；客户端 Header 不能作为可信审计主体。

## 4. 删除与生命周期

### 4.1 逻辑删除

只适用于可恢复、默认查询隐藏、仍需审计且唯一性策略明确的普通实体。逻辑删除不是默认能力。事实流水、
谱系、安全审计、状态历史、Outbox、Inbox、OAuth2 状态、Session/Refresh、快照和对账记录使用自身状态与
保留模型，不套通用逻辑删除。

### 4.2 物理删除与归档

物理删除必须定义触发主体、保留期限、法规/追溯要求、归档、批次上限、锁与性能、审计和失败恢复；
禁止普通请求无界清理大表。归档后仍须可追踪、可验证完整性、有明确查询入口及恢复/审计方法，不能搬表后
删除唯一权威记录而丢失追溯。

### 4.3 敏感数据

按字段类别定义最小化、脱敏和保留期限。密码、Token、Secret 和未脱敏个人数据不得进入日志、Trace、
事件或错误；测试只用虚构数据。S02 不建设完整隐私治理平台。

## 5. 人工 Review 清单

每项新持久化变更至少 Review：并发策略、affected rows、唯一语义、软删复用、审计填充、事务边界、
外部调用位置、保留/清理、敏感字段、索引与迁移锁风险。静态门禁无法可靠判断“该表应继承哪个基类”、
索引选择、事务业务边界或法规期限；这些必须以设计说明和测试证明。

## 6. 官方事实来源

- [PostgreSQL 17 MVCC](https://www.postgresql.org/docs/17/mvcc.html)
- [PostgreSQL 17 SELECT locking 与 SKIP LOCKED](https://www.postgresql.org/docs/17/sql-select.html)
- [MyBatis-Plus Optimistic Locker](https://baomidou.com/en/plugins/optimistic-locker/)
- [MyBatis-Plus Logic Delete](https://baomidou.com/en/guides/logic-delete/)
- [MyBatis-Plus Auto-fill](https://baomidou.com/en/guides/auto-fill-field/)

