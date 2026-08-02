# Testcontainers、Smoke 与基础设施验收规范

- 状态：Current
- 生效 Slice：P1.6 S04

## 1. Testcontainers

使用根 BOM 的 Testcontainers `2.0.5`，镜像固定版本并禁止 `latest`。测试不依赖开发机已有容器或 CI Reuse；使用随机映射端口、独立数据库/Schema/Redis 命名空间和唯一数据。容器级 Wait Strategy 必须验证服务真实可用，不只看 TCP；超时有界，失败日志脱敏，资源由 JUnit 扩展清理。

`static @Container` 只在单测试类共享；不跨类共享可变容器。Testcontainers JUnit 5 并行执行没有官方支持证据，因此 MOM 不对这些 IT 开启并行。Apple Silicon、x86_64 和 GitHub Runner 均要求镜像提供相应架构；无法证明时登记环境限制。

## 2. L4 打包 Smoke

必须启动实际 `*-exec.jar`、使用真实端口并验证健康、关键入口和依赖故障；不得直接调用 Bean 绕过打包物。脚本使用 `set -Eeuo pipefail`、trap cleanup、有界轮询和固定镜像，失败上传容器/进程状态及有界日志。

## 3. L5 独立验收

- Nacos Discovery：Nacos readiness、MDM/Integration 注册、Feign、Gateway `lb://`、Correlation 和发现中断；不依赖 Redis。
- Redis Idempotency：直接启动 Integration 打包应用，验证首次/重复、TTL、Key 脱敏、大小写/Unicode/空格和 Fail Closed；不依赖 Nacos/Gateway。
- Redis Rate Limit：两个 Gateway 打包实例、Smoke 专用静态下游、共享配额、429、Redis 503、恢复和低基数指标；不修改生产 Route，不依赖 Nacos。
- PostgreSQL、RocketMQ、Seata、Collector/Tempo/Loki/Grafana 使用各自专项脚本，结论互不替代。

Nacos 3.1.0 镜像即使测试关闭客户端鉴权，入口脚本仍要求测试 Token/Identity；Smoke 在进程环境中生成隔离值，并使用 `8848/nacos/v3/admin/core/state/readiness`。Redis 使用 `redis:8.4.4-alpine` 和 `redis-cli ping`，业务可用性仍由打包应用请求证明。

## 4. Test Binder 与跨仓库 E2E

Test Binder 只属于 L2/L3 的应用路径证据；Broker 行为由真实 RocketMQ验收。完整 platform/web/mobile/infra E2E 属于 L6，S04 不执行；IAM 全协议 E2E 进入 S10。
