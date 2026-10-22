# MOM Platform

面向流程制造、离散制造及混合制造的通用 MOM 后端平台，以可运行的业务切片验证主数据、制造执行、仓储、质量、集成与追溯边界。

> 当前处于持续建设阶段，不应直接用于生产。已实现范围以 [当前实现地图](docs/当前实现地图.md) 和源码为准。

## 当前能力

- Mini Auth：用户、角色、权限、关系维护、账号密码登录和 Redis Opaque Token。
- Gateway：路由、Bearer 形态检查、可信 Header 清洗与本地限流；最终认证授权在 Resource Server。
- System V1：受限字典、支持 Locale 和 `system.*` 动态国际化。
- MDM：组织与仓储结构、物料与 UOM、配方/工艺路线/生产版本、质量基础定义和一致性检查。
- QMS：保留独立运行时质量域边界；质量定义 CRUD 已迁移至 MDM。

详细接口、Schema、权限和成熟度见 [当前实现地图](docs/当前实现地图.md)。

## 技术基线

| 类别 | 技术 |
|---|---|
| 运行时 | JDK 25、Spring Boot 4.1、Spring Cloud 2025.1、Spring Cloud Alibaba 2025.1 |
| 数据 | PostgreSQL、MyBatis-Plus、Flyway、Redis |
| 调用与消息 | OpenFeign、RocketMQ、Outbox/Inbox |
| 可观测性 | Micrometer、OpenTelemetry、Prometheus、Loki、Tempo、Grafana |
| 测试 | JUnit 5、Testcontainers、ArchUnit |

具体版本以根 `pom.xml` 和 `mom-dependencies/pom.xml` 为准。

## 模块

```text
mom-framework              公共技术能力
mom-gateway                WebFlux 网关
mom-auth-platform          认证与权限
mom-system-platform        平台字典与国际化
mom-mdm-platform           静态主数据及质量基础定义
mom-qms-platform           运行时质量边界
mom-mes-platform           制造域骨架
mom-wms-platform           仓储运行时边界
mom-integration-platform   外部集成边界
mom-traceability-platform  追溯边界
mom-ems/eam-platform       能源与设备域骨架
mom-architecture-tests     架构适应度测试
```

## 开发入口

```bash
bash scripts/codex-doctor.sh
bash scripts/codex-verify-changed.sh
```

- [任务型文档导航](docs/README.md)
- [当前实现地图](docs/当前实现地图.md)
- [ADR 索引](docs/adr/README.md)
- [工程规范索引](docs/engineering/standards/README.md)
- [MOM 项目基线](docs/plans/MOM项目基线.md)

架构默认采用渐进式分层，详见 [ADR-042](docs/adr/ADR-042-MOM渐进式分层与对象模型.md)。
