# MOM Platform

面向新能源材料制造场景的工业 MOM 平台后端仓库。

## 技术基线

- JDK 25
- Spring Boot 4.1.x
- Spring Framework 7.x
- Spring Cloud 2025.1.x
- Spring Cloud Alibaba 2025.1.x
- PostgreSQL、Redis、RocketMQ、Nacos、Seata
- OpenTelemetry、Tempo、Prometheus、Loki、Grafana

## 模块

- `mom-dependencies`：统一依赖版本清单。
- `mom-framework`：平台级基础能力，不包含 MOM 业务领域。
- `mom-gateway`：统一入口、认证、Redis 限流、审计与上下文传递。
- `mom-*-platform`：IAM、MDM、MES、WMS、QMS、EMS、EAM、Integration、Traceability。
- `mom-bootstrap-tests`：跨模块架构与启动验证。

每个核心领域平台按 `api / client / server` 分层：

- `api`：DTO、命令、查询、事件契约和枚举。
- `client`：Feign 客户端及调用适配。
- `server`：领域、应用、接口和基础设施实现。

## 构建

```bash
mvn -B -ntp verify
```

构建要求 JDK 25 和 Maven 3.9.9+。

## 当前阶段

当前提交只建立 V1 技术骨架和架构约束，不包含业务 CRUD。首个业务垂直切片为：

供应商送货 → 原料检验 → 原料入库 → 生产执行 → PCS 协同 → 成品放行 → WCS 入库 → 批次追溯。
