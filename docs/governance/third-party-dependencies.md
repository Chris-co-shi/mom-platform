# 第三方依赖来源与许可证记录

本文件记录 MOM Platform 主动新增且需要持续审查的第三方依赖。版本权威来源仍为根目录 `pom.xml` 和导入的官方 BOM。

| 依赖 | 当前版本 | 使用范围 | 官方来源 | 许可证 | 说明 |
|---|---:|---|---|---|---|
| Project Lombok | 1.18.46 | Java 编译期注解处理；用于生成 getter、setter 等机械代码 | https://projectlombok.org/ / https://github.com/projectlombok/lombok | MIT License | 仅作为 `provided` 编译依赖和 Maven annotation processor，不进入应用运行时依赖；禁止在持久化实体上使用 `@Data` 自动生成身份相关方法。 |
| Spring Cloud Stream | 由 Spring Cloud 2025.1.2 BOM 管理 | `mom-messaging` 的 Binding、函数式消息模型和 `StreamBridge` 传输适配 | https://spring.io/projects/spring-cloud-stream / https://github.com/spring-cloud/spring-cloud-stream | Apache License 2.0 | 仅负责消息应用抽象，不负责 MOM 本地事务、Outbox 状态机和消费幂等。升级时必须与 Spring Boot、Spring Cloud Alibaba 和 Binder 联合验证。 |
| Spring Cloud Alibaba RocketMQ Binder | 2025.1.0.0 | MDM Producer 与 Integration Consumer 的 RocketMQ Binder | https://sca.aliyun.com/ / https://github.com/alibaba/spring-cloud-alibaba | Apache License 2.0 | 依赖 `spring-cloud-starter-stream-rocketmq`；Topic、Group、重试和 DLQ 必须显式配置，兼容性以真实 Broker CI 为准。 |
| Apache RocketMQ Client | 5.3.1（由 Spring Cloud Alibaba BOM 管理） | RocketMQ Binder 底层生产、消费和协议实现 | https://rocketmq.apache.org/ / https://github.com/apache/rocketmq | Apache License 2.0 | 业务代码不得直接依赖 Client 类型；确需使用 Binder 未覆盖能力时必须新增 ADR。 |
| Spring Cloud Alibaba Seata Starter | 2025.1.0.0 | `mom-seata` 的 Boot 4 自动配置、Feign XID 传播和 Seata 集成 | https://sca.aliyun.com/ / https://github.com/alibaba/spring-cloud-alibaba | Apache License 2.0 | 只允许受控短同步事务；默认关闭。升级必须与 Boot、Cloud、Seata Client 和 Seata Server 联合验证。 |
| Apache Seata Spring Boot Starter / Client | 2.5.0（由 Spring Cloud Alibaba BOM 管理） | AT 数据源代理、TM/RM、XID、Undo Log 和全局事务协议 | https://seata.apache.org/ / https://github.com/apache/incubator-seata | Apache License 2.0 | 不允许业务代码直接操作 TC/RM 内部 API；正式场景必须遵守 ADR-009 与 ADR-016，TC 不可用时 fail-closed。 |
| Spring Boot OpenTelemetry Starter | 由 Spring Boot 4.1.0 BOM 管理 | `mom-tracing` 的 OpenTelemetry SDK、OTLP Exporter 与 Micrometer Tracing 自动配置 | https://spring.io/projects/spring-boot / https://github.com/spring-projects/spring-boot | Apache License 2.0 | 业务与 Framework 使用 Micrometer 门面，不直接依赖 SDK。OTLP 默认关闭，Collector 故障不得改变业务结果。 |
| OpenFeign Micrometer | 由 Spring Cloud / OpenFeign BOM 管理 | OpenFeign Client Observation、Trace Context 注入和客户端指标 | https://github.com/OpenFeign/feign | Apache License 2.0 | 只有在 `ObservationRegistry` 存在时启用；不得手工复制 W3C Trace Header。 |
| OpenTelemetry Collector Contrib | 0.156.0（CI 镜像） | OTLP 接收、批处理与 Tempo 转发 | https://opentelemetry.io/docs/collector/ / https://github.com/open-telemetry/opentelemetry-collector-contrib | Apache License 2.0 | 当前版本仅用于真实兼容性 CI；生产部署、容量和升级由 `mom-infra` 管理。 |
| Grafana Tempo | 2.10.5（CI 镜像） | Trace 存储与查询 | https://grafana.com/oss/tempo/ / https://github.com/grafana/tempo | AGPL-3.0 | CI 使用单机本地存储验证，不代表生产高可用、保留或对象存储方案。 |
| Prometheus | 3.12.0（CI 镜像） | Actuator 指标抓取、PromQL 查询和最小告警规则评估 | https://prometheus.io/ / https://github.com/prometheus/prometheus | Apache License 2.0 | CI 使用单实例短保留；生产长期存储、高可用和 Alertmanager 路由由 `mom-infra` 管理。 |
| Grafana Loki | 3.7.2（CI 镜像） | 应用日志存储、LogQL 查询和 Trace 日志关联 | https://grafana.com/oss/loki/ / https://github.com/grafana/loki | AGPL-3.0-only | 只索引低基数 Stream Label；Trace ID 作为 structured metadata 和正文保存。CI 单机文件存储不代表生产方案。 |
| Grafana Alloy | 1.16.1（CI 镜像） | 采集应用日志、提取低基数标签并转发到 Loki | https://grafana.com/oss/alloy/ / https://github.com/grafana/alloy | Apache License 2.0 | 新日志采集基线使用 Alloy，不新增 Promtail。业务应用不得直接推送 Loki。 |
| Grafana | 13.1.0（CI 镜像） | Prometheus、Loki、Tempo 数据源、Dashboard 与跨信号查询入口 | https://grafana.com/oss/grafana/ / https://github.com/grafana/grafana | AGPL-3.0-only | 核心数据源和 Dashboard 必须使用 Provisioning；Grafana 不作为指标、日志或 Trace 的权威存储。 |

## 维护规则

- 升级版本前必须确认目标 JDK、Spring Boot、Spring Cloud、Binder、Broker、Seata Client、OpenTelemetry 与后端兼容性。
- 新增依赖必须记录官方来源、许可证、运行时影响和替代方案。
- 编译期依赖不得因为使用方便而被打入业务应用运行时包。
- 消息中间件升级不能只验证编译，必须执行真实生产、消费、重复投递、故障恢复和 DLQ 测试。
- Seata 升级不能只验证应用启动，必须使用两个独立数据库验证全局提交、参与者失败、远端成功后回滚、Undo Log 清理和 TC 中断。
- Tracing 升级不能只验证 Bean 或依赖树，必须使用真实 Collector、Tempo、Gateway、Feign 和消息链路验证传播、导出与故障策略。
- Prometheus、Loki、Alloy 或 Grafana 升级必须验证数据源健康、真实查询、Dashboard Provisioning、低基数标签和告警生命周期。
