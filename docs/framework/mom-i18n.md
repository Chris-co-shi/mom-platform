# mom-i18n：Owner 本地动态文案

**状态：Auth、System、MDM、WMS 已装配，System 管 Locale。** 模块提供 `I18nManagementService`、`I18nRuntimeService`、`I18nMessageResolver` 实现及本地 JDBC Store。源码入口为 [`mom-i18n`](../../mom-framework/mom-i18n/)；翻译记录由各 Owner 持有。

- 入口：管理服务创建/更新 Message、保存 Translation、查询重复 Key；运行时服务 `load(requestedLocale)` 返回 Bundle。HTTP 管理与运行时端点由 `mom-webmvc` 提供，不应从业务 Application 直接构造 HTTP 响应。
- 装配：显式 `mom.i18n.owner` 仅支持 `auth/mdm/system/wms`；自动配置使用当前 Owner DataSource 与同一 `DataSourceTransactionManager`，不匹配时启动失败。宿主须提供表、ID、Actor、Clock 与 Locale 策略；非 System 默认内部基准为 `zh-CN`，不能据此推断平台动态默认语言。
- 保证与限制：管理写操作有本地事务；运行时文案按 Owner 基准 Locale 回退，最后回退 Key。WebMVC 的 SSE 通知为单 JVM，本模块不保证跨实例实时刷新。

新增文案、Key 和 Owner 选择必须先按[国际化与 Locale 规范](../engineering/standards/localization-locale-standard.md)查重；服务不得通过请求参数任意切换写入 Owner。
