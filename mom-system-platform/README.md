# MOM System Platform

`mom-system-platform` 在 P1.6 S12 中只提供 System Platform 技术骨架与依赖门禁。技术骨架与依赖门禁已完成，业务能力尚未开始。

## 模块职责

| 模块 | 当前职责 | S12 内容 |
|---|---|---|
| `mom-system-api` | 未来跨模块稳定契约 | 只有 `package-info.java`，无 DTO、枚举或 Spring Bean |
| `mom-system-client` | 未来同步调用 Adapter | 只有包边界；依赖自身 API 与 `mom-openfeign`，无 Feign 接口 |
| `mom-system-server` | System 独立运行时宿主 | 最小启动类、环境中立配置、分层包和轻量启动测试 |

依赖方向固定为：

```text
caller → mom-system-client → mom-system-api
mom-system-server → mom-system-api
web → application → domain
infrastructure → domain/application ports
```

## Server 允许依赖

- `mom-system-api`：System 自身契约边界；
- `mom-webmvc`：统一 Servlet/Spring Boot 运行宿主；
- `mom-tracing`、`mom-metrics`：现有服务公共可观测性基线；
- Nacos Discovery Starter：统一服务发现能力，默认关闭且空骨架不依赖 Nacos 可用；
- `mom-test`：只在 test scope 提供测试与 ArchUnit 能力。

`mom-security` 在 S12 未引入：当前 Framework 安全模块会传递引入 Redis，而空骨架没有业务端点，S12 又明确禁止 Redis。真实业务 API 出现后必须在对应 Slice 同时设计安全与 Redis 失败边界，不能在空骨架中预埋。

## 禁止依赖与 ADR-025 边界

- 不依赖 `mom-iam-server` 或任何其他领域 `*-server`；
- 不访问 IAM Application、Web、Repository、Mapper、Entity、Infrastructure 或 `mom_iam` Schema；
- 不保存或定义 Role、Permission、Factory Scope、Party Binding、Credential、Session、Refresh Token、revoked sid、OAuth Client Secret 或其他 Secret；
- 不引入 Data、MyBatis、JPA、JDBC、数据库驱动、Flyway、Redis、MQ、Outbox、Inbox、Seata；
- 不创建跨 Schema FK/JOIN，也不共享数据库读写；
- API 不暴露 Entity、Mapper、Repository、Controller 或自动配置实现；
- Client 不包含业务 Feign 接口，也不伪造本地成功。

以上规则由 `mom-architecture-tests` 的 XML 语义测试和 ArchUnit 规则自动验证，并包含 `mom-iam-server` 违规依赖负例 Fixture。

## 当前未实现

S12 没有参数、字典、用户偏好、视图设置、Application Catalog、Menu、Navigation、Dynamic I18n、Audit Projection 或业务 API；没有 Entity、Mapper、Repository、Schema、SQL 或 Flyway。

后续职责仍按独立 Slice 执行：

- S13：GLOBAL/APPLICATION 类型化参数；
- S14：非权威通用字典；
- S15：仅在存在真实调用方时实现动态国际化；
- S16：用户偏好与视图设置；
- S17：应用目录、菜单、导航与 IAM Permission Code 引用。

S12 完成不授权进入上述 Slice。

## 本地验证

需要 JDK 25 与 Maven 3.9.9 或更高版本：

```bash
bash scripts/codex-mvn-test.sh \
  -pl mom-system-platform/mom-system-server,mom-architecture-tests \
  -am test

bash scripts/codex-mvn-test.sh \
  -pl mom-system-platform \
  -am clean verify

BASE_REF=8b079eafaac52848bfaae68305d3bd4818b612fb \
  bash scripts/codex-verify-changed.sh

bash scripts/codex-mvn-test.sh clean verify
```

## 回滚

S12 没有数据或运行时迁移。需要整体撤销时，在独立 Review 后对集中提交执行普通 `git revert`，同时回退根 Reactor 注册、三模块、架构门禁和阶段文档；不得 reset、rebase 或 force-push 改写长期分支历史。
