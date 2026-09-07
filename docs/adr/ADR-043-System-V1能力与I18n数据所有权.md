# ADR-043：System V1 能力与 I18n 数据所有权

- 状态：Accepted
- 提出日期：2026-09-07
- 接受日期：2026-09-07
- 决策人：Chris
- 适用范围：`mom-system-platform`、`mom-webmvc` 以及未来接入动态 I18n 的业务服务
- 关联决策：ADR-023、ADR-025、ADR-026、ADR-030、ADR-031、ADR-037、ADR-042
- 替代关系：替代 ADR-030、ADR-031、ADR-037 对 System 当前运行时的结论；替代 ADR-023 中由 System 持有用户 Locale 偏好的部分；历史文件和 V1～V9 Migration 保留

## 1. 背景

System 在 P1.6 阶段逐步累积了 Parameter、Dictionary、Dynamic I18n、User Preference、Application
Catalog、Navigation、发布快照、缓存、Outbox/Inbox、RocketMQ 和 IAM 权限引用校验。它们各自可以解释，
但组合后使 System 同时承担配置中心、前端导航中心、用户偏好中心和运行时分发中心，超过 V1 的真实需求。

本次决策不是重新设计 MOM，也不否定历史验证价值，而是把当前可运行基线收敛到能够明确拥有、测试和演进的
最小能力集合。

## 2. 决策

### 2.1 System V1 只保留三类业务能力

1. 受限平台字典：Dictionary Type 与 Dictionary Item；
2. 平台支持语言：Supported Locale，且任一时刻恰好一个启用的默认 Locale；
3. `system.*` 动态文案：Message Definition 与按 Locale 保存的 Translation。

Parameter、User Preference、Application Catalog、Navigation Metadata、发布/回滚、快照、运行时缓存、
Outbox/Inbox、RocketMQ 和 IAM Permission Reference 不属于 System V1。已有历史表不删除，当前生产代码不再读写。

### 2.2 分层采用 ADR-042 Level 1

```text
controller → application → infrastructure mapper/entity → PostgreSQL
```

System 当前没有复杂聚合状态机、多个持久化实现或需要隔离的外部系统，因此不创建 Domain、Repository Port、
一对一 Repository Adapter、Converter 或 Mapper XML。事务位于 Application 公共写方法。

### 2.3 I18n 所有权按 namespace 与数据库共同隔离

- Framework 持有 `framework.*`，使用 classpath `MessageSource`，至少提供 `zh-CN` 和 `en-US`；
- System 只持有 `system.*`，保存于 `mom_system`；
- MDM、MES、WMS、QMS 等服务分别持有 `mdm.*`、`mes.*`、`wms.*`、`qms.*`，并保存到自己的 Schema；
- System 不成为全平台 Translation Center，业务服务不得远程调用 System 解析自身错误文案；
- 稳定错误 `code` 不本地化，`messageKey` 稳定，最终展示 `message` 可本地化。

### 2.4 Framework 只提供薄运行时协议

`mom-core` 提供无框架依赖的 `I18nMessageResolver` 契约；`mom-webmvc` 提供：

- classpath `I18nMessageResolver` 实现：按 Locale 解析框架消息；
- `I18nRuntimeProvider`：由业务服务提供 namespace bundle；
- `I18nChangeNotifier`：事务提交后发送本实例 SSE 失效提示；
- 通用 Runtime Bundle Controller 与 SSE Controller。

Framework 不拥有业务表、不反向依赖 System、不实现 Redis 缓存、MQ 广播或全平台文案中心。
`mom-security` 只消费 Core 契约，在 Filter Chain 的 AuthenticationEntryPoint/AccessDeniedHandler 生成本地化 JSON，
不依赖 MVC ControllerAdvice，也不引入 WebMVC。

### 2.5 运行时语义

- Locale 解析只接受启用的 BCP 47 Tag；不支持或非法值回退到数据库默认 Locale；
- 文案回退顺序固定为“请求 Locale → 默认 Locale → message key”；
- 同一 Message 的所有 Translation 必须使用完全相同的数字 Placeholder 集合，例如 `{0}`、`{1}`；
- Translation 是纯文本，不接受 HTML 标签或控制字符；
- Message Key 创建后不可修改；
- 管理写入使用唯一约束、乐观锁和必要的行锁处理并发；
- SSE 只表示“请重新拉取”，在数据库事务提交后发送；断线由客户端重连和重新拉取恢复。

## 3. 候选方案

### 3.1 保留原有 System 全能力

拒绝。V1 没有足够真实调用方证明 Catalog、Preference、发布快照、跨实例缓存失效和 IAM 引用闭环的成本合理。

### 3.2 建立中央 Translation Center

拒绝。它会使所有业务错误展示依赖 System 的可用性，并破坏 bounded context 的数据所有权。

### 3.3 仅保留静态 Resource Bundle

拒绝。System 管理文案存在运行时调整需求，但动态能力仍限定在各服务本地所有权内。

## 4. 后果

正向后果：System 调用链、事务边界和数据归属显著缩短；业务模块可以独立发布文案；System 故障不会阻断其他
服务解析本地文案；V1 无需 Redis、RocketMQ 或新的基础设施。

负向后果：多实例部署时 SSE 不跨实例广播；各服务需要重复实现少量 Message/Translation 持久化；平台不提供
统一翻译运营后台；历史表仍占用 Schema 并可能让运维人员误解为当前能力。

## 5. 风险与约束

- System V1 当前仅承诺单实例 SSE；扩展为多实例前必须基于真实部署规模另行决策；
- 历史表只能通过未来明确 Migration 归档或删除，禁止修改 V1～V9；
- 默认 Locale 切换必须与禁用操作串行化，数据库部分唯一索引是最终兜底；
- DB 不可用时动态 `system.*` 解析失败，不伪装成成功；`framework.*` 仍可由 classpath 解析；
- 不得把 Locale、messageKey、文案或 SSE 事件当作权限、审计主体或业务事实。

## 6. 验证方式

1. 架构测试确认 System 只有 API/Server、无 Client、无旧能力 Java 类型及禁用依赖；
2. PostgreSQL 集成测试从 V1 迁移到 V10，证明历史 Migration 未被改写；
3. 集成测试覆盖唯一冲突、禁用、默认 Locale、回退、Placeholder、一组多 namespace bundle；
4. Framework 单元测试覆盖 classpath 双语回退和事务提交后 SSE 通知；
5. CI PostgreSQL Smoke 验证 V10 表、种子、逻辑删除字段与零业务外键。

## 7. 重新评估触发条件

仅在出现两个以上实例的真实部署、明确的跨实例即时失效 SLA、集中翻译运营组织或三个以上业务模块重复成本已被
量化时，重新评估共享通知设施或翻译运营能力。重新评估不自动意味着把业务 Translation 移交给 System。
