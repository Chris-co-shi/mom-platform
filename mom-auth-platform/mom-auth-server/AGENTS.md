# Mini Auth Server 专属约束

本文件只追加 `mom-auth-platform/mom-auth-server` 的认证领域特有规则。**全项目通用的 Java 注释、分层、MyBatis-Plus、CrudRepository、批量操作、事务、Entity/View、分页、测试和变更纪律，以仓库根 `AGENTS.md` 及 `docs/engineering/standards` 为准，不在本文件重复冻结。**

后续人工开发者、AI 编码工具或自动化 Agent 修改本模块前，必须同时遵守：

- 根 `AGENTS.md`；
- `docs/engineering/standards/crud-application-standard.md`；
- `docs/engineering/standards/persistence-data-modeling-standard.md`；
- 本文件以下 Mini Auth 专属约束。

## 1. V1 范围冻结

Mini Auth V1 只实现：

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

V1 不引入 Spring Authorization Server、OAuth Client、Refresh Token、Session 平台、OIDC/SSO、JWT/JWK、SYSTEM/SERVICE Identity、动态授权策略或动态 i18n 框架。没有新的真实业务需求和 ADR，不扩大范围。

## 2. 当前 Auth 用例边界

Mini Auth 当前保持 Level 1：

```text
controller → application → infrastructure
```

`UserApplication`、`RoleApplication`、`PermissionApplication`、`AuthenticationApplication` 是当前有效用例层。禁止为了“简化”删除、改成占位内容，或把业务职责搬回 Controller/Mapper。

任何删除或大范围改写现有 Application 类前，必须先说明真实问题并检查完整 diff；禁止以 `noop`、空类或占位实现替换现有业务代码。

Auth 当前 User/Role/Permission 单表 CRUD 和关系编排没有形成可复用的持久化技术层，因此继续直接使用 Mapper。**这不是对 MyBatis-Plus `CrudRepository` 的全局禁止。**以后如果 Auth 内真实出现可复用 batch 策略、持久化组合或技术数据访问职责，应按全项目标准评估是否引入具体 `XxxRepository extends CrudRepository`。

## 3. 用户名密码认证

用户名密码认证必须复用 Spring Security 原生认证引擎：

```text
AuthenticationApplication
→ AuthenticationManager
→ ProviderManager
→ DaoAuthenticationProvider
→ AuthUserDetailsService
→ AuthUserPrincipal
```

- `AuthenticationApplication` 不重新手工查询用户、调用 `PasswordEncoder.matches()` 或自行判断 enabled；
- `UserEntity` 不实现 `UserDetails`，使用独立 `AuthUserPrincipal`；
- `userId` 是 MOM 稳定身份，`username` 只是登录名称；
- V1 只实现 enabled，账号过期、锁定、密码过期保持 `true`，不得提前增加无需求字段。

Spring Security 负责 Credential Authentication；认证成功后 MOM 才负责 Opaque Token 的生成、存储和注销。复用 Spring Security 不等于迁移到 Session/JWT/Spring Authorization Server。

## 4. Opaque Token 与 Logout

V1 Token 规则冻结：

- `SecureRandom` 生成 32 bytes（256 bit）；
- Base64URL without padding 输出 raw token；
- TokenStore 使用 Redis；
- Token Principal 只保存 `userId + authorities + expiresAt`；
- Redis/TokenStore 故障 Fail Closed；
- Logout 只删除当前已经通过 Resource Server 验证的 Token；
- Controller 不手工解析 `Authorization` Header，不 `substring("Bearer ")`；
- 不实现 revoke-all、用户到 Token 索引或实时权限刷新。

## 5. RBAC

授权链固定为：

```text
User → Role → Permission → GrantedAuthority → @PreAuthorize
```

- 角色 authority 使用 `ROLE_*`；Permission 保持业务 code；
- `PLATFORM_ADMIN` 不允许硬编码绕过 Permission；
- 只加载 enabled Role、enabled Permission 和未逻辑删除数据；
- 用户没有角色/权限仍可登录，authorities 可以为空；
- 权限快照在登录时写入 Token，V1 不承诺角色/权限修改后立即影响既有 Token。

## 6. Auth 数据完整性

继续遵守 ADR-026：`auth_user`、`auth_role`、`auth_permission`、`auth_user_role`、`auth_role_permission` 不建立物理外键和级联。

Auth 的额外要求：

- 删除 User/Role/Permission 前显式检查关系引用；
- User-Role、Role-Permission 整体替换位于 Application 本地事务；
- 登录 authority 查询必须排除 disabled / logically deleted Role 和 Permission；
- 关系或权限变化不主动回收已经签发的 Token，直到 Logout 或 TTL 到期。

## 7. Auth 当前分页与映射实现

当前 User/Role/Permission 分页继续复用全项目 `PageAdapter/PageResult`，并保持稳定排序：

```text
User        username, id
Role        code, id
Permission  code, id
```

批量主键查询使用当前 MyBatis-Plus `selectByIds(...)`，不恢复已废弃的 `selectBatchIds(...)`。

简单 Entity → View 当前使用 `XxxView.from(XxxEntity)`；如果以后全项目对象转换规范发生变化，Auth 跟随全项目规则，不在本模块另起一套 Converter 体系。

## 8. Auth 安全注释重点

公共类型和公共方法的中文 Javadoc 要求来自根 `AGENTS.md`。Auth 额外必须解释这些安全意图：

- `UserEntity` 与 `UserDetails` 分离的原因；
- Spring Security Credential Authentication 与 MOM Opaque Token 的责任边界；
- V1 为什么账号过期、锁定、密码过期固定返回 true；
- Token 随机强度、凭据清理和 Fail Closed；
- authority 快照及“不实时刷新”的 V1 限制；
- Logout 为什么只处理当前已认证 Token。

## 9. 参考决策

- `docs/architecture/Mini-Auth数据库与代码分层.md`
- `docs/security/P1.5-认证与授权设计基线.md`
- `docs/adr/ADR-040-Mini-Auth与Redis-Opaque-Token认证基线.md`
- `docs/adr/ADR-042-MOM渐进式分层与对象模型.md`
- `docs/engineering/standards/crud-application-standard.md`
- `docs/engineering/standards/persistence-data-modeling-standard.md`
