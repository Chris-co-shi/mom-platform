# Spring Cloud Alibaba 2025.1 工程规范

## 官方矩阵与 MOM 基线

Spring Cloud Alibaba 官方 `2025.1.0.0` 精确矩阵为：

- Spring Cloud `2025.1.0`；
- Spring Boot `4.0.0`；
- Nacos `3.1.1`；
- Seata `2.5.0`；
- RocketMQ `5.3.1`。

MOM 当前使用 `Spring Boot 4.1.0 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0`。其中 Boot/Cloud 组合得到 Spring Cloud 官方支持，但并非 Spring Cloud Alibaba 表格中的精确版本组合，因此标记为 **MOM 已验证组合**，必须持续保留真实 Nacos、Seata 和 RocketMQ Smoke Test。

官方来源：

- 版本矩阵：https://sca.aliyun.com/docs/2025.x/overview/version-explain/
- Nacos 2025.x 高级指南：https://sca.aliyun.com/docs/2025.x/user-guide/nacos/advanced-guide/
- Nacos Quick Start：https://sca.aliyun.com/docs/2025.x/user-guide/nacos/quick-start/

## Nacos Config

- 2025.1.x 必须使用 `spring.config.import` 导入 Nacos 配置。
- 禁止新增 `bootstrap.yml`、`bootstrap.yaml` 或 `bootstrap.properties`。
- 禁止使用已弃用的 `shared-configs`、`extension-configs` 和默认 application-name 隐式加载。
- 禁止关闭 `spring.cloud.nacos.config.import-check` 来掩盖多余依赖或缺失配置。
- `optional:nacos:` 与强制 `nacos:` 必须根据服务失败策略显式选择。

## Nacos Discovery

- 普通单元测试关闭 Discovery；注册发现只在独立 Smoke Test 中连接真实 Nacos。
- 服务名、namespace、group、metadata 和健康检查配置必须受控，不接受用户输入动态创建。
- Nacos 不可用时必须明确服务启动、读请求和写请求的失败语义。

## Seata

- Seata 默认关闭，只有获准场景显式开启。
- 不得在 TC 不可用时静默降级成普通本地写入。
- `@GlobalTransactional`、XID 传播、DataSourceProxy、`undo_log` 和事务组映射变化必须执行真实 TC 加两个独立 PostgreSQL 数据库的 AT Smoke Test。
- Seata 不替代 Outbox、Inbox、幂等、补偿和对账。

## RocketMQ

- Broker、NameServer、Topic 和消费者组使用受控配置。
- 消息可靠性使用 Outbox/Inbox 和业务幂等，不把 Binder 发送成功视为业务完成。
- 升级或配置变化必须验证正常发送、重复投递、Broker 中断恢复、消费失败和 DLQ。
