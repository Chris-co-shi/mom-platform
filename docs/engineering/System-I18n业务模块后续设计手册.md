# System I18n 业务模块后续设计手册

## 1. 目的与使用方式

本手册供 MDM、MES、WMS、QMS 及后续 bounded context 设计自己的动态 I18n Slice。它定义接入约束和设计检查
项，不代表这些模块已经实现，也不授权一次性批量生成所有模块代码。每个模块仍需由负责人决定真实文案范围、
事务、权限和当前 Slice。

## 2. 必须先做的所有权决策

每个模块开始前必须回答：

1. 哪些文案必须运行时修改，哪些应继续留在 classpath 或前端资源；
2. namespace 是否唯一属于该 bounded context；
3. 文案由谁维护、谁有权限、是否需要审计；
4. 数据写入哪一个服务 Schema；
5. 数据库不可用和 Translation 缺失时的精确回退行为；
6. 是否真的需要 SSE；单实例通知能否满足当前部署；
7. 是否存在真实审批、发布或回滚要求。没有时不得复制旧 System 发布模型。

不得把“所有模块都需要国际化”推导成中央 Translation Center。System 只提供支持 Locale 元数据与 `system.*`，
各服务自行拥有业务文案。

## 3. namespace 与数据归属

| 模块 | namespace 前缀 | 权威 Schema | 典型文案（需逐项确认） | 禁止放入 System 的事实 |
|---|---|---|---|---|
| MDM | `mdm.*` | `mom_mdm` | 物料、工厂等管理校验展示文案 | Material/Factory 主数据及业务错误 Translation |
| MES | `mes.*` | `mom_mes` | 工单、工序、报工状态错误文案 | 生产状态机、工单事实、批次事实 |
| WMS | `wms.*` | `mom_wms` | 库位、库存、作业状态错误文案 | 库存余额、流水、预占、容器关系 |
| QMS | `qms.*` | `mom_qms` | 检验、冻结、放行错误文案 | 质量判定、冻结与放行事实 |

推荐 namespace 形态为 `<context>.<feature>`，例如 `mes.work-order`。`messageKey` 在 namespace 内稳定，例如
`error.invalid_status_transition`。稳定机器错误码可采用 `MES-WO-409-001`，不得将本地化文案作为 code。

## 4. 推荐最小结构

当能力仍是简单 CRUD、唯一约束和文本校验时，从 ADR-042 Level 1 开始：

```text
<context>/controller/i18n
<context>/application/i18n
<context>/infrastructure/entity
<context>/infrastructure/mapper
<context>/resources/db/migration/<context>/Vn__create_<context>_i18n.sql
```

只创建 Message Definition 与 Translation 两类当前需要的表。业务模块消费 System 的启用 Locale 列表可以通过
稳定 API 契约或本地受控配置实现；采用哪种方式属于跨服务边界决策，必须在该模块 Slice 中明确，不能偷偷通过
跨 Schema 查询或外键实现。

仅当出现复杂翻译审批/生命周期时引入 Domain；仅当存在多个真实存储/外部翻译供应商或测试隔离收益时引入
Port/Adapter。不得机械复制 System 类名或建立通用 Repository。

## 5. Framework 接入约定

模块实现 `I18nRuntimeProvider`，声明自己拥有的 namespace，并注册 Core `I18nMessageResolver` 的业务实现：

```text
Framework Controller → I18nRuntimeProvider → 本模块 Application/Mapper → 本模块数据库
Framework/Security Exception → I18nMessageResolver → classpath 或本模块 Translation
Application 成功提交 → I18nChangeNotifier → 本实例 SSE → 客户端重新拉取
```

Provider 必须拒绝其他 bounded context 的 namespace。不要从 MDM Provider 查询 MES 表，也不要从业务 Provider
远程调用 System 解析每一条文案。

## 6. 数据与校验基线

- ID 使用 Java `String`、PostgreSQL `varchar(19)`；时间点使用 `Instant/timestamptz`；
- 普通可维护 Message/Translation 表需要审计、乐观锁和逻辑删除时使用 `BaseEntity`；
- 新表通过新 Flyway Versioned Migration 创建，禁止修改已发布 Migration；
- 自主业务表之间不建立物理外键或物理级联；Application 校验引用，唯一约束兜底；
- Message 身份 `(namespace,messageKey)` 创建后不可变；
- Translation 只保存纯文本；数字 Placeholder 集合在所有 Locale 间必须一致；
- 运行时只读取启用 Message、启用 Locale 和未删除 Translation；
- 回退固定为请求 Locale、平台默认 Locale、messageKey；
- Locale 是展示偏好，不进入权限、状态机、库存、质量或追溯事实。

## 7. 事务与并发检查表

- [ ] 写用例的 `@Transactional` 位于 Application 公共方法；
- [ ] Translation 写入前验证 Message 和 Locale 引用；
- [ ] 更新携带 version，affected rows 为 0 转换为稳定并发冲突；
- [ ] 唯一冲突不会被误报为 500 或覆盖已有数据；
- [ ] SSE 通知只在事务成功提交后触发；
- [ ] 通知失败不回滚已经提交的业务数据；
- [ ] 没有在数据库事务中做远程 HTTP、MQ 发送或长时间等待；
- [ ] 没有跨 Schema JOIN、外键或写入。

## 8. API 与安全检查表

- [ ] 管理 API 与 Runtime API 分离；
- [ ] 管理 API 使用本模块明确权限，不复用 System 管理权限；
- [ ] `code` 和 `messageKey` 稳定，展示 `message` 才本地化；
- [ ] BCP 47 Tag 不使用下划线或数字枚举；
- [ ] 不记录完整敏感 Payload，不把文案或用户标识放入指标 Label；
- [ ] Runtime API 限制 namespace 数量、长度和字符集，避免无界查询；
- [ ] SSE 不承载 Token、完整 Translation 或业务事实。

## 9. 最小测试证据

每个模块至少覆盖：正常创建/更新、重复 key、Message/Locale 不存在、禁用状态、乐观锁冲突、Placeholder 不一致、
请求 Locale 命中、默认回退、最终 key 回退、多 namespace bundle、事务回滚不通知、数据库不可用失败行为。新增表
必须使用 PostgreSQL Testcontainers 或等价真实数据库证据；SSE 单元测试不能替代事务提交边界测试。

## 10. 分模块建议顺序

1. MDM：先选一个真实管理错误调用方，证明模式与 Locale 契约；
2. MES：在生产状态错误码稳定后接入，禁止让 Translation 参与状态判断；
3. WMS：明确库存/作业错误的 code 与前端展示边界，不让 I18n 影响幂等；
4. QMS：明确质量判定事实与展示文案分离，禁用 Translation 不得改变冻结/放行结果。

每次只实施一个有真实调用方的 Slice。若没有运行时修改需求，优先保留本模块 classpath 双语资源，不创建数据库表。
