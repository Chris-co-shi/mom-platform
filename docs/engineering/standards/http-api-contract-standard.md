# MOM HTTP API 契约规范

- 状态：Accepted；响应格式已确认，既有不一致端点按兼容性治理
- 生效范围：MOM 第一方业务/管理 HTTP API
- 首次冻结：P1.6 S01

## 1. 规则与现状

- **官方框架事实**：Spring MVC 对 `@Valid @RequestBody` 与方法参数约束分别可能抛出 `MethodArgumentNotValidException` 和 `HandlerMethodValidationException`。框架支持其他错误格式，不改变 MOM 的响应契约。
- **MOM 项目决策**：第一方业务/管理 HTTP API 的成功与业务失败响应统一使用 `Result<T>`；同时遵循本文的路径、DTO、状态码、分页和规范化规则。
- **当前已有实现**：Mini Auth、业务 Controller 和 Framework WebMVC 使用 `Result<T>`；Gateway/Resource Server 认证错误各有 HTTP 边界。以当前源码和 OpenAPI 快照核实具体端点。
- **历史例外**：少数已发布旧端点响应不一致，见第 9 节；保留兼容行为直至完成调用方核查与明确迁移，不作为新接口范例。

官方来源：

- [Spring MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [Jakarta Validation 3.1](https://jakarta.ee/specifications/bean-validation/3.1/)
- [Spring Security 7 Reference](https://docs.spring.io/spring-security/reference/7.0/)

## 2. 路径与资源

1. 浏览器完整路径为 `/{模块前缀}/{Controller资源根}[/{业务操作}/{参数}]`。Gateway 模块前缀由 Gateway 配置（如 `/auth`、`/system`、`/mdm`、`/integration`），`StripPrefix=1` 后才进入服务；Controller 不重复声明模块前缀。第一方路径不包含 `/api`。
2. Controller 类级路径定义一个清晰的复数资源根；不同资源使用不同 Controller。集合使用复数名词，路径表达稳定业务资源，不暴露数据库表名。
3. 普通 CRUD 由 HTTP 方法表达操作：集合 `POST` 创建、`GET` 查询，单资源 `GET/PUT/DELETE` 操作资源；禁止仅为描述 CRUD 而在 URL 中重复 `create`、`get`、`update`、`delete`。
4. 状态切换、改密、设为默认等命令操作使用明确业务动词，动作放在资源参数前，例如 `PATCH /dictionary-items/change-status/{typeId}/{itemId}`；禁止 `/doSomething`、`/executeAction`。关系仍属于子资源时保留子资源路径，不改成替换命令。
5. GET 不得产生隐藏写副作用。仅在外部第三方公开、存在不可兼容变更且有并行兼容/退役计划时引入 URI Major Version。
6. 新 DELETE 默认不使用 Request Body；确需使用必须记录调用方兼容证据。IAM Admin 当前带 Body 的 DELETE 是已发布兼容现状，S01 不修改。
7. 批量接口必须明确最大条数、全成全败或部分失败结构，以及幂等策略；超过协议上限返回 413。

## 3. Request 与 Response DTO

- Web Request/Response DTO、跨服务 API DTO、Application Command/Result、Domain Model、数据库 Entity 是不同边界；不得为省转换而互相替代。
- 不直接暴露 MyBatis-Plus Entity、`Page` 或内部聚合；不以 `Map<String,Object>` 作为长期公共契约。
- 固定结构使用显式 `record`/class；Request 与 Response 分开，创建/更新/查询按语义拆分。
- 禁止用包含大量 Nullable 字段的万能 DTO 或任意 JSON 字段代替明确业务契约。
- ID 序列化为 JSON String；`Instant` 为 ISO-8601 UTC；`BigDecimal` 不转换为 `double`。
- 密码、Token、密钥、摘要、内部安全状态和敏感审计字段不得进入响应。
- 每个字段必须定义：缺失、显式 `null`、空字符串和空集合的含义；响应集合默认返回 `[]`，除非 `null` 具有明确业务意义。
- DTO 字段重命名、空值语义改变和枚举收窄都按兼容性变更处理。

IAM Controller 内嵌 Command、Map 响应和 Application Views 仅登记为现状，列入 S08/S09 候选；S01 不改变公开 JSON。

## 4. 校验与输入规范化

### 4.1 协议校验（Web）

Web/Bean Validation 负责必填、长度、数值范围、集合/批量上限、基础格式、时间格式、非法枚举和 Query 上限。新 MVC Controller 必须同时考虑对象校验与方法校验两种异常。

### 4.2 用例校验（Application）

Application 负责资源存在、状态转换、乐观版本、业务唯一性、调用主体对目标的业务授权和跨字段规则。

### 4.3 领域不变量（Domain）

Domain 负责聚合状态、值对象合法性和任何入口都不能绕过的不变量。

规范化规则：

- 普通名称、说明和由领域声明可规范化的编码可 Trim；用户名/业务编码大小写由所属领域定义；
- 密码、Token、签名和 `Idempotency-Key` 不得自动 Trim、改变大小写或做 Unicode 归一化；
- 禁止静默截断；超长输入返回明确校验错误；
- Controller 中重复手写 `blank()` 不是新代码长期标准；
- 现有安全端点手工校验和规范化在行为保持重构前不修改。

## 5. 成功响应

第一方业务/管理 Controller 使用 `mom-webmvc` 的 `Result<T>` 包装成功数据：`Result.success(data)`；无业务数据时使用 `Result.success()`。成功码固定为 `"0"`，响应体字段为 `code`、`message`、`data`。Application 不返回 `Result<T>`。新增接口不得直接返回业务 DTO 或自定义成功信封。

- HTTP 200/201/202 的使用由具体端点契约决定；不能从本段推断所有创建已返回 201。新接口应返回 `Result<T>` 正文，不新增无响应体的 204；既有 204 按兼容性治理。
- 不以 200 表示失败；列表元数据按项目分页契约返回。

## 6. MOM 业务错误响应

新业务/管理端点的业务失败响应使用 `Result.failure(code, message)`；若该端点需要结构化校验详情，可使用 `Result.failure(code, message, data)`，其 `data` 结构须由端点契约定义。HTTP 状态码仍表达失败类别，不能用 HTTP 200 包装失败。不得以直接返回 `ProblemDetail` 或其他错误信封代替 `Result<T>`。

- `code` 是稳定机器码；`message` 可本地化，不得成为客户端唯一分支依据；
- Correlation ID 只用于排障，不是业务 ID；Trace ID 不用于权限或幂等；
- 校验详情仅在校验失败时放入 `data`，结构参考实际端点的 Response DTO；
- 不返回 Java 类名、SQL、Stack Trace、数据库约束原文、密码、Token、Secret 或内网连接信息。

### 6.1 兼容边界

- Mini Auth 登录和业务响应使用 `Result<T>`；既有例外以各端点 ControllerAdvice、Security Filter Chain 和 OpenAPI 快照为准，不能从历史 IAM/SAS 文档推断。
- 旧 IAM/SAS/OIDC 协议端点当前未启用；重新引入需新 ADR。
- Gateway 与 Resource Server 的认证失败由 Security Filter Chain 中的 `AuthenticationEntryPoint`/`AccessDeniedHandler` 等协议边界处理，普通 ControllerAdvice 不得覆盖。未认证/无效认证为 401，已认证但无权为 403；防枚举例外必须有明确安全场景。

## 7. HTTP 状态码矩阵

| 状态 | MOM 使用语义 |
|---:|---|
| 200 | 查询或更新成功且有正文 |
| 201 | 资源创建成功 |
| 202 | 请求已接受并异步处理，不代表业务完成 |
| 204 | 已发布无正文响应的兼容现状；新业务/管理接口不采用 |
| 400 | 语法、绑定、基础校验、非法枚举或非法请求 |
| 401 | 缺少或无效认证、Token/Session 失效 |
| 403 | 已认证但无访问权限 |
| 404 | 资源不存在；仅明确防枚举场景可隐藏资源存在性 |
| 409 | 状态、唯一性、乐观版本或幂等键负载冲突 |
| 412 | 仅用于明确的 HTTP 条件请求前置条件失败 |
| 413 | 请求体或批量条数超过已声明上限 |
| 415 | 不支持的媒体类型 |
| 422 | MOM 当前不使用；可解析但业务不接受的请求仍按 400/409 的固定矩阵映射，避免同类错误漂移 |
| 429 | 限流 |
| 500 | 未预期服务端错误 |
| 502 | 上游返回无效协议响应 |
| 503 | 依赖暂时不可用或 fail-closed 保护 |
| 504 | 上游超时 |

数据库唯一约束必须转换为稳定 409，不能回显底层文本；乐观锁优先 409；不得把全部异常映射为 400，也不得把依赖故障映射为业务成功。

## 8. 分页、排序与过滤

### 8.1 控制面/中小列表

- 分页检索统一使用 `POST /资源/search`，请求体为强类型 `PageQuery<XxxPageParams>`；集合根路径的
  `POST` 继续用于创建，禁止同时保留旧 GET 分页协议；
- `pageNo`、`pageSize` 和 `params` 必填；`pageNo/pageSize` 必须为正数，`params` 没有过滤条件时传
  空对象 `{}`，禁止传 `null`、无类型 `Map` 或由服务端补默认值；
- 最大单页条数由 `mom.data.pagination.max-page-size` 配置；超限及其他非法值在 SQL 执行前返回 400，
  禁止静默纠正或由 MyBatis-Plus 截断；
- 必须稳定排序；相同排序值追加唯一 String ID 作为 tie-breaker；
- Controller 与 Application 均只接收 `PageQuery<XxxPageParams>` 这一项分页业务入参；Infrastructure
  可以接收由它转换出的 `IPage` 与强类型 Params，不得手工重复实现 Count、Offset 或 PageResult；
- 不返回 MyBatis `Page`；承诺总数时复用平台 `PageResult`；不承诺 `total` 时不得伪造或强制昂贵 Count。

### 8.2 高数据量流水/事件/审计/追溯

优先 Cursor/Seek Pagination，使用稳定排序字段、唯一 tie-breaker 和不透明 Cursor；禁止长期依赖深 Offset。

### 8.3 排序与过滤

- 排序字段必须白名单化并明确 ASC/DESC；客户端字段名不得直接拼 SQL；禁止任意表达式排序；
- 普通分页过滤使用 `PageQuery.params` 内的强类型业务参数；不支持任意字段、无类型操作符或 SQL；
- 日期范围为闭开区间 `[from, to)`；大小写、模糊匹配、空值语义必须逐接口说明；
- 批量 ID 查询必须有数量上限。

## 9. 当前历史例外

| 文件 | 偏差与证据 | 处理 Slice |
|---|---|---|
| `.../admin/IamAdminController.java`、`IamAdminExceptionHandler.java` | 已发布列表直接返回数组、错误为 `{code,message}`、撤销全部 Session 返回 `Map`、DELETE 带 Body | S09 基于 Web/Mobile 调用方证据设计兼容迁移 |
| `.../web/IamDirectAuthenticationController.java`、`IamDirectAuthenticationExceptionHandler.java` | 第一方认证内嵌 DTO、手工校验、设备名超过 120 字符会截断、错误为 `{error,message}` | S08 行为保持抽取；若端点仍启用，后续按 `Result<T>` 决策及调用方证据治理 |
| `.../resources/mapper/iam/IamUserMapper.xml`、`IamRoleMapper.xml`、`IamPermissionMapper.xml` | Offset 查询有上限但排序只使用非唯一业务字段 | S09 为 IAM 管理列表补唯一 ID tie-breaker，保持外部字段不变 |
| `.../resources/mapper/iam/IamUserSessionMapper.xml`、`IamSecurityAuditEventMapper.xml` | 高增长 Session/审计仍用 Offset，且时间排序无 ID tie-breaker | S09 先补稳定排序；Cursor 演进需兼容证据，复杂平台推迟 P1.7 |
| `.../admin/IamAdminService.java` | `limit<=0` 回退 50、负 offset 归零，而目标契约要求 400 | S09 结合调用方证据迁移，S01 不改状态码 |
