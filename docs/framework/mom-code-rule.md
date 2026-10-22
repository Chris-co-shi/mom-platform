# mom-code-rule：Owner 本地编码规则与正式发号

**状态：MDM/WMS Owner 已接入；WMS 期初和盘点有业务消费者。** 模块提供规则草稿、修订、发布和正式编码生成。源码入口为 [`mom-code-rule`](../../mom-framework/mom-code-rule/)；规则、流水、已发编码归各 Owner 本地数据库，不是跨 Owner 中央服务。

- 业务入口：`CodeGenerationApplication.generate(ruleCode, context, requestKey)` 返回 `CodeGenerationResult(ruleCode, revisionId, revisionNo, code)`。`ruleCode` 在 Owner 内稳定，`context` 传业务字段，`requestKey` 标识同一次申请且重试须复用。真实调用见 WMS [`OpeningDocumentNumberService`](../../mom-wms-platform/mom-wms-server/src/main/java/io/github/chrisshi/mom/wms/application/OpeningDocumentNumberService.java)。
- 管理入口：`CodeRuleApplication` 提供创建、详情、分页搜索、修改草稿、修订、发布、停用；写操作使用规则级版本。`CodeSegmentHandler`/`CodeFieldProvider` 是已实现扩展点，具体业务字段仍由 Owner 提供。
- 装配：`mom.code-rule.enabled=true` 后由自动配置绑定本地 DataSource、事务管理器和 `mom.code-rule.owner`。HTTP 管理入口另要求 Resource Server 启用；Flyway 表和权限需在 Owner 服务核查。
- 失败与保证：未发布/停用规则、非法定义或版本冲突会失败；相同 Owner、请求键与内容可重放已发编码，异内容冲突。正式发号必须与业务写处于同一本地事务；不得直接操作流水表或跨 Owner 读写。

前端工作台只能管理规则，不代表任意 Owner 已允许正式发号。规范依据见[事务规范](../engineering/standards/transaction-consistency-standard.md)。
