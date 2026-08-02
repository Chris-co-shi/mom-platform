# ADR-030：System 应用目录、导航发布与 IAM 权限引用边界

- 状态：Accepted
- 日期：2026-08-01
- 决策人：Chris
- 关联 Slice：P1.6 S17
- 关联 ADR：ADR-025、ADR-026、ADR-027、ADR-028、ADR-029

## 1. 背景

MOM Web 和 Mobile 需要稳定的应用入口、导航元数据、国际化引用和权限可见性，但这些能力不能导致：

- System 复制 IAM Permission、Role 或授权关系；
- 服务端下发 Vue/uni-app 可执行组件、动态 import 或脚本；
- 编辑中的部分导航树直接暴露给 Runtime；
- Catalog 缓存成为新的权威数据源；
- 通过物理外键或跨 Schema JOIN 把 System 与 IAM、客户端实现耦合。

S17 采用两阶段人工评审工作流：先审计规范和真实代码、输出表结构/API/Package/权限/测试方案，经 Chris Review 后再实施。

## 2. 决策

### 2.1 所有权

System Own：

- Application Catalog；
- Navigation Draft；
- 层级、排序、展示、启停；
- `routeKey`、`iconKey`、Dynamic I18n Reference；
- IAM `permissionCode` Reference；
- 不可变发布快照、版本和回滚历史。

IAM Own：

- Permission 定义、状态和分配；
- Role、User-Role、Role-Permission；
- JWT Claims 与 Authority；
- OAuth Client、Session、Factory/Party Scope。

客户端 Own：

- Vue/uni-app Route；
- Component Import、Layout 和页面实现；
- 静态 Route/Icon Registry；
- Route Guard；
- 未知 `routeKey` 的安全失败与静态 fallback。

核心边界：

```text
System 返回受控 Metadata
客户端拥有可执行实现
```

### 2.2 Application 与 OAuth Client

System Application 使用独立、稳定且创建后不可修改的 `applicationCode`。

```text
System Application != IAM OAuth Client
```

S17 不复制 Client Secret、Redirect URI、Grant Type、Token TTL 或 OAuth 安全配置，也不建立 IAM FK 或跨 Schema JOIN。

### 2.3 数据模型

System V8 创建：

- `system_application`；
- `system_navigation_item`；
- `system_catalog_release`。

全部使用 String 技术主键、统一审计、乐观版本和 PostgreSQL Check/Unique/Index；遵守 ADR-026，不建立任何物理外键。

Application 层负责：

- Application 与 Navigation 引用完整性；
- Parent 同 Application、同 Channel；
- 自引用、孤儿和循环检测；
- 节点数量、深度和排序约束；
- 发布前全树校验。

### 2.4 Draft、Publish 与 Rollback

采用：

```text
Draft CRUD
-> 完整树校验
-> 原子 Publish
-> Immutable Snapshot
-> Runtime 只读发布版本
```

`system_catalog_release` 是完整不可变 JSONB 快照，数据库 Trigger 拒绝 UPDATE/DELETE。

Publish：

- 使用 Application 聚合 Version 作为并发边界；
- 校验 Application、Navigation、Route、Dynamic I18n Reference；
- 生成确定性 JSON 和 SHA-256；
- 拒绝 No-op Publish；
- 创建单调递增 Release Version；
- 在单 PostgreSQL 本地事务中推进发布指针。

Rollback 不倒退版本，也不修改历史 Release，而是复制目标快照形成新的单调版本，并记录 `sourceReleaseVersion`。

### 2.5 客户端路由契约

System 只保存和返回稳定 `routeKey`、`iconKey` 与 Route Contract Version。

禁止保存或返回：

- Component Path；
- Layout Class；
- JavaScript、HTML；
-动态 import；
-任意文件路径、包名或远程模块 URL。

客户端通过静态 Registry 将 `routeKey` 映射为 Path、Component 和 Layout。未知 `routeKey` 不得动态加载，应 fail closed、记录脱敏告警并进入受控不可用页或静态 fallback。

### 2.6 Permission Reference

System 只保存 IAM `permissionCode` Reference，不保存 Permission ID、Role、Assignment 或用户授权副本。

IAM JWT 的 Permission Claim 经 `MomJwtGrantedAuthoritiesConverter` 映射时保留原始：

```text
domain:resource:action
```

Runtime 使用：

```text
JWT Authorities
INTERSECT
Navigation permissionCode Reference
```

进行目录可见性过滤。

无 `permissionCode` 的节点表示已认证即可见。受保护节点缺少对应 Authority 时不返回；GROUP 过滤后没有可见子节点时不返回。

菜单隐藏不是业务授权。各业务 API 必须继续由 Resource Server 独立鉴权。

### 2.7 管理权限

IAM V10 注册并赋予内置 `PLATFORM_ADMIN`：

- `system:catalog:read`；
- `system:catalog:write`；
- `system:catalog:publish`。

System 只在 Controller 引用这些 Code，不成为 Permission 权威。

### 2.8 Dynamic I18n

Catalog 保存：

- `i18nResourceCode`；
- `i18nMessageKey`。

Dynamic I18n 继续拥有翻译值。Publish 必须验证引用已发布；Catalog 不建立物理 FK，也不复制 `zh-CN/en-US` 翻译列作为第二权威。

### 2.9 Runtime

Runtime API：

- 只允许已认证用户；
- 只读取当前完整 Release；
- 根据 JWT Authorities 过滤；
- 不返回数据库 ID、Component Path 或可执行内容；
- 使用基于 Release 和 Authorities 的强 ETag；
- 支持 `If-None-Match` / 304；
- Application 禁用作为即时 Kill Switch。

### 2.10 持久化与 Package

遵守 ADR-027、ADR-028：

```text
web.catalog
-> application.catalog
-> domain.catalog
<- infrastructure.persistence.entity/mapper/repository/query
```

普通 CRUD、过滤、分页、计数和固定排序使用 `MomBaseMapper`、MyBatis-Plus Wrapper 与 Repository Adapter。

禁止：

- `IService` / `ServiceImpl`；
- JdbcTemplate / JdbcClient / `java.sql`；
-普通 CRUD XML；
-注解 SQL；
- `${}`、`SELECT *`；
- Controller 直接依赖 Mapper/Entity；
- Application 直接依赖 Mapper/Entity。

S17 没有新增 Catalog Mapper XML。

## 3. 失败语义

- 未认证：401；
- 缺少管理 Permission：403；
- 参数、树结构或未知字段非法：400；
- 不存在或未发布：404；
- Version 冲突、完整性冲突或 No-op：409；
- Runtime 没有可见节点：404 或从聚合目录中省略；
- Snapshot 元数据或 checksum 不一致：Fail Closed，不返回不可信目录。

## 4. Deferred

以下能力不属于 S17 完成范围：

1. **IAM Permission 发布期权威批量校验**：当前没有稳定跨服务批量校验契约。错误或失效 Permission 在 Runtime 因无匹配 Authority 而 Fail Closed，但生命周期对账、禁用通知和发布期验证进入 S18。
2. **Catalog 缓存与变更通知**：进入 S18；PostgreSQL/Release 仍是唯一权威。
3. **mom-web / mom-mobile 正式接入**：客户端 Route Registry、Fallback 和跨仓库 E2E 进入后续独立任务/S19-A。
4. **External URL、动态组件、脚本和远程模块**：V1 禁止，不作为 Deferred 自动开放。

## 5. 后果

正向：

- 编辑和 Runtime 权威分离；
- 发布版本可审计、可回滚；
- Catalog 不扩大授权；
- 客户端执行面保持静态可审计；
- 数据库不形成跨域强耦合；
- 可为 S18 缓存、失效与对账提供稳定版本输入。

代价：

- 管理端需要显式 Publish；
- 客户端必须维护 Route/Icon Registry；
- Permission 生命周期完整闭环需要 S18 的批量验证、对账和通知；
- 客户端正式使用前仍需跨仓库接入。

## 6. 验证

S17 由以下证据约束：

- Flyway V8 三表和 Release Immutable Trigger；
- IAM V10 Catalog Permission Seed；
- Domain Rules、Application Service、Web Security 测试；
- PostgreSQL Testcontainers；
- Architecture Tests；
- System packaged PostgreSQL Smoke：Flyway 8、Catalog 表 3、Catalog JSONB 1、业务/跨 Schema FK 0；
- 最终 Head 六组 GitHub Workflow 全部成功。

## 7. 替代与替代条件

已拒绝：

- Runtime 直接读取 Draft；
- System 下发 Component Path 或动态 import；
- System 复制 IAM Permission/Role；
- Catalog 表建立 IAM 或客户端物理 FK；
- 仅靠菜单隐藏实现业务授权；
-直接修改历史 Release 进行回滚。

本 ADR 只有在以下情况发生时才应被新 ADR 替代：

- Application 与 OAuth Client 的关系发生产品级变化；
-允许远程模块或可执行客户端配置；
- Permission 权威从 IAM 迁移；
- Catalog 发布模型从不可变快照变为其他权威模型；
-多租户或独立租户目录成为正式需求。