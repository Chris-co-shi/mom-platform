# MOM API 演进、HTTP 幂等与 OpenAPI 规范

- 状态：Accepted
- 首次冻结：P1.6 S01

## 1. HTTP 幂等与重复提交

必须区分 HTTP 方法幂等、业务唯一约束、请求幂等键、消息消费幂等和分布式锁，它们互不替代。

1. GET、PUT、DELETE 必须满足各自协议语义下的幂等；GET 不写业务状态。
2. 可能被客户端、Gateway 或网络安全重试的 POST 命令必须评估 `Idempotency-Key`；不要求所有 POST 支持。
3. 幂等身份至少绑定调用主体、Client、规范化路由/操作和请求负载摘要。
4. 同一 Key + 相同负载返回首次结果或稳定等价结果；同一 Key + 不同负载返回 409。
5. 幂等记录必须有 TTL 或明确业务生命周期，并保存足以判断进行中/成功/失败和重放响应的状态。
6. 原始 Key 不写日志、不直接进入 Redis Key/数据库可观测字段；保存不可逆摘要。原始 Key 不 Trim、不改大小写、不做 Unicode 归一化。
7. 幂等键不替代数据库业务唯一约束；分布式锁不替代幂等；消息 Inbox 不替代 HTTP 请求幂等。
8. Redis 不可用时必须按操作风险明确 fail-open/fail-closed；不得把绕过保护伪装为已取得幂等执行权。
9. S01 不新增幂等存储，只冻结适用矩阵；库存、工单、质量、外部命令等副作用场景在各业务 Slice 实现。

### 1.1 当前 Framework 差异

| 文件 | 当前语义 | 后续处理 |
|---|---|---|
| `mom-framework/mom-idempotency/.../RedisIdempotencyKeyFactory.java` | 对原始 `requestKey` 执行 `trim()` 后摘要，与“不规范化原始 Key”目标冲突 | S03 Redis/安全配置规范实施时修正并增加兼容测试 |
| `mom-framework/mom-idempotency/.../RedisIdempotencyGuard.java` | 只做 SET-NX-TTL 占位，不绑定主体、Client、路由和负载摘要，也不保存首次结果 | 保留为低阶技术原语；具体业务 Slice 不得直接宣称其满足完整 HTTP 幂等 |
| `mom-integration-platform/.../IntegrationIdempotencyProbeController.java` | 条件开启的 Phase 01 技术探针，重复直接 409，不重放首次结果 | 仅保留技术探针语义，不作为正式 API 范例；S01 不改行为 |

## 2. OpenAPI 策略

- OpenAPI 用于 HTTP 契约文档、Review、调用方沟通和兼容检查，不是领域模型或数据库模型；
- OAuth2/OIDC 标准端点优先引用官方 Discovery 与协议文档，不重新生成或包装其语义；
- 内部管理 API 与外部集成 API 可以采用不同发布门槛，但已发布契约都必须受兼容治理；
- OpenAPI 注解只能位于 Web/API Adapter，不得污染 Domain/Application；权限、幂等、失败策略和业务不变量不能只存在于注解；
- S01 不引入运行时 OpenAPI 依赖。维护方资料表明 springdoc-openapi 3.x 支持 Spring Boot 4，但正式引入前仍需在仓库锁定的 Boot 4.1.0、Framework 7.1、Jackson 3 组合上做生成、启动和契约验证；
- 可先维护审阅过的静态 OpenAPI 文件；生成产物必须可重复、纳入版本控制策略并能与基线比较。

维护方证据：[springdoc-openapi 3.x / Spring Boot 4 文档](https://springdoc.org/v4/)。springdoc 是社区项目，不是 Spring 官方组件，因此该兼容声明不得写成 Spring 官方保证。

未来契约检查至少检测：路径删除、字段删除、必填字段增加、类型变化、Enum 收窄、状态码变化、安全方案变化和分页语义变化。工具选择与 CI 集成在有真实 OpenAPI 产物后实施，S01 不强行增加依赖。

## 3. API 兼容性分类

### 3.1 通常兼容

- 增加可选响应字段；
- 增加独立端点；
- 增加可选 Query 参数；
- 增加新的稳定错误码且不改变既有错误语义。

兼容仍需契约测试；严格反序列化客户端、签名 Payload 或字段顺序依赖可能使“通常兼容”变为实际破坏。

### 3.2 通常破坏性

- 删除/重命名字段或端点；改变字段类型、ID 格式、时间/时区或空值语义；
- 可选字段改必填；枚举值删除/收窄；分页方式或状态码改变；
- 安全方案、权限要求、Token Claim、OAuth Client 行为改变。

“收窄权限”从安全角度可能必要，但对调用方仍是兼容性事件，必须同时走安全处置和迁移沟通，不能以兼容为由保留已确认漏洞。

## 4. 弃用与删除

公开契约删除前必须同时具备：

1. 调用方清单与仓库搜索/运行使用统计证据；
2. 明确替代契约；
3. 公布的兼容期和迁移文档；
4. 回滚方案；
5. Chris 明确批准；
6. ADR 或明确阶段决策。

不得因代码重复、目录不符合新规范或“理论上无人使用”删除端点。复杂自动弃用平台推迟到 P1.7；当前以版本化文档、调用方证据和明确决策治理。

## 5. Review 与发布门禁

- PR 必须声明新增/兼容/破坏/弃用类别；
- 破坏性变更必须给出并行版本或迁移窗口；
- 错误码、状态码、权限和幂等变化必须纳入契约测试；
- OpenAPI 基线可用后，CI 必须阻止未获批准的破坏性差异；
- 标准 OAuth2/OIDC 协议响应不参与 MOM ProblemDetail 统一迁移。
