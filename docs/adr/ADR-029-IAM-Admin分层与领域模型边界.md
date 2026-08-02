# ADR-029：IAM Admin 分层与领域模型边界

- 状态：Accepted
- 日期：2026-07-31
- 决策人：Chris
- 适用范围：`mom-iam-platform/mom-iam-server`
- 关联决策：ADR-024、ADR-025、ADR-027、ADR-028

## 1. 背景

S09 将原单体 Admin Service 拆为多个用例服务，但实现仍集中在
`io.github.chrisshi.mom.iam.admin`。该包同时承载 Controller、HTTP Command、
Application Service、Spring Configuration、审计构造、Session 撤销和安全守卫。
Application Service 直接依赖 Infrastructure Repository，User、Role 与授权关系的
状态规则则主要表现为 Application 中的条件分支。

该结构虽然保持了接口行为，却没有满足
`Web → Application → Domain Port ← Infrastructure Adapter`，也没有充分利用
ADR-028 冻结的 MyBatis-Plus Repository 复用边界。

## 2. 决策

### 2.1 删除混合 Admin 包

正式生产代码不得再位于 `io.github.chrisshi.mom.iam.admin`。

- HTTP Controller、错误映射和请求上下文转换进入 `web.admin`；
- 用例、Command、View、Actor、审计编排和 Outbound Port 进入
  `application.admin`；
- User、Role、User Authorization 与跨聚合策略进入 `domain`；
- MyBatis Adapter 和 Query Adapter 保持在 `infrastructure.persistence`；
- Spring Bean 装配进入顶层 `configuration`。

不保留旧 Package 代理类，不保留 `IamAdminService` 兼容 Facade。

### 2.2 Application 不认识 Spring 入站对象和 Infrastructure

Application Admin：

- 不接收 `Authentication`、`HttpServletRequest` 或 Servlet DTO；
- 不依赖 Mapper、Persistence Entity 或具体 MyBatis Repository；
- 接收 `IamAdminActor`、`IamAdminRequestContext` 与稳定 Command；
- 只依赖 Domain Repository Port 和 Application Outbound Port；
- 公开写用例继续拥有原本地事务。

Web Adapter 负责把已验证的 `MomJwtAuthorization` 转换为 `IamAdminActor`。
Permission 校验仍在 Application Actor 边界执行，避免绕过 Controller 调用时失去授权。

### 2.3 User、Role 和授权关系成为领域模型

`IamUserAccount` 承载：

- 聚合版本校验；
- 自禁用、自删除和管理员自重置保护；
- 账号状态迁移；
- Party Binding 和 Mobile Access 资格；

`IamRole` 承载：

- 内置角色不可修改；
- 内置角色 Permission 不可替换；
- 角色状态和用户类型分配资格；
- PLATFORM_ADMIN 固定不变量；
- 聚合版本校验。

`IamUserAuthorization` 承载：

- User Version 统一并发边界；
- Role、Factory Scope、Mobile Access、Party Binding 关系变更决策；
- 外部用户必须存在有效 Party Binding；
- 禁用 Mobile Access 和 Party 重绑的 Session 撤销决策。

`PlatformAdministratorRetentionPolicy` 承载至少保留一个有效
`PLATFORM_ADMIN` 的跨聚合策略。Application 负责加载锁定事实，Domain Policy
负责裁决。

### 2.4 Repository 与 Query 分离

- User 与 Role 是单 Mapper、单 Entity、普通 CRUD 主路径的 Domain Aggregate；
  对应 Adapter 必须继承 Spring `CrudRepository` 并实现 Domain Repository Port。
- Authorization Assignment、User Access 是多表关系 Adapter，不机械继承
  `CrudRepository`。
- Session、Permission、Authorization Read Model 和 Security Audit 查询使用
  专用 Query Port/Adapter。
- OAuth Client Policy 保持 SAS 协议联合投影与受控状态更新，不伪装成普通单表
  Domain Repository。

### 2.5 兼容边界

本次重构必须保持：

- 25 条 Admin HTTP Method 与 Path；
- JSON 请求字段名和类型；
- 成功状态码、错误码和两字段错误响应；
- Permission Code；
- 16 个公开写用例事务入口；
- PostgreSQL 行锁、Version CAS、关系替换、Session 撤销和审计顺序；
- OAuth2/OIDC、Token、Session 和 SAS Registered Client 协议。

## 3. 自动门禁

- Java Persistence Baseline 将 IAM User/Role Adapter 加入必需
  `CrudRepository` 清单；
- ArchUnit 禁止 `application.admin` 依赖 Infrastructure、Web、Spring Security、
  Spring Web 和 Servlet；
- ArchUnit 禁止 `web.admin` 直接依赖 Infrastructure；
- ArchUnit 要求旧 `iam.admin` Package 为空；
- ArchUnit 验证 User/Role Adapter 同时继承 `CrudRepository` 并实现 Domain Port；
- Controller 契约测试聚合检查拆分后的全部 25 条路由；
- Domain 单元测试覆盖 User、Role 和 User Authorization 核心不变量。

## 4. 后果

正向结果：

- IAM Admin 不再以一个混合 Package 逃避标准分层；
- Application 与 MyBatis/Spring Web 解耦；
- User、Role 与授权关系拥有可独立测试的状态行为；
- 单表 Adapter 复用 MyBatis-Plus，多表和 Query 场景保持精确建模；
- 删除无业务价值的兼容 Facade 和 God Support。

代价：

- 类型数量增加，但每个类型拥有明确架构角色；
- Domain 与 Application 失败需要显式映射；
- 后续新增 IAM Admin 能力必须先选择聚合、用例和 Port，而不能继续向一个通用
  Admin Service 追加方法。
