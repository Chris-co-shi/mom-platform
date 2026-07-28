# 国际化与 Locale 工程规范

## 1. 事实与项目决策

BCP 47 定义语言 Tag；HTTP `Accept-Language` 表达客户端语言偏好；OIDC `ui_locales` 只提示授权服务器 UI。MOM 决定初始支持 `zh-CN`、`en-US`，默认回退 `zh-CN`。Java 内部可使用 `Locale`，跨服务契约只使用标准 Tag，不使用 `zh_CN`、`en_US` 或数字枚举。

## 2. 解析、Alias 与回退

输入先去除协议外围非法空白，再按 BCP 47 解析和规范大小写，最后匹配显式支持列表。只允许受控 Alias：初始至少为 `zh-Hans-CN -> zh-CN`、`en -> en-US`；不得依赖宿主机默认 Locale 或模糊匹配到不同地区。非法或不支持值不用于资源路径拼接。

未认证 IAM 页面选择顺序：OIDC `ui_locales`、页面临时显式选择、`Accept-Language`/浏览器语言、`zh-CN`。已认证应用未来选择顺序：会话临时选择、System 用户偏好、应用默认、浏览器/设备 Locale、`zh-CN`。临时选择只有用户显式保存时才写永久偏好。

`Accept-Language` 只影响展示消息，不影响数据过滤、排序、金额、时区、权限或 Factory 业务日期；不把完整 Header 写入指标。`ui_locales` 不修改永久偏好，不进入 Token Claim，也不改变 Client、Scope 或 Redirect URI。

## 3. 资源所有权

- mom-web 各应用：按钮、标签、菜单固定标题、帮助和前端固定校验。
- mom-mobile：移动端静态资源。
- 各后端服务：Bean Validation、固定业务错误说明和审计类型说明。
- System：仅在 S15 有真实调用方时拥有运行期动态菜单标题、公告、字典显示名等动态资源。

平台不建立 Web 静态资源中心，也不以 Nacos 或数据库立即替换应用内置 Resource Bundle。资源文件启用 `zh-CN` 或 `en-US` 时必须成对存在且 Key 对齐；缺失翻译按明确默认语言回退并可观测。

## 4. 错误与日志

普通业务 ProblemDetail 的 `code`、`fieldErrors[].code` 稳定且不本地化，`detail` 和字段 `message` 可本地化；OAuth2/OIDC 标准错误保持协议格式。安全审计保存稳定事件 Code。服务器日志采用稳定结构和运维语言，不根据用户 Locale 改变字段或机器码。

## 5. S15 进入条件

只有仓库或运行证据证明至少一个真实调用方需要运行期编辑、发布和读取动态翻译，且已明确所有权、缓存、版本、回退、审计和兼容策略，S15 才实现动态国际化。当前没有该证据，因此 S15 保持条件性计划输入，不预建表、API、缓存或通知。
