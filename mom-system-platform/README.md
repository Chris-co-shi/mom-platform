# MOM System Platform V1

`mom-system-platform` 当前只承担三类能力：受限平台字典、平台支持 Locale、`system.*` 动态文案。当前设计以
[ADR-043](../docs/adr/ADR-043-System-V1能力与I18n数据所有权.md) 和
[System V1 与 I18n 运行时设计](../docs/architecture/System-V1与I18n运行时设计.md) 为准。

## 模块

```text
mom-system-platform
├── mom-system-api       跨模块稳定只读契约
└── mom-system-server    HTTP、Application 与 PostgreSQL 实现
```

V1 不保留 `mom-system-client`，也不提供 Parameter、User Preference、Application Catalog、Navigation、发布
快照、Redis Cache、Outbox/Inbox、RocketMQ 或 IAM Permission Reference。

## 代码结构

```text
io.github.chrisshi.mom.system
├── controller
├── application
└── infrastructure
    ├── entity
    └── mapper
```

调用方向为 `Controller → Application → Infrastructure`。Application 负责事务、引用校验、唯一冲突、状态和
Placeholder 一致性；当前没有建立 Domain、Repository Port/Adapter、Converter 或 Mapper XML。

## 当前表

- `system_dictionary`、`system_dictionary_item`：沿用历史表作为当前字典存储；
- `system_supported_locale`：V10 新增，初始化 `zh-CN` 与 `en-US`，且只有一个默认 Locale；
- `system_i18n_message_definition`：V10 新增，只接受 `system` / `system.*` namespace；
- `system_i18n_translation`：V10 新增，保存纯文本 Translation。

V1～V9 Migration 不修改。旧表仍可存在于 `mom_system`，但当前运行时代码不读写。

## I18n 语义

```text
framework.* → mom-webmvc classpath Resource Bundle
system.*    → mom_system Message / Translation
mdm.* 等    → 未来由对应 bounded context 自己实现和持有
```

回退顺序为“请求 Locale → 默认 Locale → messageKey”。同一 Message 的所有 Translation 必须使用一致的数字
Placeholder 集合。SSE 在事务提交后发送，只提示当前实例客户端重新拉取；不保证跨实例广播和可靠投递。

## 验证

优先使用项目 Maven 包装脚本：

```bash
MAVEN_BIN=/path/to/maven-3.9.9-or-newer/bin/mvn \
  bash scripts/codex-mvn-test.sh -pl mom-framework,mom-system-platform -am test

MAVEN_BIN=/path/to/maven-3.9.9-or-newer/bin/mvn \
  bash scripts/codex-mvn-test.sh -pl mom-system-platform/mom-system-server -am verify

bash .github/scripts/system-postgresql-smoke.sh
```

完整日志写入 `.codex/runtime/logs/`，摘要写入 `.codex/runtime/summaries/`。本机 Maven 必须满足项目要求；当前
基线要求 3.9.9 或更高版本。

## 后续业务模块

MDM、MES、WMS、QMS 的 namespace、表和 Translation 归各自 bounded context 所有。开始实现前阅读
[业务模块 I18n 后续设计手册](../docs/engineering/System-I18n业务模块后续设计手册.md)，并为每个真实 Slice 单独
确认数据所有权、失败语义、权限和测试证据。
