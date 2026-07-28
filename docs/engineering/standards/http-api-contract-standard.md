# MOM HTTP API 契约规范

- 状态：Accepted
- 生效范围：MOM 第一方业务/管理 HTTP API
- 首次冻结：P1.6 S01

## 1. 四类事实

- **官方框架事实**：Spring Framework 7 以 RFC 9457 `ProblemDetail`、`ErrorResponse`、`ErrorResponseException` 和 `ResponseEntityExceptionHandler` 支持 HTTP 问题详情；Spring MVC 对 `@Valid @RequestBody` 与方法参数约束分别可能抛出 `MethodArgumentNotValidException` 和 `HandlerMethodValidationException`。
- **MOM 项目决策**：本文的路径、DTO、状态码、分页、规范化和错误扩展字段规则。
- **当前已有实现**：IAM Admin、第一方 JSON 认证、Gateway/Resource Server 和技术探针已有已发布契约。
- **未来迁移目标**：普通 MOM 业务 API 渐进采用本文目标；S01 不改写现有公开响应。

官方来源：

- [Spring Framework 7 Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [Jakarta Validation 3.1](https://jakarta.ee/specifications/bean-validation/3.1/)
- [Spring Security 7 Reference](https://docs.spring.io/spring-security/reference/7.0/)
- [Spring Authorization Server Protocol Endpoints](https://docs.spring.io/spring-authorization-server/reference/protocol-endpoints.html)

## 2. 路径与资源

1. 现有 `/api/iam/**` 等公开路径在 S01 保持不变，不批量增加 `/v1`。
2. MOM 内部第一方正式 API 默认使用 `/api/{bounded-context}/...`。
3. 集合使用复数名词；路径表达稳定业务资源，不暴露数据库表名。
4. CRUD 使用 GET/POST/PUT/PATCH/DELETE 的标准语义；GET 不得产生隐藏写副作用。
5. 真实业务命令无法自然表达为资源状态变化时，允许 `/users/{id}/unlock`、`/sessions/{id}/revoke` 等领域命令；禁止 `/doSomething`、`/executeAction`。
6. 仅在外部第三方公开、存在不可兼容变更且有并行兼容/退役计划时引入 URI Major Version。
7. 新 DELETE 默认不使用 Request Body；确需使用必须记录调用方兼容证据。IAM Admin 当前带 Body 的 DELETE 是已发布兼容现状，S01 不修改。
8. 批量接口必须明确最大条数、全成全败或部分失败结构，以及幂等策略；超过协议上限返回 413。

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

MOM 不引入通用 `ApiResponse<T>` 成功信封。HTTP 已表达状态；通用包装增加噪音，容易产生 HTTP 200 + 业务失败，并妨碍流、文件、分页及 OAuth2/OIDC 标准协议。

- 成功直接返回明确 Response DTO；
- 查询或更新成功且有正文：200；创建：201；异步接受：202；无正文：204；
- 不以 200 表示失败；列表是否含元数据由分页契约决定。

## 6. MOM 业务错误目标契约

普通 MOM 业务 API 目标采用 Spring Framework 7 `ProblemDetail/ErrorResponse`，媒体类型为 `application/problem+json`：

```json
{
  "type": "https://mom.example/problems/resource-conflict",
  "title": "Resource conflict",
  "status": 409,
  "detail": "资源状态不允许当前操作",
  "instance": "/api/wms/reservations/123",
  "code": "wms.reservation.state_conflict",
  "correlationId": "...",
  "fieldErrors": [
    {"field": "quantity", "code": "positive", "message": "必须大于 0"}
  ]
}
```

- `code` 是稳定机器码；`detail/message` 可本地化，不得成为客户端唯一分支依据；
- `correlationId` 只用于排障，不是业务 ID；Trace ID 不用于权限或幂等；
- `fieldErrors` 仅在字段校验失败时出现，条目固定为 `field/code/message`；
- 不返回 Java 类名、SQL、Stack Trace、数据库约束原文、密码、Token、Secret 或内网连接信息。

### 6.1 兼容边界

- IAM Admin `{code,message}` 是已发布兼容契约，S09 迁移必须提供调用方证据、兼容策略和回归测试；
- 第一方 JSON 认证 `{error,message}` 在 S07/S08 协议裁决前保持；
- Spring Authorization Server OAuth2/OIDC 端点继续返回协议规定的 OAuth2/OIDC 错误，禁止包装为 MOM ProblemDetail；
- Gateway 与 Resource Server 的认证失败由 Security Filter Chain 中的 `AuthenticationEntryPoint`/`AccessDeniedHandler` 等协议边界处理，普通 ControllerAdvice 不得覆盖。未认证/无效认证为 401，已认证但无权为 403；防枚举例外必须有明确安全场景。

## 7. HTTP 状态码矩阵

| 状态 | MOM 使用语义 |
|---:|---|
| 200 | 查询或更新成功且有正文 |
| 201 | 资源创建成功 |
| 202 | 请求已接受并异步处理，不代表业务完成 |
| 204 | 成功且无正文 |
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

- 允许 `limit/offset`；默认 `limit=50`，最大 200，`offset >= 0`；非法值返回 400，不静默纠正；
- 必须稳定排序；相同排序值追加唯一 String ID 作为 tie-breaker；
- 不返回 MyBatis `Page`；承诺总数时结构为 `items/total/limit/offset`；不承诺 `total` 时不得伪造或强制昂贵 Count。

### 8.2 高数据量流水/事件/审计/追溯

优先 Cursor/Seek Pagination，使用稳定排序字段、唯一 tie-breaker 和不透明 Cursor；禁止长期依赖深 Offset。

### 8.3 排序与过滤

- 排序字段必须白名单化并明确 ASC/DESC；客户端字段名不得直接拼 SQL；禁止任意表达式排序；
- 过滤使用显式 Query 参数或 Query DTO；不支持任意字段/操作符/SQL；
- 日期范围为闭开区间 `[from, to)`；大小写、模糊匹配、空值语义必须逐接口说明；
- 批量 ID 查询必须有数量上限。

## 9. 当前历史例外

| 文件 | 偏差与证据 | 处理 Slice |
|---|---|---|
| `.../admin/IamAdminController.java`、`IamAdminExceptionHandler.java` | 已发布列表直接返回数组、错误为 `{code,message}`、撤销全部 Session 返回 `Map`、DELETE 带 Body | S09 基于 Web/Mobile 调用方证据设计兼容迁移 |
| `.../web/IamDirectAuthenticationController.java`、`IamDirectAuthenticationExceptionHandler.java` | 第一方认证内嵌 DTO、手工校验、设备名超过 120 字符会截断、错误为 `{error,message}` | S08 行为保持抽取；S09 仅在协议决定后迁移 |
| `.../resources/mapper/iam/IamUserMapper.xml`、`IamRoleMapper.xml`、`IamPermissionMapper.xml` | Offset 查询有上限但排序只使用非唯一业务字段 | S09 为 IAM 管理列表补唯一 ID tie-breaker，保持外部字段不变 |
| `.../resources/mapper/iam/IamUserSessionMapper.xml`、`IamSecurityAuditEventMapper.xml` | 高增长 Session/审计仍用 Offset，且时间排序无 ID tie-breaker | S09 先补稳定排序；Cursor 演进需兼容证据，复杂平台推迟 P1.7 |
| `.../admin/IamAdminService.java` | `limit<=0` 回退 50、负 offset 归零，而目标契约要求 400 | S09 结合调用方证据迁移，S01 不改状态码 |
