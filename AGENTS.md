# MOM Platform — AI 开发与工程约束

本文件适用于整个仓库。目标是**按已确认的业务方案增量开发，遵守架构与安全边界，同时减少 Codex 不必要的上下文、工具调用和重复验证**。本文件是任务入口；各领域的详细约束以对应 Current ADR、工程标准和实际代码为准，不在这里重复全文。

## 1. 每个任务的最小执行闭环

1. **确认范围**：读取本次需求、涉及模块的少量现有代码和必要规范；先搜索相似实现。不得默认遍历全部仓库、读取所有 ADR/历史报告或重新设计已确定的架构。
2. **先复用再扩展**：优先 Reuse → Extend → Create；不因“将来可能需要”添加 DDD 层、Port/Adapter、通用 CRUD、状态机、分布式事务、依赖或新模块。
3. **按风险实施**：纯文档/局部修复直接处理；正常 CRUD 检查对应契约和数据约束；新增表、事务、鉴权、并发、跨服务调用、公共框架及基础设施时，先核对完整设计清单与失败策略。出现真正阻塞、数据破坏或安全风险才中断实现并说明。
4. **按范围验证**：优先受影响的测试或模块构建；进入 Slice/Phase 收口、公共依赖修改、发布或用户明确要求时执行完整验证。失败优先读摘要、报告和相关源码，不把整份构建日志输入模型；相同失败命令不做无分析的反复重试。
5. **简洁交付**：报告修改内容、关键调用链/事务和数据去向（适用时）、已执行验证及结果、未解决风险。不得将未运行或被跳过的验证描述为通过。

修改任何文件都需保留用户已有改动，不越界重构，不为通过测试降低业务要求。用户确认的 Decisions 已冻结，只有 Open Questions 或真实冲突需要讨论；业务领域所有权、事务和失败语义不能由 AI 静默改变。

## 2. Skill 与文档加载

- **按任务激活 Skill**：需求模糊或新架构设计时才进入较完整的方案流程；复杂调试、审查或 UI 设计只选相关 Skill。日常已标准化的 CRUD、修错、字段调整不自动执行整套 Superpowers、UI 重设计或跨模块审查；有更高优先级的强制要求时仍须遵守。
- **按主题读权威文件，不按目录批量读取**。先用文件名、符号搜索定位需要的章节；优先真实调用方和现有测试。历史 P1.5/P1.6、计划及验收报告是证据，不自动覆盖当前 ADR 与运行时代码。
- 文档总导航：`docs/README.md`；工程规范索引：`docs/engineering/standards/README.md`；ADR：`docs/adr/README.md`；本地 Codex：`docs/engineering/codex-local-workflow.md`。
- 仅在修改对应模块时读取该模块的局部 `AGENTS.md`；例如 `mom-auth-platform/mom-auth-server/AGENTS.md` 只约束 Auth Server。

| 任务命中范围 | 主要权威文档（按需打开） |
|---|---|
| 新服务、接口、Package 分层 | `docs/adr/ADR-042-MOM渐进式分层与对象模型.md`；`module-layering-standard.md`；`package-directory-architecture-standard.md`；`http-api-contract-standard.md` |
| 普通 CRUD、SQL、Flyway、分页/关联 | `crud-application-standard.md`；`persistence-data-modeling-standard.md`；`database-schema-design-standard.md`；`multi-table-association-query-standard.md` |
| 事务、消息、Seata、幂等、并发 | `transaction-consistency-standard.md`；`audit-concurrency-lifecycle-standard.md`；相关 Accepted ADR |
| 认证、授权、外部 HTTP、Redis | `security-protocol-runtime-standard.md`；`configuration-profile-secret-standard.md`；`outbound-http-client-standard.md`；`redis-key-ttl-failure-standard.md` |
| Locale、时间、数量与单位 | `localization-locale-standard.md`；`timezone-date-time-standard.md`；`number-money-rounding-standard.md`；`measurement-unit-standard.md` |
| 测试、构建、CI | `testing-strategy-standard.md`；`maven-test-lifecycle-standard.md`；`ci-scope-quality-gate-standard.md` |
| JDK / Spring / Alibaba 版本调整 | `official-source-policy.md` 及对应版本工程标准，先确认官方兼容性 |

表中省略目录的 `*-standard.md` 文件均在 `docs/engineering/standards/`。**表是路由，不表示一次任务必须读取整行所有文件。**

## 3. 不得突破的工程不变量

- **架构**：`*-api` 只承载跨模块契约，`*-client` 只承载调用契约；Server 不直接依赖其他领域 Server。Gateway 保持 WebFlux，PCS/WCS 保留独立边界。简单业务优先 `Controller/Web → Application → Infrastructure`，Level 1 可由 Application 直接使用本 bounded context 的 Mapper/Entity；Auth 当前实现与 ADR 精确例外优先。不为目录对齐机械生成 DO/PO/BO/VO、Repository Port 或空 Domain。
- **Java 注释**：新增或实质修改的公开类型/公共方法使用准确的**中文 Javadoc**说明职责、边界和非显然的事务、并发、失败或副作用；关键私有逻辑解释设计原因。简单访问器不堆砌注释，禁止复制模板、逐行翻译或伪称已实现。
- **数据与迁移**：PostgreSQL 的服务 Schema 与数据所有权必须清楚；禁止业务跨 Schema JOIN/外键/级联，禁止修改已发布 Flyway Migration。主键 Java 使用 `String`，数据库按现行标准为 `varchar(19)`；Snowflake ID 不作为 JSON Number。依类型选择 Entity 基类，不强迫关系/流水/Outbox 表继承完整基类；审计、版本、Affected Rows 必须正确处理。普通服务单权威 DataSource、本地事务优先，涉及其他数据源须明确例外。
- **CRUD/持久化**：Controller 不直达 Mapper；事务通常位于 Application；MyBatis-Plus 能覆盖的操作不手写重复 SQL。新增/修改表、关系或复杂查询前必须明确数据所有权、生命周期、删除/禁用策略、关系完整性、分页、索引、事务与并发、迁移和测试证据；高风险任务使用 `docs/engineering/templates/` 下的对应设计模板。不要求每个小修改机械输出完整的 13 项清单。
- **安全与配置**：Base 配置不含生产 Secret，生产环境关键安全配置 Fail Fast；Gateway 不是唯一认证点，业务 Resource Server 必须校验身份和权限。认证 V1 使用当前 ADR-040 的 Redis Opaque Token；不恢复已弃用 JWT/JWK 流程。Nacos Config 不承担 Secret Manager，2025.1.x 依赖 `spring.config.import`；Feign 超时有限，写请求默认不自动重试；敏感数据、Token 和密钥不得进入日志、Trace 或 MQ Payload。
- **一致性和基础设施**：业务写/Outbox 与消费写/Inbox 分别使用同一本地事务；不可把 RocketMQ ACK 当作业务完成，也不能让 Seata AT 覆盖不可逆外部副作用。Seata 默认关闭，使用前按相应 ADR 和真实环境验证；幂等键与 Redis 临时状态须有 TTL、原子语义和明确的故障策略。Trace 使用 W3C 上下文与稳定业务关联 ID；Prometheus 标签限制高基数和敏感信息。
- **时间与精度**：技术时间使用 `Instant` / UTC / `timestamptz`；跨服务 Locale 使用 BCP 47；工厂业务日期以权威 IANA Zone ID 确定；金额、比例和精度敏感数量使用 BigDecimal/Decimal String。权限与偏好、主数据归属不得混淆。
- **可验证性**：高风险变更验证正常与失败路径；真实 Broker、Redis、数据库或 TC 兼容性结论不能仅凭编译或内存替身。CI 门禁及条件 Smoke 不得人为跳过并称成功；新增依赖遵守许可证与来源记录。

## 4. 本地 Codex 验证约定

- 环境首次搭建、JDK/Maven/Docker 状态变化或异常时执行：`bash scripts/codex-doctor.sh`。
- 后端改动优先使用：`bash scripts/codex-verify-changed.sh`（默认 `test`；局部可用 `CODEX_VERIFY_LEVEL=compile`）；专门的 Maven 命令使用 `bash scripts/codex-mvn-test.sh` 包装。
- 包装脚本保留完整日志至 `.codex/runtime/logs/`，Codex 仅先读取 `.codex/runtime/summaries/` 的有界诊断。不要重复运行等价命令只为收集更多输出。
- 纯文档任务先检查 Diff、链接和约束一致性，不必启动 Maven、Docker 和中间件。共享 API/框架、全局配置、Schema、认证及发布等影响面扩大时，按权威规范升级验证，不得以节省额度为由跳过必要检查。

## 5. 实现与学习模式分离

AI 是实施者和批判性审阅者，不替用户决定制造业核心业务模型和系统演进。普通开发只给简洁 Implementation Review；**Design Pressure Test 与 Interview Mode 不默认在每次任务后运行**。用户明确请求学习/面试训练，或重要 Slice 已收口且双方进入复盘阶段时再进行压力测试、Why/Boundary/Flow/Failure/Scale/Trade-off 提问。发现设计缺陷时准确区分当前必须修复、合理延期和过度设计，不为展示能力无故扩大实现。
