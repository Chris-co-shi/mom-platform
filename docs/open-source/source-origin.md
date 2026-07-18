# 开源来源登记

本文件记录项目研究、直接依赖或可能复用的开源项目。正式引入依赖或源码时，必须补充精确版本、许可证和 NOTICE 要求。

## 登记规则

- `直接依赖`：通过 Maven、npm/pnpm 或官方发行物引入。
- `学习后重构`：阅读源码和设计，但使用 MOM 自有接口和实现。
- `仅领域研究`：只吸收领域概念、术语和通用方法。
- `必须自研`：不得通过复制通用框架替代 MOM 核心能力。

## 项目清单

| 项目 | 官方来源 | 精确版本/提交 | 许可证 | 用途 | 复用方式 | 是否复制源码 | 核验状态 |
|---|---|---|---|---|---|---|---|
| OpenJDK | https://openjdk.org/ | JDK 25 | GPL-2.0 with Classpath Exception | Java 运行时 | 直接使用发行版 | 否 | 已确定 |
| Spring Boot | https://github.com/spring-projects/spring-boot | 根 POM 锁定 | Apache-2.0 | 应用框架 | 标准依赖 | 否 | 已确定 |
| Spring Cloud | https://github.com/spring-cloud | 根 POM 锁定 | Apache-2.0 | 微服务基础设施 | 标准依赖 | 否 | 已确定 |
| Spring Cloud Alibaba | https://github.com/alibaba/spring-cloud-alibaba | 根 POM 锁定 | Apache-2.0 | Nacos、RocketMQ、Seata 集成 | 标准依赖并做兼容验证 | 否 | 已确定 |
| Spring Authorization Server | https://github.com/spring-projects/spring-authorization-server | 待依赖锁定 | Apache-2.0 | OAuth2.1/OIDC | 标准依赖 | 否 | 待接入 |
| MyBatis-Plus | https://github.com/baomidou/mybatis-plus | 待依赖锁定 | Apache-2.0 | 数据访问 | 标准依赖并验证 Boot 4 | 否 | 待接入 |
| OpenTelemetry Java | https://github.com/open-telemetry/opentelemetry-java | 由 BOM 锁定 | Apache-2.0 | Trace 与 OTLP | 标准依赖 | 否 | 待接入 |
| Grafana Tempo | https://github.com/grafana/tempo | 由 mom-infra 锁定 | AGPL-3.0 | Trace 后端 | 独立部署 | 否 | 待部署 |
| Grafana Loki | https://github.com/grafana/loki | 由 mom-infra 锁定 | AGPL-3.0 | 日志后端 | 独立部署 | 否 | 待部署 |
| Prometheus | https://github.com/prometheus/prometheus | 由 mom-infra 锁定 | Apache-2.0 | 指标后端 | 独立部署 | 否 | 待部署 |
| Pig | https://github.com/pig-mesh/pig | 研究时锁定 Tag/Commit | Apache-2.0 | SAS、Gateway、Feign、安全上下文参考 | 学习后重构 | 否 | 已研究，待精确登记 |
| Yudao Cloud | https://github.com/YunaiV/yudao-cloud | 研究时锁定 Tag/Commit | MIT | 数据权限、审计和 API 分层参考 | 学习后重构 | 否 | 已研究，待精确登记 |
| JetLinks Community | https://github.com/jetlinks/jetlinks-community | 研究时锁定 Tag/Commit | Apache-2.0 | 协议 SPI、设备会话和物模型参考 | 学习后重构 | 否 | 待 PCS 阶段登记 |
| Apache PLC4X | https://github.com/apache/plc4x | PoC 时锁定版本 | Apache-2.0 | PLC/Modbus/S7 协议 | 直接依赖，通过适配层隔离 | 否 | 待 PoC |
| Eclipse Milo | https://github.com/eclipse/milo | PoC 时锁定版本 | EPL-2.0 | OPC UA | 直接依赖，通过适配层隔离 | 否 | 待 PoC |
| OpenWMS | https://github.com/openwms/org.openwms | 研究时锁定 Tag/Commit | 以具体模块为准 | 运输单元、任务和路由领域参考 | 仅领域研究 | 否 | 待 WCS 阶段登记 |
| Vue Vben Admin | https://github.com/vbenjs/vue-vben-admin | mom-web 初始化时锁定 | MIT | MOM Web 和门户 UI 基座 | 直接作为前端基座 | 以独立仓库记录为准 | 待 mom-web 登记 |
| uni-app | https://github.com/dcloudio/uni-app | mom-mobile 初始化时锁定 | 以官方仓库为准 | PDA 与多端基座 | 直接作为移动端基座 | 以独立仓库记录为准 | 待 mom-mobile 登记 |

## MOM 必须自研能力

- MES、WMS、QMS 等核心领域模型和业务规则。
- 库存事实流水、余额、预占和对账。
- 原料—半成品—成品多对多批次谱系。
- Integration Hub、Outbox/Inbox、补偿和对账流程。
- PCS/WCS 业务命令、状态机、恢复和人工接管。
- 工业数据权限模型。

## 新增依赖检查清单

1. 是否来自官方仓库或官方发行渠道？
2. 是否锁定了精确版本、Tag 或 Commit？
3. License 是否允许当前使用和发布方式？
4. 是否存在 NOTICE、源码公开或网络服务义务？
5. 是否复制或修改源码？
6. 是否可以通过标准依赖或适配层隔离？
7. 是否更新 `THIRD-PARTY-NOTICES.md` 和 SBOM？

禁止通过批量改包名复制其他开源项目。