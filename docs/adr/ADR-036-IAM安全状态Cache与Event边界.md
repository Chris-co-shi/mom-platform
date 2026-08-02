# ADR-036：IAM 安全状态、Cache 与 Event 边界

- 状态：Accepted
- 日期：2026-08-01
- 决策人：MOM Platform 维护者
- 关联需求：P1.6 Framework Governance Phase 5
- 关联文档：[ADR-024](ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md)、[ADR-032](ADR-032-Cache-Region与Factory-Scope兼容迁移.md)、[ADR-035](ADR-035-Framework-Freeze与平台适应度函数.md)

## 1. 背景

IAM 当前使用 Redis 保存 revoked SID，并在 IAM、Gateway 与业务 Resource Server 之间进行 Fail Closed 检查。旧 IAM Store 自己拼接 Key 并定义本地不可用异常，而 `mom-security` 已提供共享 Key 和异常。IAM 目前没有普通 Permission/Token Cache，也没有真实外部 IAM Domain Event 消费者。

## 2. 问题

如何消除 revoked SID Key/异常重复，同时避免把安全状态误迁移到普通 Cache、为未来消费者提前创建 IAM Event/Outbox，或用 Resilience4j 包装 PostgreSQL/Redis/Nacos？

## 3. 候选方案

### 方案 A：把 revoked SID 迁移到 CacheService

优点：表面上统一 Redis 访问。

缺点：Cache fail-open 与安全撤销 fail-closed 冲突；Miss 可能被解释为 Session 有效，产生越权风险。

### 方案 B：保持 Security Store，复用 mom-security Key/异常

优点：保留现有 Key、TTL 和 Fail Closed 语义；消除重复实现；Gateway/Resource Server 一致。

缺点：IAM 仍有直接 StringRedisTemplate，但它是精确 Security Adapter 例外，不是通用 Cache。

### 方案 C：预先建立 IamEventType 与 Outbox

优点：未来可能更快接消费者。

缺点：当前没有真实外部消费者，违反禁止过度抽象和空 Topic/表原则。

## 4. 决策

采用方案 B：

- revoked SID 是 IAM 权威安全状态，不是 Cache；
- IAM Store 复用 `MomRevokedSessionKeys` 与 `MomRevocationStoreUnavailableException`；
- Redis 不可用或结果不确定继续 Fail Closed；
- 每个 Key TTL 至少覆盖已签发 Access Token 剩余寿命；
- IAM 不引入 Caffeine，不创建普通 Permission/Token Cache；
- 当前不新增 `IamEventType`、Domain Event Topic 或 Outbox 表；
- IAM Security Audit 保持本地审计事实；
- PostgreSQL、Redis 与 Nacos 不用 Resilience4j 包装。

## 5. 抽象成立依据

- `mom-security` Key/异常已被 Gateway、业务 Resource Server 与 IAM 三类真实消费者共享，属于平台安全不变量。
- 不新增 IAM Cache/Event 抽象：当前没有两个消费者，也没有待发布的跨服务业务事实。
- 未来出现真实 IAM 跨服务消费者时，必须先定义撤销延迟、Payload 脱敏、Consumer 幂等和失败语义，再创建新的 IAM Event ADR。

## 6. 安全与事务语义

- `false` 只能来自 Redis 明确返回 Key 不存在；连接错误、null/不确定结果不得放行。
- revoke 写入失败向上抛出共享不可用异常，调用方不得返回成功。
- revoked SID 不经过 Cache L1/L2，不受 Cache fail-open 策略影响。
- Session 数据库写、撤销 Redis 副作用与 Token 签发遵循现有 IAM Security 协议；本 ADR 不引入分布式事务或 Seata。

## 7. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 开发者误把 revoked SID 当 Cache | 架构测试断言 Store 不依赖 mom-cache，ADR 明确 fail-closed |
| IAM/Gateway Key 漂移 | 统一使用 MomRevokedSessionKeys + 共享测试 |
| Redis 故障被 Resilience/Fallback 吞掉 | 不包装 Redis，统一异常向上 Fail Closed |
| 为未来事件提前造表/Topic | 架构测试禁止当前 IamEventType，新增需独立 ADR |

## 8. 验证方式

- `IamRevokedSessionStoreTest`：共享 Key、TTL 与共享不可用异常。
- `RedisMomRevokedSessionCheckerIT`：真实 Redis 已撤销/未撤销和连接失败 Fail Closed。
- `FrameworkGovernanceArchitectureTest`：IAM 无 Caffeine、Store 不依赖 Cache/Resilience、当前无 IamEventType。
- IAM Security/Runtime Security 测试继续验证 503 与受保护 API 不放行。

## 9. 替代与回滚条件

若撤销机制迁移到其他权威 Security Store，必须创建 Security ADR 并证明撤销最大延迟、故障关闭与跨应用一致性。不得回滚到 IAM 私有 Key/异常或普通 CacheService。
