# 模块边界

## 依赖方向

```text
mom-core
  ↑
framework capabilities
  ↑
domain api / client / server
  ↑
gateway and bootstrap tests
```

## 强制规则

1. `*-api` 只包含跨模块契约，不暴露数据库 Entity、Mapper 或 Repository。
2. `*-client` 只依赖对应 `*-api` 与调用基础设施。
3. `*-server` 实现领域能力，禁止依赖其他领域的 `*-server`。
4. 跨领域同步调用通过 `*-client`，异步协作通过领域事件。
5. PostgreSQL 每服务独立 Schema，禁止跨 Schema JOIN 和跨域写入。
6. `mom-framework` 不得包含 MES、WMS、QMS 等业务规则。
7. PCS、WCS 独立仓库，不进入本 Reactor。

## System Platform

`mom-system-platform` 自 P1.6 S12 起登记为 `api/client/server` 三模块。S12 仅建立技术骨架：

- API 为空契约边界；
- Client 仅依赖自身 API 与 `mom-openfeign`，没有真实 Feign 接口；
- Server 仅依赖自身 API、公共 WebMVC/Tracing/Metrics、Nacos Discovery 与测试基础设施；
- Server 不依赖 IAM Server、其他领域 Server、Data、Flyway、数据库、Redis、MQ、Outbox 或 Seata；
- System 包不得访问 IAM Application、Web、Repository、Mapper、Infrastructure 或 Schema；
- 当前没有参数、字典、Preference、Application Catalog、Menu、Dynamic I18n 或 Audit Projection。

POM XML 语义门禁与 ArchUnit 规则位于 `mom-architecture-tests`。ADR-025 仍是数据所有权的权威决策。
