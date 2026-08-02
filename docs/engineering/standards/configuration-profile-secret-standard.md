# 运行时配置、Profile 与 Secret 工程规范

- 状态：Current
- 生效 Slice：P1.6 S03
- 决策来源：[ADR-021](../../adr/ADR-021-运行时配置来源与Secret边界.md)

## 1. 事实、决策与当前实现

| 类型 | 结论 |
|---|---|
| 官方框架事实 | Spring Boot 4.1 使用既定 `PropertySource`/Config Data 优先级；环境变量和命令行可以覆盖文件；`@ConfigurationProperties` 可进行结构化绑定和校验 |
| 维护方事实 | Spring Cloud Alibaba 2025.1.x 不再支持 Bootstrap，Nacos Config 使用 `spring.config.import`，可通过 Import Check 发现依赖与导入不一致 |
| MOM 决策 | Secret 来源、正式 Profile Fail Fast、安全关键配置不动态刷新、Discovery 与 Config 分离 |
| 当前实现 | Base 配置使用 loopback/空 Secret/外部能力关闭；没有 Nacos Config Starter；IAM 已对 Bootstrap、JWK、Pepper 和生产 Issuer 实施部分启动校验 |

## 2. Profile 契约

### 2.1 Base

`application.yml` 必须环境中立：地址默认 loopback 或能力关闭，Secret 默认空，外部能力默认关闭，不得设置 `spring.profiles.active`。安全关键能力不能通过缺省值在正式环境静默可用。

### 2.2 local

仅用于开发者本机。允许 loopback HTTP、IAM Cookie `secure=false` 和显式技术探针；不得连接公网正式资源，不得被正式部署默认激活。S03 不为没有真实差异的模块机械创建文件。

### 2.3 test

只存在于测试资源或测试启动参数。可使用仓库内测试密钥和测试 Pepper，但不得访问正式外部资源，也不得被主运行包当作正式凭据来源。

### 2.4 ci

只在真实 CI 场景需要时使用，必须限定服务和中间件地址，不携带生产 Secret，不由本地或生产自动激活。

### 2.5 prod 与 production

两者在启动校验中视为等价正式 Profile。任一 Profile 都必须拒绝：启用管理员 Bootstrap、测试密钥、本地 Pepper、Cookie `secure=false`、缺失或非 HTTPS 外部 Issuer、缺少 JWK/Refresh Pepper、通配 Redirect URI、技术探针、危险 Actuator 暴露，以及关闭官方兼容性检查。

当前仓库不提交 `application-prod.yml`；正式值由部署环境提供，但代码中的 `Environment.acceptsProfiles(Profiles.of("prod", "production"))` 必须维持同义校验。

## 3. 类型安全配置

1. 配置族使用 `@ConfigurationProperties`；安全关键配置必须在创建运行 Bean 前校验。
2. Duration 使用 Spring Boot 支持的 `Duration` 绑定；URI 使用 `URI` 或显式解析。
3. 安全配置不得在业务代码中散落 `@Value`。
4. 环境变量名稳定且同一语义只保留一个权威名称；兼容别名必须写明退役计划。
5. 配置对象不得通过 `toString()` 输出 Secret。
6. 校验异常只报告属性路径、缺失或格式错误，不输出值，不连接外部系统试验凭据。
7. 安全配置不得进入日志、Trace、测试报告或未保护的 Actuator 输出。

## 4. Secret 分类

| 类别 | 例子 | 允许来源 | 动态刷新 |
|---|---|---|---|
| 身份签名 | JWK 私钥、Refresh Pepper | 环境变量、Kubernetes Secret、经 ADR 的 Secret Manager | 禁止 |
| 基础设施凭据 | PostgreSQL、Redis、Nacos 账号密码 | 环境变量、Kubernetes Secret、经 ADR 的 Secret Manager | 禁止 |
| 公共协议定位 | Issuer、JWK Set URI、Redirect URI | ConfigMap/环境变量；仍需正式校验 | 禁止 |
| 非敏感调优 | 有界超时、采样率、批次上限 | 配置文件、ConfigMap、受控 Nacos Config | 满足白名单条件后允许 |

普通 ConfigMap、普通 Nacos Config、Git、Maven Profile、镜像、前端变量和命令行都不是 Secret 来源。

## 5. Nacos

### 5.1 Discovery

- Base 默认关闭；正式环境显式启用。
- Namespace、Group、Cluster 和服务地址由部署环境指定。
- Username/Password 不得具有非空敏感默认值。
- `lb://` 路由只有在 Discovery 确实启用且健康时才有可用性含义；发现失败不得返回伪成功。

当前 IAM、Gateway、MDM、Integration 和业务骨架仅配置 Discovery；启用与否不改变 Nacos Config 边界。

### 5.2 Config

当前没有正式调用方，因此不得提前引入 Starter、Data ID 或导入。未来接入：

- 使用 `spring.config.import=nacos:...`；
- 不使用 `bootstrap.yml`/`bootstrap.properties`；
- 不关闭 `spring.cloud.nacos.config.import-check.enabled`；
- 明确 Required/Optional，正式必需配置禁止 `optional:`；
- Data ID、Group、Namespace 受控；Secret 默认禁止；
- `refreshEnabled=true` 只允许白名单非敏感属性。

## 6. 动态刷新白名单条件

以下全部成立才可动态刷新：配置非敏感、不改变协议/授权/数据边界、存在安全默认值和校验、带版本与审计、失败可回滚。数据库/Redis凭据、JWK、Pepper、Issuer/JWK URI、Client/Redirect、TTL 安全语义、CORS、Permission、FilterChain、Schema/Flyway/Seata 均不在白名单。

## 7. Actuator 与诊断

Base 和正式环境只暴露最小集合：`health`、必要的 liveness/readiness、经网络与认证保护的 `info`/`prometheus`。`env`、`configprops`、`loggers`、`heapdump`、`threaddump`、`mappings` 不得无保护公开；`shutdown` 禁止。健康详情不得输出凭据、完整拓扑或异常栈。

当前 MDM/Integration 暴露 `bindings` 是 Phase 01 消息技术验证兼容现状，登记为 S04 Review 输入；S03 不改运行配置。

## 8. 自动门禁边界

运行时安全门禁检查可证明的 YAML/Properties 结构和受控 Java 注解候选，不连接外部系统，不读取或输出 Secret 值。复杂的部署值、Kubernetes RBAC、实际 TLS 与 Secret 挂载仍须部署 Review 和环境验收。

## 9. 官方依据

- [Spring Boot 4.1 Externalized Configuration](https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html)
- [Spring Boot 4.1 Profiles](https://docs.spring.io/spring-boot/4.1/reference/features/profiles.html)
- [Spring Boot 4.1 Actuator Endpoints](https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html)
- [Spring Cloud Alibaba Nacos 2025.x](https://sca.aliyun.com/docs/2025.x/user-guide/nacos/advanced-guide/)
