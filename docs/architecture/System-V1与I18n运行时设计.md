# System V1 与 I18n 运行时设计

## 1. 文档定位

本文描述 `fix/system` 收敛后的当前实现，是 System V1 的代码导航、数据模型、事务与失败语义说明。架构决策以
[ADR-043](../adr/ADR-043-System-V1能力与I18n数据所有权.md) 为权威；V1～V9 对应报告属于历史证据。

## 2. 范围

System V1 提供平台字典、支持 Locale、`system.*` Message/Translation 管理与运行时读取。不提供 Parameter、
用户偏好、应用目录、导航、发布快照、缓存、MQ、Outbox/Inbox 或 IAM 引用校验。

```text
HTTP Controller
    ↓ Request / Response
Application（校验、事务、并发语义）
    ↓ MyBatis-Plus Mapper / Entity
PostgreSQL mom_system
```

这是 ADR-042 Level 1。当前规则是简单唯一性、状态和 Placeholder 一致性，不足以触发独立 Domain 或 Port/Adapter。

## 3. 数据模型

| 表 | 类型与生命周期 | 关键约束 | 删除策略 |
|---|---|---|---|
| `system_dictionary` | 平台配置主表 | `dictionary_code` 唯一 | 逻辑删除能力已具备，V1 不开放删除 API |
| `system_dictionary_item` | 字典从表 | `(dictionary_id,item_key)` 唯一 | 同上 |
| `system_supported_locale` | 平台 Locale 配置 | `locale_code` 唯一；仅一个未删除默认项；默认项必须启用 | 同上 |
| `system_i18n_message_definition` | `system.*` 文案定义 | `(namespace,message_key)` 唯一；namespace 必须属于 `system` | 同上 |
| `system_i18n_translation` | Message 的 Locale 文案 | `(message_id,locale_code)` 唯一；纯文本 | 同上 |

全部 Java 技术主键为 `String`、数据库为 `varchar(19)`，时间点为 `Instant/timestamptz`。五张当前业务表继承
`BaseEntity`，获得审计、乐观锁和逻辑删除能力。表间不建立物理外键；Application 在写入前验证引用，唯一约束和
受影响行数作为并发兜底。

V10 新增三张 Locale/I18n 当前表；V1～V9 历史表保留但运行时代码不读写。V10 初始化 `zh-CN`（默认）、
`en-US` 和 System 核心错误的双语文案。

## 4. 用例与事务

### 4.1 字典

管理入口调用 `DictionaryApplication`。类型 Code、条目 Key 创建后保持稳定；修改与启停携带 `version`，由
MyBatis-Plus 乐观锁执行 CAS。运行时只返回启用类型下的启用条目，并按 `sortOrder/itemKey` 排序。

### 4.2 支持 Locale

`SupportedLocaleApplication` 负责创建、修改、启停和切换默认项。默认切换在一个本地事务中锁定当前 Locale 行，
先撤销旧默认再设置新默认；数据库部分唯一索引确保最终只有一个默认项。默认项不能直接禁用。

### 4.3 Message 与 Translation

`I18nApplication` 创建不可变的 `(namespace,messageKey)` 身份。Translation 保存前锁定 Message，验证 Locale
已启用，并将新文本的数字 Placeholder 集合与已有 Translation 比较。任何差异都拒绝整个事务。

成功修改 Locale、Message 或 Translation 后调用 `I18nChangeNotifier`。Notifier 检测到活动事务时注册
`afterCommit` 回调，因此回滚不会产生“已变更”通知。SSE 只携带失效信号，不携带业务文案快照。

## 5. 解析与运行时 Bundle

单条服务端消息通过 Core 的 `I18nMessageResolver` 契约：

```text
framework.* → classpath MessageSource → zh-CN/en-US
system.*    → PostgreSQL Translation → 请求 Locale → 默认 Locale → messageKey
```

前端运行时通过 Framework 通用 Controller 请求一个或多个 namespace。System 的
`SystemI18nRuntimeProvider` 只接受 `system` 或 `system.*`，批量查询启用 Message 与 Translation，返回每个
namespace 的键值 Map。客户端收到 SSE 后重新拉取，而不是把 SSE 当成可靠事件流。

## 6. API 边界

- `/api/system/admin/dictionaries` 与 `/api/system/dictionaries/{code}/items`：字典管理与运行时读取；
- `/api/system/i18n/locales`：支持 Locale 读取，`/admin` 子路径负责管理；
- `/api/system/admin/i18n/messages`：Message/Translation 管理；
- `/api/system/i18n/runtime/bundles`：一个或多个 namespace 的运行时 Bundle；
- `/api/system/i18n/runtime/events`：当前实例 SSE 失效通知。

Controller 只处理 HTTP、校验、权限注解和响应转换；Application 不返回 HTTP `Result`，Infrastructure 不感知
HTTP。稳定错误响应保留 `code`，展示文案按请求 Locale 解析。

Servlet Security 异常不会进入 ControllerAdvice。Framework 的 AuthenticationEntryPoint 与 AccessDeniedHandler
直接复用同一 Resolver，分别返回稳定 `AUTHENTICATION_REQUIRED` / `ACCESS_DENIED` code 和本地化 message。

## 7. 失败模型

| 故障 | 行为 |
|---|---|
| 唯一键并发冲突 | 转换为稳定 Conflict code，不覆盖已有数据 |
| 乐观锁冲突 | 409，调用方重新读取后决定是否重试 |
| 默认 Locale 并发切换 | 行锁串行化，部分唯一索引最终兜底 |
| PostgreSQL 不可用 | 管理写入和 `system.*` 动态读取失败；不回退陈旧缓存 |
| SSE 连接断开 | 不影响事务；客户端重连后重新拉取 |
| 事务回滚 | 不发送变更通知 |
| 请求 Locale 非法或未启用 | 使用数据库默认 Locale |
| 请求/默认 Translation 都缺失 | 返回稳定 message key |

## 8. 当前限制

SSE 仅对本 JVM 生效，不提供跨实例广播；不提供翻译工作流、审批、版本发布、回滚快照、HTML 富文本、ICU
MessageFormat 或复数规则；不清理历史表；不替业务模块存储其 Translation。

## 9. Review 导航

优先检查：V10 Migration 的约束与种子、默认 Locale 切换事务、Placeholder 校验、运行时 fallback、Notifier 的
`afterCommit` 行为，以及架构测试中对旧能力和依赖的禁止规则。
