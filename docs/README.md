# MOM Platform 文档导航

先读源码和 [当前实现地图](当前实现地图.md)。本文只负责按任务定位文档，不复制业务事实。

## 默认预读链

1. 根目录 `AGENTS.md`；
2. 下表命中的一个入口；
3. 必要时再读一个当前 ADR。

普通任务不要批量读取 `docs/`、全部 ADR 或全部工程标准。

| 我要修改 | 必读入口 | 按需补充 |
|---|---|---|
| 当前模块/API/Schema/权限 | [当前实现地图](当前实现地图.md) | 相关源码、Migration、测试 |
| Auth/Gateway/Token | [安全协议运行时规范](engineering/standards/security-protocol-runtime-standard.md) | [ADR-040](adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md) |
| 分层、包结构、跨模块契约 | [模块分层规范](engineering/standards/module-layering-standard.md) | [ADR-042](adr/ADR-042-MOM渐进式分层与对象模型.md) |
| CRUD/分页/持久化 | [CRUD 规范](engineering/standards/crud-application-standard.md) | [持久化规范](engineering/standards/persistence-data-modeling-standard.md) |
| 表、索引、Flyway | [数据库表设计规范](engineering/standards/database-schema-design-standard.md) | 对应 Migration |
| 事务、消息、幂等 | [事务一致性规范](engineering/standards/transaction-consistency-standard.md) | 对应 Accepted ADR |
| 国际化文案/Key/Owner/翻译种子/去重 | [国际化与 Locale 规范](engineering/standards/localization-locale-standard.md) | [Key 与 Owner 边界](architecture/I18n-Key维度与Owner边界设计.md) |
| Locale/时间/数量/UOM | [工程规范索引](engineering/standards/README.md) | 只读命中的单项规范 |
| 测试/CI | [测试策略](engineering/standards/testing-strategy-standard.md) | Maven 或 CI 专项规范 |
| 质量定义所有权 | [ADR-045](adr/ADR-045-MDM质量基础定义与QMS运行时边界.md) | MDM/QMS 当前源码 |
| 产品范围与路线 | [V1 路线图](plans/V1路线图.md) | [垂直切片计划](plans/V1垂直切片计划.md) |
| Framework 公共技术能力 | [真实模块与接入边界](framework/README.md) | 对应源码、专题规范 |
| 平台可复用业务契约 | [平台契约](platform/README.md) | Auth、System、MDM 的 Owner 接口 |
| WMS 内部业务入口 | [WMS 模块文档](modules/wms.md) | WMS 源码和 Slice 验收证据 |

## 文档职责

- `当前实现地图.md`：唯一的稳定实现状态摘要。
- `requirements/`：产品能力与范围，不宣称完成状态。
- `architecture/`：当前整体结构和边界，不记录实施流水账。
- `framework/`：真实 Framework Maven 模块、公开入口、采用程度和限制。
- `platform/`：跨业务模块复用的 Owner 契约；业务内部流程留在 `modules/`。
- `engineering/standards/`：可执行工程规则。
- `adr/`：决策及替代关系；历史 ADR 不代表当前运行时。
- `plans/`：只保留未来路线与尚未完成的垂直切片。

文档维护方式见 [文档维护约定](文档维护约定.md)。
