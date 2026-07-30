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

`mom-system-platform` 自 P1.6 S12 起登记为 `api/client/server` 三模块；S13 增加类型化非敏感参数，S14 增加非权威受限字典，S15-B 增加 Dynamic I18n 后端：

- API 只暴露 Parameter Scope/Value Type/有效值，以及 Dictionary Active Option/Compatibility 只读契约；
- Client 仅依赖自身 API 与 `mom-openfeign`，没有真实 Feign 接口；
- Server 精确依赖自身 API、WebMVC、Data、Security、Tracing/Metrics、Nacos Discovery 与测试基础设施；
- PostgreSQL `mom_system` 中 V1 Parameter、V2 Dictionary 两表与 V3 I18n 三表是各自唯一权威；FK 只允许同 Schema Restrict，禁止跨 Schema FK/JOIN；
- Dictionary 只承载低频、无独立生命周期的稳定 Code 与 fallback Label，不得复制 IAM/MDM/WMS/EAM 权威对象；
- Dynamic I18n 使用 Draft → 显式 Publish → 双 Locale 不可变 Release；Runtime 只读当前完整版本并支持 ETag/304，回滚创建新版本；
- Security 传递的 Redis 只检查 revoked sid，不是 System 业务存储或缓存；Server 不依赖 IAM Server、其他领域 Server、MQ、Outbox 或 Seata；
- System 包不得访问 IAM Application、Web、Repository、Mapper、Infrastructure 或 Schema；
- 当前有 GLOBAL/APPLICATION 参数、受限 Dictionary/Item 和 Dynamic I18n；没有 Preference、Application Catalog、Menu、Audit Projection、Tree、Metadata 或 Alias。

POM XML 语义门禁与 ArchUnit 规则位于 `mom-architecture-tests`。ADR-025 仍是数据所有权的权威决策。
