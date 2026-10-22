# 平台可复用业务契约

这里只列出由明确 Owner 对其他模块提供的能力；模块内部 Application Service 和 WMS 作业流程不因此成为全局公共 API。当前状态先看[实现地图](../当前实现地图.md)，详细接口以源码为准。

| Owner | 可复用能力与入口 | 当前消费者及限制 |
|---|---|---|
| Auth | `POST /auth/sessions/login`、当前会话、退出；Redis Opaque Token 与 RBAC 权限快照。业务服务接入 [Framework Resource Server](../framework/README.md#已形成业务调用链的入口) | `mom-web` 登录/会话及各 Server；退出仅删除当前 Token，角色变更不即时更新已签 Token |
| System | 支持 Locale、受限字典和 `system.*` 文案；Locale 目录 `/i18n/locales`、运行时 bundle `/i18n/runtime/bundles` | Web 和服务端 I18n；其他 Owner 的翻译仍保存在各自库，不进入 System |
| MDM | 静态主数据的 `mom-mdm-api/client` 只读 Reference 契约 | WMS 期初等业务读取；可用性表示当前可用于新业务，不代表库存或历史事实 |
| MDM/WMS 各 Owner | 编码规则管理与正式发号由 [Framework `mom-code-rule`](../framework/README.md#已形成业务调用链的入口)支撑，规则和流水留在 Owner | WMS 期初、盘点已使用；不能跨 Owner 共用流水或绕过本地单据事务 |

## Auth 接入

Web 模块使用 `src/modules/auth/api/auth-api.ts` 和共享 HTTP Client；业务 Controller 使用本服务的 Resource Server 配置与实际权限码。Gateway 只路由、清洗请求头并检查 Bearer 形态；Redis Token Store 的最终校验在目标 Server。权限快照在签发时固定，注销仅撤销当前 Token；Token Store 不可用时认证失败关闭。登录 Application 通过 Spring Security `AuthenticationManager` 校验凭据，不在业务 Controller 手动解析 Authorization Header。细节见[安全运行规范](../engineering/standards/security-protocol-runtime-standard.md)与 [ADR-040](../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)。

## System 与 I18n 接入

服务端文案先确定业务 Owner，再复用 `mom-i18n` 管理/运行时入口。System 只拥有支持 Locale 和 `system.*` 文案；Auth/MDM/WMS 文案分别归其服务。读取回退顺序为请求 Locale、Owner 基准 Locale、Key。当前 SSE 更新通知是单 JVM 机制，跨实例实时刷新未获验证。Web 的 Owner 映射和本地语言选择不构成服务端翻译所有权或用户偏好事实。新增文案遵循[国际化与 Locale 规范](../engineering/standards/localization-locale-standard.md)。

## MDM Reference 接入

跨域只读查询优先依赖 [`MdmReferenceClient`](../../mom-mdm-platform/mom-mdm-client/src/main/java/io/github/chrisshi/mom/mdm/client/MdmReferenceClient.java)及 `mom-mdm-api` 的 Reference DTO，不能读 `mom_mdm` 表或做跨 Schema 业务 JOIN。当前客户端覆盖物料、制造资源、UOM、仓储位置、生产版本与质量规范等静态引用，输入/返回类型以接口为准。

真实消费者 [`OpeningInventoryReferenceService`](../../mom-wms-platform/mom-wms-server/src/main/java/io/github/chrisshi/mom/wms/application/OpeningInventoryReferenceService.java)在本地写事务外查询 MDM，校验 `Result`、`availableForNewUse` 和仓库/库位归属，然后把必要引用快照交给 WMS 本地事务。远端 401/403/404/5xx 等失败不能默默变成“引用无效”或本地缓存成功。查询与本地提交间没有跨服务原子性；当前证据不证明所有业务入口都已强制校验。
