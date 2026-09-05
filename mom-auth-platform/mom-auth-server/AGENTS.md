# Mini Auth Server 代码约束

本文件适用于 `mom-auth-platform/mom-auth-server` 及其全部子目录，并在仓库根目录 `AGENTS.md` 的基础上追加 Mini Auth 专属约束。后续人工开发、AI 编码工具和自动化 Agent 修改本模块前都必须先阅读本文件。

## 1. V1 范围冻结

Mini Auth V1 只实现以下闭环：

```text
User
 ↓
Role
 ↓
Permission
 ↓
Login
 ↓
Redis-backed Opaque Token
 ↓
Resource Server
 ↓
@PreAuthorize
 ↓
Logout
```

V1 不引入 Spring Authorization Server、OAuth Client、Refresh Token、Session 平台、OIDC/SSO、JWT/JWK、SYSTEM/SERVICE Identity、动态授权策略或动态 i18n 框架。没有新的真实业务需求和 ADR，不得扩大范围。

## 2. 分层与依赖

当前固定为 Level 1：

```text
controller
  ↓
application
  ↓
infrastructure
```

- Controller 只做 HTTP 协议适配、Bean Validation、`Result<T>` 包装和 `@PreAuthorize`；禁止直接访问 Mapper、Entity、PasswordEncoder、Redis 或 TokenStore。
- Application 负责用例编排、事务、引用校验、关系整体替换、乐观锁冲突和业务异常；允许直接依赖本 bounded context 的 Mapper/Entity/Infrastructure。
- Infrastructure 承载 Entity、Mapper、复杂 Query、Spring Security 适配与技术配置。
- 不为形式统一新增 Domain、Repository Port、Adapter Port、Command Bus、Query Bus、Assembler、Converter 或 MyBatis-Plus `IService/ServiceImpl`。
- `UserApplication`、`RoleApplication`、`PermissionApplication`、`AuthenticationApplication` 是当前有效用例层，禁止为了“简化”删除、改成占位内容或把职责搬回 Controller/Mapper。

任何删除或大范围改写现有 Application 类前，必须先说明要解决的真实问题并检查完整 diff；禁止以 `noop`、空类或占位实现替换现有业务代码。

## 3. Entity / View 转换

简单单表 1:1 映射统一使用：

```text
XxxView.from(XxxEntity)
```

目的只是集中机械字段映射，避免在多个 Application 中重复 `toView()` 私有方法。

约束：

- 不因为简单字段复制创建 `XxxConverter`、MapStruct Mapper 或 Assembler；
- Entity 不得反向依赖 Application View，即禁止 `entity.toView()`；
- 当转换出现多来源聚合、复杂条件、外部数据或多目标模型时，再基于真实复杂度讨论独立转换组件。

## 4. MyBatis-Plus 与分页

单表 CRUD 优先复用 `MomBaseMapper<T>` 和 MyBatis-Plus 原生能力。

分页固定链路：

```text
PageQuery
→ PageAdapter.toPage(...)
→ BaseMapper.selectPage(...)
→ PageAdapter.toResult(page, XxxView::from)
→ PageResult<View>
```

禁止：

- Application 手工计算 `offset`、`totalPages`；
- 为单表分页新增 `countActive()`、手写 `LIMIT/OFFSET`；
- 通过 Auth 私有 `PageView/PageResponse` 重复 `PageResult` 字段；
- 把 MyBatis-Plus `Page/IPage` 暴露到 HTTP；
- 使用已废弃的 `selectBatchIds(...)`，当前使用 `selectByIds(...)`。

稳定排序必须显式保留：User 按 `username,id`，Role/Permission 按 `code,id`。

只有真实多表 JOIN、聚合读取或 MyBatis-Plus 明显无法表达的查询才进入 `infrastructure.query`。

## 5. 用户名密码认证

用户名密码认证必须复用 Spring Security 原生认证引擎：

```text
AuthenticationApplication
→ AuthenticationManager
→ ProviderManager
→ DaoAuthenticationProvider
→ AuthUserDetailsService
→ AuthUserPrincipal
```

- `AuthenticationApplication` 不得重新手工查询用户、调用 `PasswordEncoder.matches()` 或自行判断 enabled；
- `UserEntity` 不实现 `UserDetails`；Spring Security 使用独立 `AuthUserPrincipal`；
- `userId` 是 MOM 稳定身份，`username` 只是登录名称；
- V1 只实现 enabled，账号过期、锁定、密码过期保持 `true`，不得提前增加无需求字段。

Spring Security 只负责 Credential Authentication。认证成功后，MOM 才负责 Opaque Token 的生成、存储和注销；复用 Spring Security 不等于迁移到 Session/JWT/Spring Authorization Server。

## 6. Opaque Token 与 Logout

V1 Token 规则冻结：

- `SecureRandom` 生成 32 bytes（256 bit）；
- Base64URL without padding 输出 raw token；
- TokenStore 使用 Redis；
- Token Principal 只保存 `userId + authorities + expiresAt`；
- Redis/TokenStore 故障 Fail Closed；
- Logout 只删除当前已经通过 Resource Server 验证的 Token；
- Controller 不手工解析 `Authorization` Header，不 `substring("Bearer ")`；
- 不实现 revoke-all、用户到 Token 索引或实时权限刷新。

## 7. RBAC

授权链固定为：

```text
User → Role → Permission → GrantedAuthority → @PreAuthorize
```

- 角色 authority 使用 `ROLE_*`；Permission 保持业务 code；
- `PLATFORM_ADMIN` 不允许硬编码绕过 Permission；
- 只加载 enabled Role、enabled Permission 和未逻辑删除数据；
- 用户没有角色/权限仍可登录，最终 authorities 可以为空；
- 权限快照在登录时写入 Token，V1 不承诺角色/权限修改后立即影响既有 Token。

## 8. HTTP 返回与异常

- Controller 返回 `Result<T>`；Application 不返回 `Result`；
- 分页返回 `Result<PageResult<Response>>`；
- HTTP status 与业务 `code/message/data` 是两个维度，禁止把所有错误强行变成 200；
- 400/401/403/404/409/500/503 按真实语义使用；
- Logout/Delete 使用统一 Result 信封，不额外制造 Auth 私有响应协议。

## 9. 数据完整性

继续遵守 ADR-026：`auth_user`、`auth_role`、`auth_permission`、`auth_user_role`、`auth_role_permission` 不建立物理外键和级联。

完整性由 Application 校验、唯一约束、索引、本地事务、乐观锁和测试共同保证。删除 User/Role/Permission 前必须显式检查关系引用；关系整体替换必须位于本地事务。

## 10. 注释要求

除根 `AGENTS.md` 的通用规则外，Mini Auth 重点解释以下无法从语法直接恢复的设计意图：

- Controller / Application / Infrastructure 的职责边界；
- 为什么 `UserEntity` 与 `UserDetails` 分离；
- Spring Security Credential Authentication 与 MOM Opaque Token 的责任边界；
- V1 为什么账号过期、锁定、密码过期固定返回 true；
- Token 随机强度、凭据清理和 Fail Closed 等安全语义；
- PageAdapter/PageResult 为什么是唯一分页适配链路；
- 为什么简单 Entity/View 映射不用 Converter/MapStruct；
- 为什么删除资源前要做关系引用保护。

所有新增或实质修改的公共类/接口/record 和公共方法必须有准确中文 Javadoc。禁止逐行翻译代码或堆砌“查询数据”“判断为空”之类无信息注释。

## 11. 验证与变更纪律

- 不得声称 Maven、单测、集成测试或真实基础设施验证成功，除非实际执行；
- 修改后必须检查最终 diff，尤其是删除、大文件替换和跨层移动；
- 优先做小范围、可解释、可回退的修改；
- 新增抽象前必须回答“当前哪个真实问题要求它存在？”；
- Framework 已有能力优先复用，不在 Auth 内复制通用分页、Result、TokenStore 等基础设施。

架构说明同时参考：

- `docs/architecture/Mini-Auth数据库与代码分层.md`
- `docs/security/P1.5-认证与授权设计基线.md`
- `docs/adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md`
- `docs/adr/ADR-042-MOM渐进式分层与对象模型.md`
