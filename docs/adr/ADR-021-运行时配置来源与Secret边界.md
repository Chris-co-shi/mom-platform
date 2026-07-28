# ADR-021：运行时配置来源与 Secret 边界

- 状态：Accepted
- 日期：2026-07-28
- 关联：ADR-019、P1.5 认证与授权设计基线、运行时配置与 Secret 工程规范

## 1. 背景

MOM 已使用 Spring Boot 配置文件、环境变量、Kubernetes ConfigMap/Secret 和 Nacos Discovery，未来可能使用 Nacos Config。若不冻结来源、覆盖和刷新边界，安全关键值可能被写入普通配置中心、被动态刷新，或由不安全默认值掩盖正式环境缺失。

Spring Boot 4.1 的事实是：配置源具有明确优先级，后加载的高优先级来源可覆盖低优先级来源；配置文件、环境变量、系统属性和命令行参数均属于框架既有机制。MOM 不实现第二套覆盖系统。Spring Cloud Alibaba 2025.1.x 已移除 Bootstrap 接入方式，Nacos Config 必须使用 `spring.config.import`。

## 2. 决策

### 2.1 配置来源

允许的运行时配置来源按职责分为：

1. 代码内稳定且不改变安全语义的默认值；
2. `application.yml` 中的环境中立配置；
3. 显式 Profile 配置；
4. 环境变量；
5. Kubernetes ConfigMap；
6. Kubernetes Secret；
7. Nacos Config 中经批准的非敏感运行参数；
8. 仅用于诊断或一次性执行的命令行覆盖。

实际覆盖次序以 Spring Boot 4.1 官方 `PropertySource` 和 Config Data 规则为准。Kubernetes 挂载文件、环境变量和 Nacos 导入最终都进入 Spring Environment，MOM 不声明一个与框架相冲突的绝对次序；部署清单必须通过来源选择和测试证明最终值。

### 2.2 Secret 来源

正式 Secret 默认只能来自环境变量、Kubernetes Secret，或未来经新 ADR 批准的 Secret Manager。Git、普通 ConfigMap、普通 Nacos Config、Maven Profile、镜像、前端构建变量、命令行参数、日志和测试报告不得作为正式 Secret 来源。

命令行参数被操作系统进程列表、诊断工具和部署历史暴露的风险较高，因此即使 Spring Boot 支持，也只允许覆盖非敏感诊断参数。

### 2.3 Nacos

Nacos Discovery 与 Nacos Config 是两项独立能力。启用 Discovery 不代表引入 Config。当前仓库没有 Nacos Config Starter 或真实 Config 调用方，因此 S03 不新增依赖、Data ID 或导入示例。

未来接入时必须使用 `spring.config.import`，不得恢复 `bootstrap.yml`；正式必需配置不得用 `optional:` 掩盖缺失；不得关闭 Config Import Check。普通 Nacos Config 只保存非敏感配置。若未来需要保存 Secret，必须另建 ADR，覆盖加密、密钥托管、最小权限、审计、轮换、缓存、失效和明文暴露面。

### 2.4 动态刷新

数据库/Redis 凭据、IAM 私钥、Refresh HMAC Pepper、Issuer、JWK Set URI、OAuth Client、Redirect URI、Token/Session 安全语义、CORS 信任源、Permission、SecurityFilterChain、Schema、Flyway 和 Seata 事务组不得动态刷新。

允许动态刷新的配置必须同时满足：非敏感、无协议兼容变化、有安全默认值、有边界校验、有版本和审计、刷新失败可回退。默认采取重启并重新验证。

## 3. 候选方案

### 方案 A：所有配置和 Secret 均进入 Nacos

拒绝。当前没有密钥托管、细粒度审计和轮换证据，且扩大单点泄露面。

### 方案 B：只允许环境变量

拒绝。无法覆盖 Kubernetes 文件挂载、非敏感 ConfigMap 和未来受控 Nacos Config 的合理场景。

### 方案 C：遵循 Spring Boot 覆盖规则并按敏感性分离来源

接受。它保持框架行为透明，不引入自研配置层，同时为 Secret 和动态刷新设置 Fail Closed 边界。

## 4. 后果

- 正面：来源与所有权清晰；正式 Secret 不进入普通配置中心；安全关键值变更需要重启和验证。
- 代价：部署平台必须显式管理 Secret；部分配置变更不能热更新。
- 风险：Spring Environment 仍可能被诊断端点观察，因此 `env`、`configprops` 必须受保护并保持脱敏。

## 5. 验证

- `validate-runtime-security-baseline.sh` 检查 Profile、危险默认值、Bootstrap、官方检查开关、Actuator、超时、Nacos 和动态刷新边界。
- 正反例测试证明门禁仅输出文件、属性路径和规则编号，不输出值。
- 正式 Profile 的 IAM、Gateway 和 Resource Server 通过启动期属性测试继续 Fail Fast。

## 6. 官方依据

- [Spring Boot 4.1 Externalized Configuration](https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html)
- [Spring Boot 4.1 Profiles](https://docs.spring.io/spring-boot/4.1/reference/features/profiles.html)
- [Spring Cloud Alibaba 2025.x Nacos Quick Start](https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/quick-start/)
- [Spring Cloud Alibaba 2025.x Nacos Advanced Guide](https://sca.aliyun.com/docs/2025.x/user-guide/nacos/advanced-guide/)
