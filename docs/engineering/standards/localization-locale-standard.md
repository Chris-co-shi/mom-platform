# 国际化与 Locale 工程规范

## 1. 事实与项目决策

BCP 47 定义语言 Tag；HTTP `Accept-Language` 表达客户端语言偏好；OIDC `ui_locales` 只提示授权服务器 UI。MOM 决定初始支持 `zh-CN`、`en-US`，默认回退 `zh-CN`。Java 内部可使用 `Locale`，跨服务契约只使用标准 Tag，不使用 `zh_CN`、`en_US` 或数字枚举。

## 2. 解析、Alias 与回退

输入先去除协议外围非法空白，再按 BCP 47 解析和规范大小写，最后匹配显式支持列表。只允许受控 Alias：初始至少为 `zh-Hans-CN -> zh-CN`、`en -> en-US`；不得依赖宿主机默认 Locale 或模糊匹配到不同地区。非法或不支持值不用于资源路径拼接。

未认证页面选择顺序由当前认证方案单独决定。已认证请求的展示 Locale 优先采用本次请求显式值或
`Accept-Language`，不支持时回退到 `system_supported_locale` 中唯一启用的默认 Locale。System V1 不保存用户
永久 Locale 偏好；未来如需持久化，必须先明确所有权，不能默认放入 IAM、Token 或 System。

`Accept-Language` 只影响展示消息，不影响数据过滤、排序、金额、时区、权限或 Factory 业务日期；不把完整 Header 写入指标。`ui_locales` 不修改永久偏好，不进入 Token Claim，也不改变 Client、Scope 或 Redirect URI。

## 3. 资源所有权

- mom-web 各应用：按钮、标签、菜单固定标题、帮助和前端固定校验。
- mom-mobile：移动端静态资源。
- Framework：`framework.*` 固定框架错误，使用 classpath 双语 Resource Bundle。
- 各后端服务：拥有本 bounded context 的固定与动态业务错误；动态资源使用本服务 namespace 和数据库。
- System：拥有平台支持 Locale、受限字典与 `system.*` 动态文案，不持有 MDM/MES/WMS/QMS Translation。

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
