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
