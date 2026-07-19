# ADR-003：OAuth2.1 与 OIDC 授权模型

- 状态：Superseded
- 日期：2026-07-18
- 被替代日期：2026-07-19
- 替代决策：[ADR-019：P1.5 认证与授权闭环](ADR-019-P1.5认证与授权闭环.md)

> 本 ADR 保留历史背景，不再作为当前实现依据。OAuth Client、user_type、Token、Session、RBAC、Factory/Party Scope、Gateway/Resource Server、Web/Mobile Runtime 的当前结论以 ADR-019 和 [P1.5 认证与授权设计基线](../security/P1.5-认证与授权设计基线.md) 为准。

## 1. 背景

MOM 同时服务 Web、PDA、供应商、客户、内部服务、ERP Simulator、PCS 和 WCS，需要统一身份、授权、Token 生命周期和服务间认证。

## 2. 历史候选方案

- 自研用户名密码登录和 JWT：实现快，但协议、安全和生命周期风险高。
- 复制通用后台的密码授权模式：前端简单，但不符合 OAuth2.1 方向。
- Spring Authorization Server + 标准 OAuth2/OIDC：实现成本较高，但标准化、可扩展且适合长期维护。

## 3. 历史决策

- 使用 Spring Authorization Server。
- Web、门户和 PDA 使用 Authorization Code + PKCE。
- 内部服务、ERP Simulator、PCS 和 WCS 使用 Client Credentials。
- Access Token 使用短期 JWT。
- Refresh Token 由授权服务器管理，支持轮换、吊销和重放检测。
- 禁止 Resource Owner Password Credentials Grant。

## 4. 被替代原因

本 ADR 只冻结了协议方向，没有定义：

- 三种 `user_type` 与应用访问矩阵；
- 四个 Public Client 的固定 Client ID；
- Web/Mobile Token 存储与恢复；
- Refresh Token 摘要、单 ACTIVE Token、绝对 Session 期限和重放处置；
- RBAC、Factory Scope、Party Scope；
- revoked sid、Redis Fail Closed；
- Gateway 与业务服务最终授权边界；
- CurrentActor、数据审计和 IAM 管理能力。

ADR-019 对上述内容进行了完整冻结，因此替代本 ADR。

## 5. 仍然有效的历史原则

以下原则被 ADR-019 继承：

- 不自研密码 JWT 协议；
- 用户客户端使用 Authorization Code + PKCE；
- 使用 OpenID Connect；
- 禁止 Resource Owner Password Credentials Grant；
- Gateway 与业务服务执行双层安全校验。
