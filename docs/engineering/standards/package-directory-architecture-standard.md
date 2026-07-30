# MOM 服务端 Package 与目录架构规范

- 状态：Accepted
- 生效范围：正式 bounded context 的 `mom-*-server/src/main/java`
- 决策关联：[ADR-027](../../adr/ADR-027-服务端包结构与基础设施适配器分层.md)

## 1. 标准结构与依赖方向

正式 Server 使用混合 Package 组织：Web、Application、Domain 按业务能力或用例分包；Infrastructure 按
外部 Adapter 类型分包。允许的顶层职责是 `web`、`application`、`domain`、`infrastructure` 和
`configuration`，只在存在真实职责时创建。

标准依赖方向为 `Web → Application → Domain Port ← Infrastructure Adapter`；Configuration 只装配。
Domain 不依赖 Infrastructure，Application/Web 不直接依赖 Mapper，持久化实现不依赖 Web。禁止空目录、
占位类型、无职责 `package-info.java` 和为了形式统一新增的抽象层。

## 2. Web、Application 与 Domain

三层以业务语义为第一维度，例如 `web.dictionary`、`application.parameter`、`domain.i18n`。不得建立全局
`web.controller/request/response`、`application.command/query/service`、`domain.entity/repository/service`
替代业务能力。既有业务包只有在 Infrastructure 移动引发必要引用修复时才修改，本规范不授权业务重构。

## 3. Infrastructure Adapter-first

`infrastructure` 第一层只允许真实存在的 `persistence`、`client`、`messaging`、`cache`、`storage`。
禁止 `infrastructure.parameter/dictionary/i18n/user/role/admin` 等业务 Feature。无法归类的新 Adapter 必须先
完成精确设计审查，说明外部技术、依赖方向和失败策略，不得以 `common`、`support` 或 `impl` 逃避分类。

远程调用进入 `infrastructure.client`；MQ、Outbox 发布和事件序列化进入 `infrastructure.messaging`；Redis/
Caffeine 进入 `infrastructure.cache`；文件、对象和 Blob 进入 `infrastructure.storage`。Persistence Repository
不得隐藏远程调用，Messaging/Cache/Storage 也不得直接成为 Web DTO 来源。

## 4. Persistence 职责包

### 4.1 Entity

`@TableName`、正式数据库行模型和被 `MomBaseMapper<T>` 使用的类型必须位于
`infrastructure.persistence.entity`。名称在 bounded context 内唯一并包含明确语义，例如
`SystemDictionaryItemEntity`；禁止 `ItemEntity`、`RecordEntity`、`DataEntity`、`ConfigEntity` 等宽泛名称。
Entity 不进入 Web/API，不依赖 Application/Web，且按数据能力选择最小基类。

### 4.2 Mapper

普通 MyBatis/MyBatis-Plus Mapper 位于 `infrastructure.persistence.mapper`，默认继承 `MomBaseMapper`，
负责 Entity 写模型的单表访问。它不承载业务事务、不返回 Web DTO、不跨 Schema、不依赖 Application/Web。
存在一条自定义 SQL 不会把普通 Mapper 变成 Query Mapper。

### 4.3 Repository Adapter

Domain Repository Port 的 MyBatis/PostgreSQL 实现位于 `infrastructure.persistence.repository`，名称使用
`Mybatis<Context><Capability>Repository` 或同等清晰形式。Adapter 编排 Mapper、隐藏 Wrapper/Page/Entity、
完成 Domain 转换并映射底层异常；不得使用 `RepositoryImpl`、`BaseRepository`、`CommonRepository`、
`DefaultRepository` 或通用泛型 Repository。简单转换保留为私有方法。

### 4.4 Query

多表列表、报表和专用读取投影位于 `infrastructure.persistence.query`，可包含 `XxxQueryMapper`、
`XxxListRow`、`XxxDetailRow`、`XxxProjection`。Query Mapper 返回专用 Row/Projection，不把持久化 Entity
直接返回 Web，并遵守 S15-D JOIN、分页、排序、上限、索引和 PostgreSQL 测试规则。普通 CRUD 不因查询 XML
存在而迁回 XML。

### 4.5 Converter 与 TypeHandler

只有多处复用且非平凡的 Entity/Domain 转换才创建 `persistence.converter`；禁止一表一 Converter、反射转换器
和万能 Bean Copy。业务专用 TypeHandler 进入 `persistence.typehandler`；通用 PostgreSQL/JSONB TypeHandler
保留在 `mom-framework/mom-data`，不得在各 context 复制。

`persistence` 下只允许 `entity`、`mapper`、`repository`、`query`、`converter`、`typehandler` 六类职责包，
禁止再以 Parameter、Dictionary、I18n、User、Role、Admin 等 Feature 建立子包。

## 5. Configuration

bounded context 的 Spring 装配、Properties、MyBatis/Security/Runtime 配置优先位于顶层 `configuration`，不放
`infrastructure.configuration`。配置只创建和连接 Bean，不实现业务用例或持久化逻辑。Framework
AutoConfiguration 按 Framework 角色治理，不强制迁移；SAS 官方协议配置可精确登记，但不能授权新业务复制。

## 6. 命名、可见性与引用

文件路径必须与 `package` 完全一致，仓库不得存在重复 FQCN。移动后优先保持原可见性；只有 Spring/MyBatis、
跨包 Port 或测试编译证明需要时才扩大，并在审计报告解释。不得新增旧 Package 代理类或复制文件保留双结构。

移动必须同步 Java import、FQCN、Spring Bean/Import、`@MapperScan`、Component Scan、反射/序列化字符串、
测试 Package、ArchUnit 和文档。重名使用清晰 context/业务前缀解决，不重新创建 Feature 子目录。

## 7. Mapper XML

XML 默认保留 `src/main/resources/mapper/<context>/`，不为了视觉一致性修改加载机制。文件名必须与 Mapper
接口一致；`namespace` 指向真实 Mapper/QueryMapper；`resultType`、`resultMap` 和 `typeHandler` 中的 FQCN
同步更新；Query XML 使用 `*QueryMapper.xml`。禁止重复 Namespace、孤立 XML，以及普通 Mapper/Query Mapper
职责混淆。只有既有递归扫描且确实提升清晰度时才评估资源子目录。

## 8. 测试 Package 与例外治理

测试可按被测业务能力组织，但路径与 Package 必须一致；package-private 访问测试随生产类型移动或改为公共
契约测试，不得为测试方便无条件扩大生产可见性。正式 Package 移动至少验证 test-compile、模块 test/verify、
相关 PostgreSQL IT、Mapper XML 加载和 Spring Bean 数量/名称。

例外必须精确到文件或类型，记录不同架构角色、风险、证据与退出条件。Framework、Gateway、API、Client 不
套用 bounded context Server 模板，但仍审计是否承载正式业务持久化实现。禁止模块级、目录级或通配符排除。

## 9. 新增代码验收

新增或移动 Entity、Mapper、QueryMapper、Row/Projection、Repository、Client/Messaging/Cache/Storage Adapter
和 Configuration 前，必须填写 Package Layout 验收模板。规范文件和目录名称存在不等于验收完成；最终实现
必须通过 Package Layout Baseline、ArchUnit、编译、行为测试与字符串引用扫描。
