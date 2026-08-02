# 出站 HTTP 与 OpenFeign 工程规范

- 状态：Current
- 生效 Slice：P1.6 S03
- 模块边界：[模块与分层规范](module-layering-standard.md)

## 1. 位置与职责

`@FeignClient` 只能位于 `*-client` 或 Server 的明确 Infrastructure HTTP Adapter，不得位于 Domain/API，也不得依赖提供方 Server。Client 使用稳定 API DTO，不向 Application/Domain 暴露 Feign 类型。

当前仓库只有 `MdmServiceProbeClient` 与 `IntegrationSeataAtParticipantClient`，均位于对应 client 模块，属于 Phase 01 技术探针；不存在统一 ErrorDecoder 或业务级远端调用契约。S03 只冻结规则，不批量改 Client。

## 2. Timeout

每个正式 Client 必须显式设置有限的 connect/read timeout。零值、负值或无限等待禁止；超时由调用场景与上游 SLO 决定，不能以放大 HTTP 超时承载长任务。数据库事务内不得进行长 Feign 调用。

当前两个 Client 分别配置 `2000/3000ms` 与 `2000/5000ms`，满足有限值基线；具体预算仍需在真实业务调用方出现时评审。

## 3. Retry

Spring Cloud OpenFeign 默认提供 `Retryer.NEVER_RETRY` Bean，与 Feign 原生默认行为不同；MOM 继续采用“写请求默认不自动重试”。允许重试必须同时具备幂等语义或完整 Idempotency-Key、次数上限、退避和总超时预算；4xx、认证失败与业务冲突不盲目重试。

禁止 Feign、Gateway、SDK 与业务循环多层叠加。Seata XID 继续由 Spring Cloud Alibaba 集成传播，业务代码不手工复制协议 Header。

## 4. ErrorDecoder

ErrorDecoder 将远端 HTTP 失败转换为 Adapter/Application 可理解的明确异常，保留 401、403、404、409、429、503、504 的语义；不得统一转成 500、吞错、伪造成功，或依据本地化 message 分支。错误不得回显 SQL、Token、凭据或堆栈。

网络错误必须可观察，不能伪装成本地事务或本地方法；调用方必须显式处理远端不可达、超时、协议错误和业务冲突。

## 5. Credential、Header 与日志

- 不盲目转发当前用户 Authorization 或 Cookie；用户委托必须先有协议决策。
- 服务身份未冻结前不得发明固定 Secret Header。
- Trace/Correlation 使用 Micrometer Observation 和框架传播，不手拼 `traceparent`。
- `FULL` 日志不得用于 Base/正式环境；敏感 Header/Body 不进入日志。
- 客户端不能信任上游返回的内部身份 Header 作为授权事实。

## 6. Fallback

Fallback 不得返回伪成功、假数据或吞掉写失败；读取降级必须说明陈旧性和来源，并输出低基数指标。正确性敏感写操作默认 Fail Closed。S03 不引入 Circuit Breaker/Resilience4j 依赖。

## 7. Review 清单

1. Client 是否位于允许模块并依赖 API？
2. connect/read timeout 是否有限且有预算？
3. 方法与重试是否幂等，是否存在多层放大？
4. ErrorDecoder 是否保持状态语义并脱敏？
5. Credential 是否有明确委托/服务身份协议？
6. 调用是否发生在数据库事务内？
7. Fallback 是否会伪造成功或陈旧数据？

## 8. 官方依据

- [Spring Cloud OpenFeign Reference](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)
- [Spring Cloud OpenFeign Configuration Properties](https://docs.spring.io/spring-cloud-openfeign/reference/configprops.html)
