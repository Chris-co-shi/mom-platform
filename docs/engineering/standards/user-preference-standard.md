# 用户偏好工程规范

## 1. 所有权与层级

用户偏好属于 System Platform，IAM 只提供账号标识和安全上下文。S16 V1 只实现 Platform Default 与 User Preference 两层；Application、Organization/Factory Default 和 Session Temporary Override 未获准实现。每个新增字段都必须声明允许的覆盖层级。

S16 V1 的显示偏好白名单仅为 Locale、显示时区、Theme、Density 和 Page Size；受限视图只保存列、最多三项排序、最多二十项类型化 Filter、视图 Page Size 与 Schema Version。IAM 不拥有这些值，也不把它们放入 JWT。

## 2. 初始化与授权边界

首次 Locale 跟随浏览器支持值，不支持则 `zh-CN`。浏览器时区不能被静默保存为永久偏好；时区初始化必须来自明确账号/Factory 配置或用户确认。

偏好不是 Permission，禁止保存 Role、Permission、Factory Scope、Party Scope、Token、Session、Client 授权、密码、安全策略或服务端业务状态。Default/Last Factory 必须等 IAM Factory Scope 与 MDM Factory 存在/启用批量校验契约同时稳定后再实施；S16 不以格式校验、跨 Schema 查询、IAM Repository 或远程 N+1 伪造有效性。

## 3. S16 已落地契约

S16 已根据 `mom-web` 与 `mom-mobile` 的真实消费证据建立类型化、白名单、版本化、有默认值、有校验、有审计的后端契约。显示偏好固定默认值为 `zh-CN`、`UTC`、`SYSTEM`、`COMFORTABLE`、`20`；Locale 只接受 `zh-CN`/`en-US`，显示时区必须为 Java `ZoneId` 可用的 IANA Zone ID，Page Size 只接受 10/20/50/100。

视图列、排序和 Filter 使用明确类型，禁止任意 Object、脚本、SQL、正则、Java 类型与 Secret-like Key；总有效负载最多 16 KiB。业务查询服务仍须重新校验字段白名单、Operator、类型和数据权限，不能直接执行保存的视图值。

所有 API 只从认证 JWT `sub` 解析当前 IAM User ID Reference，不接受 URL、Body 或 Header 自报 `userId`。GET 无记录时返回默认值、`version=0`、`persisted=false`；PUT 与 Reset 使用乐观版本，Reset 只清空覆盖或禁用视图，不物理删除。

S16 不实现缓存。未来客户端在服务不可用时按“本地最后成功版本 -> 应用静态默认”回退；服务端缓存、TTL 和失效属于 S18。降级不得阻止登录、扩大权限、改变 Factory Scope、Factory 业务日期、金额、权威单位或业务事实。

Default Application 延后到 S17 Application Catalog；Dashboard/Favorites 因没有稳定对象引用和真实调用方而 Deferred。客户端跨仓库正式接入为后续独立任务，S16 不修改 `mom-web`、`mom-mobile`、`/api/iam/me` 或 Token Claim。
