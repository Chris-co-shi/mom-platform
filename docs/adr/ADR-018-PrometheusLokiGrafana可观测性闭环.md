# ADR-018：Prometheus、Loki 与 Grafana 可观测性闭环

- 状态：Accepted
- 日期：2026-07-19
- 关联：Phase 01 技术骨架计划、可观测性架构、ADR-017

## 1. 背景

P01-S07 已完成 Micrometer/OpenTelemetry、OTLP Collector 与 Tempo 的真实链路验证，但 Phase 01 完成定义还要求在统一界面查看 Trace、日志和指标，并具备最小告警能力。仅启动 Prometheus、Loki 和 Grafana 容器，或只验证各后端 API 可访问，不能证明 Grafana 的数据源、Dashboard、跨信号关联和告警规则真实可用。

## 2. 决策

### 2.1 信号后端

- 指标由应用 Actuator `/actuator/prometheus` 暴露，Prometheus 主动抓取；
- Trace 延续 ADR-017：应用通过 OTLP/HTTP 发送到 OpenTelemetry Collector，Collector 转发到 Tempo；
- 应用日志写标准输出或受控日志文件，Grafana Alloy 负责采集、处理并发送到 Loki；
- Grafana 只作为查询、关联和展示入口，不成为任何信号的权威存储。

### 2.2 日志采集

采用 Grafana Alloy，不新增 Promtail。Alloy 在 CI 中使用 `loki.source.file` 采集真实打包应用日志；生产 k3s 部署由 `mom-infra` 使用 Kubernetes 日志发现能力实现，业务应用不得直接调用 Loki HTTP API。

日志流只索引低基数字段：

- `service`；
- `environment`；
- `level`。

`trace_id` 与 `span_id` 作为日志正文和 Loki structured metadata 保存，不作为 Loki Stream Label，避免高基数索引膨胀。

### 2.3 Grafana Provisioning

数据源、Dashboard 和关联规则全部以代码 Provisioning：

- Prometheus UID：`prometheus`；
- Loki UID：`loki`；
- Tempo UID：`tempo`；
- 平台总览 Dashboard UID：`mom-platform-overview`。

Tempo 配置 Trace-to-Logs 和 Trace-to-Metrics；Loki 配置从日志 Trace ID 跳转到 Tempo。生产环境不得依赖人工点击创建核心数据源和 Dashboard。

### 2.4 告警

Phase 01 至少固化：

- `MOMServiceDown`；
- `MOMHighHttp5xxRate`；
- `MOMHikariPoolExhausted`。

告警规则首先由 Prometheus 评估。通知路由、值班策略和高可用 Alertmanager 属于 `mom-infra` 部署阶段，不在本切片内伪装完成。

### 2.5 故障语义

Prometheus、Loki、Grafana、Alloy、Tempo 或 Collector 不可用时，不得改变：

- HTTP 业务响应；
- PostgreSQL 本地事务；
- Outbox/Inbox 状态；
- RocketMQ 消费确认；
- Seata 全局事务结果。

遥测可以在资源上限或后端故障时丢失，但必须通过组件自身指标、日志和告警暴露，不允许反向阻塞生产请求。

## 3. 候选方案

### 3.1 应用直接推送日志到 Loki

拒绝。它把日志后端协议、网络重试和故障压力带入业务进程，扩大业务与观测基础设施耦合。

### 3.2 继续使用 Promtail

拒绝。新基线使用 Grafana 官方推荐的 Alloy，避免在新项目中建立需要再次迁移的采集层。

### 3.3 只验证 Prometheus、Loki、Tempo 后端 API

拒绝。Phase 01 明确要求 Grafana 统一查看三类信号，因此必须验证 Grafana 数据源健康、Dashboard Provisioning 和 Datasource Proxy 查询。

### 3.4 在本阶段建设生产高可用监控集群

拒绝。当前目标是兼容性和最小可部署基线；对象存储、长期保留、多副本、租户隔离、Alertmanager 路由和容量规划由 `mom-infra` 后续落地。

## 4. 后果

正面后果：

- 三类信号拥有统一入口；
- Dashboard 和数据源可重复部署；
- Trace、日志和指标之间具有稳定关联；
- 告警规则进入版本控制并经过真实故障验证；
- 业务应用保持对具体日志和指标后端无感知。

代价与风险：

- CI 需要同时运行多个基础设施容器，资源消耗和执行时间增加；
- 单机 CI 配置不能代表生产容量、保留和高可用；
- Dashboard 查询依赖实际指标命名，框架升级时必须执行真实回归；
- Loki Label 设计错误会造成高基数和存储成本，必须持续治理。

## 5. 验证

P01-S08 CI 必须真实验证：

1. Prometheus 抓取 Gateway、Integration 和 MDM，三者 `up=1`；
2. Prometheus 能查询 JVM 与 HTTP 指标；
3. Alloy 把真实应用日志写入 Loki；
4. Loki 能按 `service/environment` 查询包含固定 Trace ID 的日志，且 Stream Label 不包含 `trace_id`；
5. Tempo 能查询 Gateway → Integration → MDM 完整 Trace；
6. Grafana 三个数据源健康；
7. Grafana Datasource Proxy 分别查询到指标、日志和 Trace；
8. `mom-platform-overview` Dashboard 由 Provisioning 创建；
9. 实际停止 MDM 后，`MOMServiceDown` 进入 firing；
10. 原有主 CI、Messaging CI、Seata CI 和 Observability CI 无回归。

## 6. 替代条件

出现以下任一情况时应新增 ADR：

- 从 Prometheus 切换到 Mimir 或其他远程指标存储；
- 从 Loki 切换到其他日志平台；
- 使用 OpenTelemetry Logs 取代文件/容器日志采集；
- 引入多租户观测数据隔离；
- 告警权威从 Prometheus 迁移到 Grafana-managed Alerting 或其他系统；
- 生产日志需要强一致、零丢失或合规归档语义。
