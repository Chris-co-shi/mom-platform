# MOM Platform — Codex 入口

本文件只负责全局边界和任务路由。当前实现范围见 `docs/当前实现地图.md`；不要从历史 ADR、计划或报告推断运行时事实。

## 工作闭环

1. 先读本文件、相关源码和下表中最多两份专题文档；先搜索相似实现。
2. 修改前说明目标、改动范围、不改内容和验证命令；保留用户已有改动。
3. 遵循 Reuse → Extend → Create；不主动增加依赖、模块、基础设施或通用框架。
4. 代码与文档冲突时先判断实现偏离还是决策变化；不得静默改变领域所有权、事务、失败语义或安全边界。
5. 只运行与影响面匹配的验证；未运行或被跳过的验证不得描述为通过。

用户拥有制造业业务模型、领域/服务边界、数据归属、聚合、状态机、事务、一致性和 API 语义的最终决定。发现问题先标记 `DESIGN ISSUE`，说明影响和当前方案是否仍可实现；不得以未来扩展为由主动引入 CQRS、Saga、Outbox、工作流、通用状态机、新微服务或新基础设施。

## 任务路由

| 任务 | 先读 |
|---|---|
| 当前模块、接口、Schema、权限 | `docs/当前实现地图.md` |
| 新服务、接口或分层 | `docs/adr/ADR-042-MOM渐进式分层与对象模型.md`；`docs/engineering/standards/module-layering-standard.md` |
| CRUD、分页、关联、Flyway | `docs/engineering/standards/crud-application-standard.md`；按需再读持久化或表设计规范 |
| 事务、消息、幂等、并发 | `docs/engineering/standards/transaction-consistency-standard.md`；按需再读审计并发规范 |
| 认证、授权、Gateway、Redis、外部 HTTP | `docs/engineering/standards/security-protocol-runtime-standard.md`；按需再读对应专项规范 |
| 国际化文案、I18n Key/Owner、翻译种子或去重 | [国际化与 Locale 工程规范](docs/engineering/standards/localization-locale-standard.md)；新增或生成国际化内容前必须按其中的新增流程检查复用 |
| Locale、时间、数量、单位 | 只读 `docs/engineering/standards/` 中命中的单项规范 |
| 测试、构建、CI | `docs/engineering/standards/testing-strategy-standard.md`；按需再读 Maven 或 CI 规范 |

文档导航见 `docs/README.md`。表中未命中的文档不默认读取。

## 不变量

- 调用方向默认 `Controller/Web → Application → Infrastructure`；`Result<T>` 只属于 HTTP 边界。
- `*-api` 承载跨模块契约，`*-client` 承载调用契约；Server 不直接依赖其他领域 Server。
- PostgreSQL 结构变更只通过 Flyway；禁止修改已发布 Migration、跨 Schema 业务 JOIN/外键/级联。
- 主键 Java 使用 `String`、数据库使用 `varchar(19)`；技术时间使用 UTC/`Instant`/`timestamptz`；精度敏感数量使用 `BigDecimal`/Decimal String。
- Gateway 不是最终认证边界；业务 Resource Server 必须验证 Redis Opaque Token 和权限。敏感信息不得进入日志、Trace 或 MQ Payload。
- 业务写与 Outbox、消费写与 Inbox 分别使用同一本地事务；Seata 默认关闭，写请求默认不自动重试。
- 新增或实质修改的公开 Java 类型和公共方法使用准确的中文 Javadoc；不写模板化注释。
- 不为通过测试降低业务规则、删除测试、放宽断言或绕过校验。

## 验证与交付

- 后端改动优先：`bash scripts/codex-verify-changed.sh`；专门 Maven 命令使用 `bash scripts/codex-mvn-test.sh`。
- 纯文档改动：检查 Diff、Markdown 相对链接、陈旧事实与工程基线，不运行 Maven/Docker。
- 交付时列出修改/新增/删除文件、核心行为、验证命令与结果、未完成事项和风险。
- 只有重要 Slice 收口或用户明确要求时才执行 Design Pressure Test 与 Interview Mode。
