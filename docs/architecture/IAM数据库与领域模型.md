# IAM 数据库与领域模型（历史资料）

- 状态：**Historical / Superseded for Mini Auth V1**
- 原阶段：`P1.5 S02`
- 原 Schema：`mom_iam`
- 当前替代文档：[Mini Auth 数据库与代码分层](Mini-Auth数据库与代码分层.md)
- 当前认证决策：[ADR-040](../adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- 当前包结构：[ADR-041](../adr/ADR-041-Mini-Auth简化三层包结构.md)

> 本文只保留旧 IAM / P1.5 设计的历史语义，不得作为 `fix/mini-auth` 当前实现依据。

## 1. 历史方案概览

旧 IAM 曾计划或实现 User Profile、External Party Binding、Role/Permission、Factory Scope、Mobile Access、OAuth Client Policy、Spring Authorization Server JDBC 表、Session、Refresh Token 和 Security Audit。

原运行基线：

```text
Schema          = mom_iam
Application     = mom-iam-server
Flyway Location = classpath:db/migration/iam
```

## 2. 为什么被替代

旧方案引入 JWT/JWK、Authorization Server、Refresh Rotation、Session/revoked sid、OAuth Client、Factory/Party Scope、外部身份、安全审计以及复杂 `Web → Application → Domain Port ← Infrastructure` 分层。

这些能力在当前第一版显著降低项目可理解性和掌控力，因此由 Mini Auth V1 替代。

## 3. 当前 Mini Auth

```text
Schema: mom_auth

auth_user
auth_role
auth_permission
auth_user_role
auth_role_permission
```

Token 不保存到 PostgreSQL，由 `MomTokenStore` 存入 Redis。

```text
classpath:db/migration/auth
V1__create_auth_core_tables.sql
V2__seed_platform_admin.sql
```

当前初始化：`admin → PLATFORM_ADMIN`。关系表按 ADR-026 不建立物理外键。

## 4. 当前代码结构

```text
io.github.chrisshi.mom.auth
├── controller
├── service
└── infrastructure
```

依赖方向：`controller → service → infrastructure`。

## 5. 历史价值

旧 IAM 资料仍可用于未来真实需求参考，例如标准 OAuth2/OIDC、第三方开放平台、多组织 SSO、Mobile PKCE、Refresh Token、外部身份、安全审计和 Factory/Party 授权。

任何上述能力重新进入当前系统，都必须重新评估并通过新的 ADR，不得直接复制旧表、旧 JWT Claim 或旧 Session 模型。

需要查看完整旧实现细节时，从 Git 历史以及 `phase/p1.5-auth-authorization` 等历史分支追溯。
