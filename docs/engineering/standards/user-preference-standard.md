# 用户偏好工程规范

## 1. 所有权与层级

用户偏好属于未来 System Platform，IAM 只提供账号标识和安全上下文。覆盖层级为：Platform Default -> Application Default -> Organization/Factory Default（仅真实需要时）-> User Preference -> Session Temporary Override。每个 Key 必须声明允许哪些层级覆盖。

System 可拥有 Locale、显示时区、日期/数字显示格式、显示单位、表格列、主题、默认首页和当前 Factory。IAM 不拥有这些值，也不把它们放入 JWT。

## 2. 初始化与授权边界

首次 Locale 跟随浏览器支持值，不支持则 `zh-CN`。浏览器时区不能被静默保存为永久偏好；时区初始化必须来自明确账号/Factory 配置或用户确认。

当前 Factory 可以作为偏好，但每次启动和切换都必须重新校验用户 Factory Scope 及 Factory 有效性。偏好不是 Permission，禁止保存 Role、Permission、Factory Scope、Party Scope、Token、Session、Client 授权、密码、安全策略或服务端业务状态。

## 3. S16 实现条件

S16 实现必须有真实 Web/Mobile 消费方和已批准契约，并满足：类型化、白名单、版本化、有默认值、有校验、有审计；不得用任意深层 JSON 承载全部偏好，也不得允许用户写系统保留 Key。数据库为权威，缓存可重建、有版本、TTL 和失效通知。

偏好缓存失败时可回退应用默认；Locale 回退默认语言，显示时区按场景回退 Factory 时区或 UTC，显示单位回退业务标准单位。降级不得改变权限、Factory 业务日期、金额、权威单位和事实。

S05 不创建 System 模块、表、API，不修改 `/api/iam/me`、Token Claim 或客户端仓库。
