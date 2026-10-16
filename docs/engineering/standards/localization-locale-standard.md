# 国际化与 Locale 工程规范

> 适用于 Locale 解析、国际化文案生成、Key/Owner 选择、翻译种子与冗余治理。新增或修改国际化内容前必须读取本文；不涉及国际化时无需读取。

## 1. 事实与项目决策

BCP 47 定义语言 Tag；HTTP `Accept-Language` 表达客户端语言偏好；OIDC `ui_locales` 只提示授权服务器 UI。MOM 决定初始支持 `zh-CN`、`en-US`，默认回退 `zh-CN`。Java 内部可使用 `Locale`，跨服务契约只使用标准 Tag，不使用 `zh_CN`、`en_US` 或数字枚举。

## 2. 解析、Alias 与回退

输入先去除协议外围非法空白，再按 BCP 47 解析和规范大小写，最后匹配显式支持列表。只允许受控 Alias：初始至少为 `zh-Hans-CN -> zh-CN`、`en -> en-US`；不得依赖宿主机默认 Locale 或模糊匹配到不同地区。非法或不支持值不用于资源路径拼接。

未认证页面选择顺序由当前认证方案单独决定。已认证请求的展示 Locale 优先采用本次请求显式值或
`Accept-Language`，不支持时回退到 `system_supported_locale` 中唯一启用的默认 Locale。System V1 不保存用户
永久 Locale 偏好；未来如需持久化，必须先明确所有权，不能默认放入 IAM、Token 或 System。

`Accept-Language` 只影响展示消息，不影响数据过滤、排序、金额、时区、权限或 Factory 业务日期；不把完整 Header 写入指标。`ui_locales` 不修改永久偏好，不进入 Token Claim，也不改变 Client、Scope 或 Redirect URI。

## 3. 资源所有权

- mom-web：负责文案展示、稳定 Key/参数契约与 Owner 调用；动态 UI 译文以所属 Owner 的数据库 Bundle 为权威，不在前端维护第二份业务译文或完整静态回退词典。
- mom-mobile：移动端静态资源。
- Framework：`framework.*` 固定框架错误，使用 classpath 双语 Resource Bundle。
- 各后端服务：拥有本 bounded context 的固定与动态业务错误；动态资源使用本服务 Owner 和数据库。
- System：拥有平台支持 Locale、受限字典与 `system.*` 动态文案，不持有 MDM/MES/WMS/QMS Translation。

服务 Owner 是数据边界，不作为 Key 的 namespace 维度。同一服务内 `message_key` 全局唯一且不可改名；不同服务可以存在相同 Key。写入前以 Locale.ROOT 统一为小写，拒绝空白、全角和非 ASCII；运行时以小写稳定 Key 查询。相同 Locale 下完全相同的译文只触发管理端提醒并列出其他 Key，用户确认后仍可保存；数据库不得对译文文本设置唯一约束。详情见 [Key 维度与 Owner 边界设计](../../architecture/I18n-Key维度与Owner边界设计.md)。

平台不建立中央 Translation Center，也不以 Nacos 或数据库替换全部应用内置 Resource Bundle。资源文件启用
`zh-CN` 或 `en-US` 时必须成对存在且 Key 对齐。动态文案回退顺序固定为“请求 Locale → 默认 Locale →
messageKey”；业务服务不得为解析本地错误同步调用 System。

## 4. 错误与日志

普通业务错误的 `code`、`fieldErrors[].code` 稳定且不本地化，`detail` 和字段 `message` 可本地化；认证协议错误
保持协议格式。安全审计保存稳定事件 Code。服务器日志采用稳定结构和运维语言，不根据用户 Locale 改变字段或
机器码。Translation 只允许纯文本，同一 Message 的数字 Placeholder 集合必须跨 Locale 一致。

## 5. 当前实现基线

System V1 的权威决策为 ADR-043：不再使用 S15 的显式发布、不可变快照、回滚版本模型，也不进入 User
Preference。Framework 提供薄 `I18nMessageResolver`、`I18nRuntimeProvider`、事务提交后本实例 SSE 通知及通用
Runtime Controller；System 实现 `system.*`。多实例通知、缓存、MQ 和集中翻译运营均不属于当前基线。

## 6. 公共 UI 与领域文案复用

- 与领域无关的普通 UI 操作优先复用 System 已有公共 Key。例如关闭弹窗使用 `system.common.close`，取消本次编辑使用 `system.common.cancel`，不得为每个业务页面重复创建对应译文。
- 领域名称、业务状态、业务动作、约束与错误仍由所属服务拥有。“取消生产订单”与“取消编辑”不是同一语义；业务状态“已关闭”也不能复用关闭弹窗的文案。文字相同不构成迁移 Owner 或合并 Key 的依据。
- 复用前比较语义、全部已有语言和参数契约。同一默认语言相同，但其他语言、数字占位符集合或参数含义不同的 Key，不得自动合并；不得通过删除语言、改写用户译文或丢弃参数制造一致。
- 跨 Owner 复用必须显式指定 Owner。Web 使用现有 `tForOwner('system', 'system.common.cancel')` 等入口，并确认该 Owner 的当前 Locale Bundle 已加载；切换语言及重新加载后仍须正确取词。禁止依赖多个 Owner Bundle 合并的偶然顺序、同名 Key 覆盖或缺失时串用其他 Owner。
- 禁止将一个 Key 写成另一条 Translation 的引用表达式来模拟别名；当前运行时不解析跨 Key 引用。业务服务也不得为复用普通 UI 文案而跨 Schema 查询 System 或同步远程解析本地业务错误。

## 7. 新增或生成国际化内容的必经流程

1. 明确文案场景、Owner、语义及参数。先判断是普通 UI 操作还是领域表达；不能确定领域归属时由用户决定，不因文字相同擅自调整边界。
2. 定向检索 System 公共 Key、所属 Owner 的现有定义、相关 Migration 和相似页面。新增种子或计划清理数据时，还须核对可访问的实际数据库与 Flyway 最终状态；无实库证据时明确限制，不把历史种子当成当前数据。
3. 已有 Key 满足第 6 节条件时直接复用；确需新增时使用同 Owner 内唯一的小写稳定 Key，并遵守现有 Key 格式、长度和数字占位符规则，不恢复 namespace，不建立文本唯一约束。
4. 新增定义及所需语言译文使用该 Owner 的新 Flyway Migration；前端只补必要的 Key/参数契约与调用。已有额外语言和自定义内容不得覆盖；缺失语言按既有回退规则处理，不伪造译文来证明可合并。
5. 按影响面验证实际页面、Owner 加载、语言切换及占位符替换。数据迁移还须通过 PostgreSQL 集成验证；交付说明复用了什么、为什么新增或保留独立 Key、执行了哪些验证及证据限制。

人工开发与 AI 生成均遵循上述流程；已有相似种子不构成继续复制的理由。本规范用于开发与评审，不表示已实现跨 Owner 自动语义去重或数据库文本唯一校验。

## 8. 冗余候选与安全清理

重复统计只产生候选。脚本或 SQL 应注明 Owner、数据库/Flyway 版本、语言集合及启用/逻辑删除过滤条件；不得把不同快照或筛选口径下的数量直接对比。历史 Migration 仅用于重建最终种子状态，不能单独证明记录无人使用或未被修改。

清理按以下顺序执行：

1. 核查候选的语义、所有语言、占位符与参数含义，并检查前后端字面引用、动态拼接、共享组件、配置、发布快照和其他已知运行时消费者。类型声明不等于运行时调用，但没有字面引用也不等于没有消费者。
2. 先迁移并发布消费者，确认仍在运行的旧页面、缓存版本及其他客户端不再依赖旧 Key。代码工作区已修改或已提交不能替代上线证据；不兼容旧消费者时保留旧定义，推迟清理。
3. 新增所属 Owner 的 Flyway Migration，不修改历史 Migration、不改 Framework Store、不查询其他 Owner Schema 的业务数据。各 Owner 的核查分别进行；其他领域文案和目标公共译文不随清理修改。
4. 删除或停用前显式列出预期 Key、Message ID、Translation ID、完整语言集合、当前译文、版本、启用/删除状态及种子审计条件，并检查数据库外键和本 Owner 已知逻辑引用。包括逻辑删除的译文在内，任一缺失、额外语言、人工编辑、身份变化或引用都必须使本次迁移失败；不能静默跳过或删除自定义内容。
5. 预检与删除处于同一事务，防止预检后目标和已检查引用被并发写入，并核对实际删除行数。先删除匹配的 Translation，再删除 Message；任何异常均回滚本次迁移及其 Flyway 成功记录，不利用级联删除清理未知引用。

无法证明安全的候选继续保留，说明原因并推进其他安全项。规范不要求将所有相同译文集中到 System，也不要求为了去重新增别名层、消息总线或通用翻译框架。

## 9. 数据治理验收

- PostgreSQL 集成测试覆盖新建数据库、已应用历史版本的升级、非目标及自定义数据保留、额外语言和引用阻断、异常后的整体回滚；不得使用 H2 或仅凭 SQL 文本断言替代。
- 对比迁移前后非目标记录，确认其他领域文案和 System 公共译文保持不变。必要时在实际数据的隔离副本验证；副本验证通过不表示实库已执行迁移。
- 交付明确 Message/Translation 删除或停用数量、Migration、验证结果、保留候选原因、实库与最终种子差异，以及实库是否已应用。未运行或跳过的验证不得描述为通过。
