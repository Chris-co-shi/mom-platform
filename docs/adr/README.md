# ADR 索引

ADR 记录决策历史，不等于当前实现。运行时事实先看 [当前实现地图](../当前实现地图.md)；只有任务涉及对应决策时才读取正文。

状态：`Accepted` 当前有效；`Historical` 仅保留背景；`Superseded` 已被后续 ADR 替代。

| ADR | 状态 | 当前用途 |
|---|---|---|
| [001 JDK 25 / Boot 4](ADR-001-jdk25-spring-boot4.md) | Accepted | 技术基线 |
| [002 仓库与模块边界](ADR-002-仓库与模块边界.md) | Accepted | 模块边界 |
| [003 OAuth2/OIDC](ADR-003-OAuth2与OIDC授权模型.md) | Superseded | 认证历史 |
| [004 独立 Schema](ADR-004-PostgreSQL按服务隔离Schema.md) | Accepted | 数据隔离 |
| [005 Outbox/Inbox](ADR-005-Outbox与Inbox消息一致性.md) | Accepted | 消息一致性 |
| [006 库存流水与余额](ADR-006-库存流水余额与预占模型.md) | Accepted | 未来 WMS |
| [007 批次谱系](ADR-007-批次谱系不可变边模型.md) | Accepted | 未来追溯 |
| [008 PCS/WCS 状态机](ADR-008-PCS与WCS命令状态机协议.md) | Accepted | 未来设备协作 |
| [009 Seata 边界](ADR-009-Seata使用边界.md) | Accepted | 一致性边界 |
| [010 开源与许可证](ADR-010-开源复用与许可证规则.md) | Accepted | 依赖治理 |
| [011 Prototype First](ADR-011-prototype-first.md) | Accepted | 产品设计 |
| [012 分布式追踪](ADR-012-distributed-tracing.md) | Accepted | 可观测性 |
| [013 Redis 限流](ADR-013-redis-rate-limit.md) | Accepted | Gateway 限流 |
| [014 单数据源/Hikari](ADR-014-单服务单数据源与Hikari连接池治理.md) | Accepted | 数据源治理 |
| [015 Stream/RocketMQ](ADR-015-SpringCloudStream与RocketMQ消息边界.md) | Accepted | 消息边界 |
| [016 Seata AT PoC](ADR-016-SeataAT受控PoC与故障边界.md) | Accepted | 受控例外 |
| [017 OpenTelemetry](ADR-017-OpenTelemetry追踪与关联边界.md) | Accepted | 追踪边界 |
| [018 监控闭环](ADR-018-PrometheusLokiGrafana可观测性闭环.md) | Accepted | 可观测性 |
| [019 P1.5 认证](ADR-019-P1.5认证与授权闭环.md) | Superseded | 认证历史 |
| [020 物理 Schema](ADR-020-PostgreSQL物理Schema命名空间.md) | Accepted | Schema 命名 |
| [021 配置与 Secret](ADR-021-运行时配置来源与Secret边界.md) | Accepted | 配置安全 |
| [022 测试与 CI](ADR-022-测试分层与CI质量门禁.md) | Accepted | 质量门禁 |
| [023 Locale/时区/偏好](ADR-023-Locale时区与用户偏好边界.md) | Partially superseded | Locale/时区有效，偏好未实现 |
| [024 双认证通道](ADR-024-PC-JSON与Mobile-PKCE-OIDC双通道.md) | Superseded | 认证历史 |
| [025 数据所有权](ADR-025-IAM-System-MDM-WMS-EAM数据所有权边界.md) | Partially superseded | 被 044/045 精化 |
| [026 禁止业务 FK](ADR-026-MOM业务表禁止物理外键与关联完整性策略.md) | Accepted | 数据完整性 |
| [027 服务端分层](ADR-027-服务端包结构与基础设施适配器分层.md) | Partially superseded | Level 2/3 选项 |
| [028 Repository 边界](ADR-028-MyBatis-Plus-Repository抽象与领域仓储边界.md) | Partially superseded | Level 2/3 选项 |
| [029 IAM Admin 分层](ADR-029-IAM-Admin分层与领域模型边界.md) | Historical | 旧 IAM |
| [030 System 导航](ADR-030-System应用目录导航发布与IAM权限引用边界.md) | Superseded | System 历史 |
| [031 System Cache/Event](ADR-031-System运行时缓存变更通知与服务身份事务边界.md) | Superseded | System 历史 |
| [032 Cache Region](ADR-032-Cache-Region与Factory-Scope兼容迁移.md) | Accepted | Cache/Scope |
| [033 Event/Outbox Ownership](ADR-033-Event与Outbox-Ownership.md) | Accepted | 事件所有权 |
| [034 Resilience Profile](ADR-034-Resilience-Profile与事务边界.md) | Accepted | 故障边界 |
| [035 Framework Freeze](ADR-035-Framework-Freeze与平台适应度函数.md) | Accepted | 框架治理 |
| [036 IAM Cache/Event](ADR-036-IAM安全状态Cache与Event边界.md) | Historical | 与 040 冲突处失效 |
| [037 System Cache/Event](ADR-037-System消费Cache与Event-Framework.md) | Superseded | System 历史 |
| [038 配置元数据](ADR-038-Configuration-Metadata-Governance.md) | Accepted | 配置治理 |
| [039 工程治理](ADR-039-MOM-Platform-Engineering-Governance.md) | Accepted | 工程治理 |
| [040 Mini Auth](ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) | Accepted | 当前认证运行时 |
| [041 Mini Auth 三层](ADR-041-Mini-Auth简化三层包结构.md) | Superseded | 包结构历史 |
| [042 渐进式分层](ADR-042-MOM渐进式分层与对象模型.md) | Accepted | 当前分层入口 |
| [043 System V1](ADR-043-System-V1能力与I18n数据所有权.md) | Accepted | 当前 System 边界 |
| [044 MDM/WMS/MES 静态边界](ADR-044-MDM静态主数据与WMS-MES运行时所有权边界.md) | Accepted | 静态/运行时边界 |
| [045 MDM/QMS 质量边界](ADR-045-MDM质量基础定义与QMS运行时边界.md) | Accepted | 质量定义所有权 |

新决策使用 [ADR 模板](ADR-模板.md)。已接受 ADR 不改写历史结论；变化通过新 ADR 和状态更新表达。
