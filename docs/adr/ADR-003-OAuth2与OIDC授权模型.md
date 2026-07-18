# ADR-003：OAuth2.1 与 OIDC 授权模型

- 状态：Accepted
- 日期：2026-07-18
- 关联文档：[安全架构](../architecture/安全架构.md)

## 1. 背景

MOM 同时服务 Web、PDA、供应商、客户、内部服务、ERP Simulator、PCS 和 WCS，需要统一身份、授权、Token 生命周期和服务间认证。

## 2. 候选方案

- 自研用户名密码登录和 JWT：实现快，但协议、安全和生命周期风险高。
- 复制通用后台的密码授权模式：前端简单，但不符合 OAuth2.1 方向。
- Spring Authorization Server + 标准 OAuth2/OIDC：实现成本较高，但标准化、可扩展且适合长期维护。

## 3. 决策

- 使用 Spring Authorization Server。
- Web、门户和 PDA 使用 Authorization Code + PKCE。
- 内部服务、ERP Simulator、PCS 和 WCS 使用 Client Credentials。
- Access Token 使用短期 JWT。
- Refresh Token 由授权服务器管理，支持轮换、吊销和重放检测。
- 禁止 Resource Owner Password Credentials Grant。

## 4. 授权边界

- Gateway 执行基础 Token 校验、路由 Scope 和限流。
- 领域服务再次校验 Token、Audience、权限和数据范围。
- 不把 Gateway Header 作为唯一可信授权依据。

## 5. 后果

正向：标准协议、客户端类型清晰、适合服务间认证和未来第三方集成。

负向：PDA 和前端登录流程更复杂，需要安全浏览器跳转和 Refresh Token 生命周期管理。

## 6. 验证方式

- PKCE 正常登录和错误校验。
- Client Credentials 服务调用。
- Token 过期、Audience 和 Scope 不匹配。
- Refresh Token 轮换与重放。
- Gateway 与领域服务双层授权。

## 7. 替代条件

只有在 Spring Authorization Server 无法满足关键协议或维护要求时，才通过新 ADR 评估外部 IAM；不得回退为自研密码登录。