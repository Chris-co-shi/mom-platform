# MOM 服务端 Package 与目录架构规范

- 状态：Accepted
- 生效范围：正式 bounded context 的 `mom-*-server/src/main/java`
- 决策关联：[ADR-027](../../adr/ADR-027-服务端包结构与基础设施适配器分层.md)
- Mini Auth 精确例外：[ADR-041](../../adr/ADR-041-Mini-Auth简化三层包结构.md)

## 1. 标准结构与依赖方向

正式 Server 默认使用混合 Package 组织：Web、Application、Domain 按业务能力或用例分包；Infrastructure 按外部 Adapter 类型分包。默认依赖方向为 `Web → Application → Domain Port ← Infrastructure Adapter`。

> `mom-auth-platform/mom-auth-server` 的 Mini Auth V1 不套用上述默认顶层结构，按 ADR-041 使用 `controller → service → infrastructure`。这是精确模块例外，不自动扩散到其他 bounded context。

## 2. 默认 Web、Application 与 Domain

默认三层以业务语义为第一维度，例如 `web.dictionary`、`application.parameter`、`domain.i18n`。不得建立全局 `web.controller/request/response`、`application.command/query/service`、`domain.entity/repository/service` 替代业务能力。

Mini Auth V1 例外：

```text
io.github.chrisshi.mom.auth
├── controller
├── service
└── infrastructure
```

其中 `service` 直接承担业务用例与聚合职责，不再创建 `application` 和 `domain` 顶层包。

## 3. Infrastructure

默认复杂 bounded context 的 `infrastructure` 继续按 `persistence`、`client`、`messaging`、`cache`、`storage` 等 Adapter 类型组织。

Mini Auth V1 为保持第一版直观，允许：

```text
infrastructure
├── entity
└── mapper
```

仅在真实需要时再增加 `repository`、`query`、`configuration` 等技术子包，不创建空目录。

## 4. Persistence 职责

### 4.1 Entity

默认正式数据库行模型位于 `infrastructure.persistence.entity`。Mini Auth V1 例外位于 `infrastructure.entity`。

无论哪种结构，Entity 不得直接作为 Web/API DTO。

### 4.2 Mapper

默认 Mapper 位于 `infrastructure.persistence.mapper`。Mini Auth V1 例外位于 `infrastructure.mapper`。

Mapper 只负责数据库访问，不承载业务事务、不返回 Controller DTO、不跨 Schema。

### 4.3 Repository

默认复杂 bounded context 可使用 Repository Adapter 隐藏 ORM 细节并实现 Domain Repository Port。

Mini Auth V1 不默认创建 Repository Port 或一对一 Repository Adapter。只有出现真实替换边界、复杂多表持久化或 ORM 细节开始污染 Service 时再引入。

### 4.4 Query / Converter / TypeHandler

只有真实查询复杂度或多处复用转换出现时才增加对应包；禁止一表一 Converter、万能 Bean Copy 或占位 Query。

通用 PostgreSQL/JSONB TypeHandler 继续保留在 `mom-framework/mom-data`。

## 5. Configuration

默认 bounded context 的应用级 Spring 装配可位于顶层 `configuration`。

Mini Auth V1 若只出现服务自身 Infrastructure 配置，可先放 `infrastructure.configuration`；不为了目录形式提前增加第四个顶层包。

## 6. 命名、可见性与引用

文件路径必须与 `package` 完全一致，仓库不得存在重复 FQCN。移动必须同步 Java import、FQCN、Spring Bean/Import、`@MapperScan`、Component Scan、反射/序列化字符串、测试 Package、ArchUnit 和文档。

不得新增旧 Package 代理类或复制文件保留双结构。

## 7. Mapper XML

XML 默认保留 `src/main/resources/mapper/<context>/`。Mini Auth V1 若普通 MyBatis-Plus CRUD 不需要 XML，则不创建 XML 占位文件。

## 8. 测试 Package 与例外治理

正式 Package 移动至少验证 test-compile、模块 test/verify、相关 PostgreSQL IT、Mapper 加载和 Spring Bean 数量/名称。

当前 Mini Auth V1 的例外由 ADR-041 精确到 `mom-auth-platform/mom-auth-server`；其他模块不得直接复制该例外。

## 9. Mini Auth 新增代码验收

- 顶层业务包只出现 `controller`、`service`、`infrastructure`；
- Controller 不直接依赖 Mapper/Entity；
- Infrastructure 不反向依赖 Controller；
- 不使用 MyBatis-Plus `IService/ServiceImpl` 作为业务 Service；
- 不创建只为形式满足 DIP 的空接口或一对一代理；
- 业务事务、引用校验和关系编排可以从 Service 清晰追踪。

## 10. Mini Auth 例外退出条件

出现以下情况时，需要重新评估 ADR-041，而不是在三层结构中无序堆叠：

- Auth 出现多个可替换持久化实现；
- Service 中出现大量 ORM Wrapper/Page/Entity 泄漏；
- 数据库、远程服务、消息等多种 Infrastructure 同时参与一个能力；
- 单元测试难以隔离不可控外部依赖；
- User/Role/Permission 之外出现明显独立聚合和复杂生命周期。

在这些条件出现前，Mini Auth 保持简化三层，不提前恢复旧 IAM 的复杂分层。
