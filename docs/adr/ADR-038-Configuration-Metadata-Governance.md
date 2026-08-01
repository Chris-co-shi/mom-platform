# ADR-038：Configuration Metadata Governance

- 状态：Accepted
- 日期：2026-08-01
- 关联需求：P1.6 Framework Governance Phase 7
- 关联决策：[ADR-032](ADR-032-Cache-Region与Factory-Scope兼容迁移.md)、[ADR-034](ADR-034-Resilience-Profile与事务边界.md)、[ADR-037](ADR-037-System消费Cache与Event-Framework.md)

## 1. 背景

运行时配置分布在 Framework 与业务应用的 `application.yml`、ConfigurationProperties 和环境变量中。缺少机器可读的 owner、失败语义与变更影响时，部署差异和 AI Agent 审查只能依赖全文搜索，也容易把 Resilience 当前建议值误当成冻结契约。

## 2. 决策

- 根目录 `config-metadata.yml` 是治理索引，不参与 Spring Boot 运行时绑定；
- 每个已治理模块维护自己的 `src/main/resources/config-metadata.yml`；
- 每个 Property 至少记录 key、环境变量、类型、当前默认值、required/requiredWhen、sensitive、owner、failureMode、restartRequired 和 changeImpact；
- Cache 配置额外记录 Global/Factory Scope；System 当前运行时 Projection 全部标记 Global；
- Resilience 配置记录命名 Profile、覆盖优先级和环境变量 Pattern，明确 `overridable=true`、`valuesFrozen=false`；
- Metadata 由静态架构测试验证，不在业务启动时增加解析器或生产依赖。

## 3. 抽象成立依据

- Cache、Resilience 与 System 三个真实模块已经存在环境差异和部署配置，根索引解决当前治理问题；
- Metadata 是平台基础能力，失败语义是 CI fail-fast、运行时无副作用；
- 不创建 Metadata Registry、动态配置 Provider 或发布服务；YAML + 静态测试是满足当前需求的最简单实现；
- 当模块不再包含受治理配置时可从根索引移除，不保留空 Metadata 文件。

## 4. 敏感信息与失败语义

- Metadata 只能记录环境变量名称和敏感标志，不保存 Secret 值；
- 默认值必须与版本库配置一致，生产 Secret 仍由部署环境提供；
- Metadata 缺字段或索引指向不存在文件时 CI 失败，但不得影响生产应用启动；
- Resilience 建议值可以被业务实例覆盖，架构测试禁止冻结具体窗口、阈值、超时和并发数。

## 5. 验证与演进

`ConfigurationMetadataGovernanceTest` 验证根索引、模块文件、Property 必填字段和 Resilience 非冻结语义。新增已治理模块时先增加模块 Metadata，再注册根索引；新增配置必须在同一 Slice 更新 Metadata。Schema 发生不兼容变化时递增根 `schemaVersion` 并增加迁移说明。
