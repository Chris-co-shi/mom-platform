# 第三方依赖来源与许可证记录

本文件记录 MOM Platform 主动新增且需要持续审查的第三方依赖。版本权威来源仍为根目录 `pom.xml` 和导入的官方 BOM。

| 依赖 | 当前版本 | 使用范围 | 官方来源 | 许可证 | 说明 |
|---|---:|---|---|---|---|
| Project Lombok | 1.18.46 | Java 编译期注解处理；用于生成 getter、setter 等机械代码 | https://projectlombok.org/ / https://github.com/projectlombok/lombok | MIT License | 仅作为 `provided` 编译依赖和 Maven annotation processor，不进入应用运行时依赖；禁止在持久化实体上使用 `@Data` 自动生成身份相关方法。 |
| Spring Cloud Stream | 由 Spring Cloud 2025.1.2 BOM 管理 | `mom-messaging` 的 Binding、函数式消息模型和 `StreamBridge` 传输适配 | https://spring.io/projects/spring-cloud-stream / https://github.com/spring-cloud/spring-cloud-stream | Apache License 2.0 | 仅负责消息应用抽象，不负责 MOM 本地事务、Outbox 状态机和消费幂等。升级时必须与 Spring Boot、Spring Cloud Alibaba 和 Binder 联合验证。 |
| Spring Cloud Alibaba RocketMQ Binder | 2025.1.0.0 | MDM Producer 与 Integration Consumer 的 RocketMQ Binder | https://sca.aliyun.com/ / https://github.com/alibaba/spring-cloud-alibaba | Apache License 2.0 | 依赖 `spring-cloud-starter-stream-rocketmq`；Topic、Group、重试和 DLQ 必须显式配置，兼容性以真实 Broker CI 为准。 |
| Apache RocketMQ Client | 5.3.1（由 Spring Cloud Alibaba BOM 管理） | RocketMQ Binder 底层生产、消费和协议实现 | https://rocketmq.apache.org/ / https://github.com/apache/rocketmq | Apache License 2.0 | 业务代码不得直接依赖 Client 类型；确需使用 Binder 未覆盖能力时必须新增 ADR。 |

## 维护规则

- 升级版本前必须确认目标 JDK、Spring Boot、Spring Cloud、Binder 与 Broker 兼容性。
- 新增依赖必须记录官方来源、许可证、运行时影响和替代方案。
- 编译期依赖不得因为使用方便而被打入业务应用运行时包。
- 消息中间件升级不能只验证编译，必须执行真实生产、消费、重复投递、故障恢复和 DLQ 测试。
