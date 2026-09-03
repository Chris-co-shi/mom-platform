# MOM Platform 文档中心

本目录是 `mom-platform` 的需求、计划、架构、安全协议和架构决策权威入口。

> 当前代码收敛工作以 `fix/mini-auth` 为事实来源。认证与授权已经从旧完整 IAM / Authorization Server 方案收敛为 Mini Auth V1；Gateway 已删除旧 JWT/JWK/revoked-sid 认证链，当前只做 Bearer 边缘检查与 Header 清洗，真实 Token 认证由各 Resource Server 完成。

## 文档使用原则

1. 需求文档回答“系统必须提供什么能力”。
2. 计划文档回答“按什么顺序实现、如何验收”。
3. 架构文档描述当前系统整体结构、边界和协作方式。
4. ADR 记录关键架构选择及其后果。
5. 代码与文档冲突时先确认决策变化，禁止静默偏离。
6. 历史 P1.5/P1.6 文档不得覆盖当前 Mini Auth V1 语义。

## 当前阶段

- `mom-core`：CurrentActor V1 已收敛。
- `mom-data`：数据基础设施 V1 已收敛。
- `mom-security`：Redis Opaque Token、TokenStore、Introspector、Servlet Resource Server 已收敛。
- `mom-gateway`：旧 JWT/JWK/Audience/revoked-sid 链已移除；当前使用 Bearer 形态检查并原样转发 Authorization。
- `mom-auth-platform`：`mom-auth-api` / `mom-auth-server`、`mom_auth` Schema、5 张核心表和管理员初始化脚本已建立；业务代码下一步开始。
- `mom-openfeign`：当前只有 Correlation ID 与 CircuitBreaker 命名，Bearer 传播尚未实现，暂时冻结等待单独技术决策。

## Mini Auth V1 当前权威文档

1. [ADR-040：Mini Auth 与 Redis Opaque Token 认证基线](adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
2. [ADR-041：Mini Auth 简化三层包结构](adr/ADR-041-Mini-Auth简化三层包结构.md)
3. [P1.5 认证与授权设计基线](security/P1.5-认证与授权设计基线.md)
4. [安全架构](architecture/安全架构.md)
5. [Mini Auth 数据库与代码分层](architecture/Mini-Auth数据库与代码分层.md)
6. [Mini Auth 管理员初始化](security/Mini-Auth管理员初始化.md)
7. [CurrentActor 与数据审计](architecture/CurrentActor与数据审计.md)

## 当前认证链

```text
Client
  ↓ Authorization: Bearer <opaque-token>
Gateway
  ↓ Bearer 形态检查 + X-MOM-* 清洗，不查 Redis
Resource Server
  ↓ MomOpaqueTokenIntrospector
Redis MomTokenStore
  ↓
SecurityContext / @PreAuthorize
```

服务间同步调用后续传播原始 Bearer Credential，由目标 Resource Server 再次验证；不传播可信 `X-MOM-USER/ROLE/PERMISSION` 结果 Header。

## Mini Auth 当前代码结构

```text
io.github.chrisshi.mom.auth
├── AuthApplication
├── controller
├── service
└── infrastructure
```

```text
controller → service → infrastructure
```

`service` 直接承担业务用例和聚合职责，不额外创建 `application` / `domain` 顶层包。

## Mini Auth 当前数据库

```text
Schema: mom_auth

auth_user
auth_role
auth_permission
auth_user_role
auth_role_permission

V1__create_auth_core_tables.sql
V2__seed_platform_admin.sql
admin → PLATFORM_ADMIN
```

## 需求与计划

- [产品范围](requirements/产品范围.md)
- [V1 需求清单](requirements/V1需求清单.md)
- [V1 路线图](plans/V1路线图.md)
- [Phase 01 技术骨架](plans/Phase-01-技术骨架计划.md)
- [P1.5 历史实施计划](plans/P1.5-认证与授权闭环计划.md)
- [P1.6 历史治理计划](plans/P1.6-IAM与System平台治理计划.md)

P1.5/P1.6 的旧认证实施资料继续作为历史证据；与 ADR-040/041 冲突时，以当前 ADR 为准。

## 技术架构

- [服务端 Package 与目录架构规范](engineering/standards/package-directory-architecture-standard.md)
- [数据库表结构设计规范](engineering/standards/database-schema-design-standard.md)
- [系统上下文](architecture/系统上下文.md)
- [服务与容器架构](architecture/服务与容器架构.md)
- [模块边界](architecture/module-boundaries.md)
- [数据架构](architecture/数据架构.md)
- [安全架构](architecture/安全架构.md)
- [Mini Auth 数据库与代码分层](architecture/Mini-Auth数据库与代码分层.md)
- [IAM 数据库与领域模型（历史）](architecture/IAM数据库与领域模型.md)
- [CurrentActor 与数据审计](architecture/CurrentActor与数据审计.md)
- [集成架构](architecture/集成架构.md)
- [可观测性架构](architecture/可观测性架构.md)
- [部署架构](architecture/部署架构.md)

## 架构决策

- [ADR 索引](adr/README.md)
- [ADR-040：Mini Auth 与 Redis Opaque Token](adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md)
- [ADR-041：Mini Auth 简化三层包结构](adr/ADR-041-Mini-Auth简化三层包结构.md)
- [ADR-026：业务表禁止物理外键](adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)

ADR-027/028 对其他 bounded context 继续有效；`mom-auth-server` V1 的精确例外由 ADR-041 决定。

## 安全与凭据

- [Mini Auth 管理员初始化](security/Mini-Auth管理员初始化.md)
- [IAM 内置管理员初始化（历史）](security/IAM内置管理员初始化.md)
- [IAM 内置管理员恢复（历史）](security/IAM内置管理员恢复.md)
- [P1.6 S00 配置凭据清单](security/P1.6-S00-配置凭据暴露与轮换清单.md)

## 文档维护

- [文档维护约定](文档维护约定.md)

文档修改继续在 `agent/complete-chinese-docs` 分支完成；架构决策变化通过新 ADR 记录，不静默覆盖历史 ADR。
