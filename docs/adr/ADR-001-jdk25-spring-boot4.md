# ADR-001：JDK 25 与 Spring Boot 4 技术基线

- 状态：Accepted
- 日期：2026-07-18
- 关联需求：NFR-MNT-001、NFR-MNT-002
- 关联计划：[Phase 01：技术骨架计划](../plans/Phase-01-技术骨架计划.md)

## 1. 背景

项目需要形成可长期演进的现代 Java 工业软件底座，同时通过真实兼容性验证展示架构升级和技术治理能力。项目不以“使用最稳定旧版本”为唯一目标，也不能为了追新而接受不可控的中间件风险。

## 2. 候选方案

### 方案 A：JDK 21 + Spring Boot 3.5

优点：生态成熟、第三方 Starter 兼容性较高。

缺点：无法达到项目对 JDK 25、Spring Framework 7、Jakarta EE 11 和 Boot 4 技术研究的目标。

### 方案 B：JDK 25 + Spring Boot 4

优点：长期技术基线新、面试展示价值高，可直接面向 Spring Framework 7 和新一代生态。

缺点：部分中间件 Starter 需要额外兼容性验证，Jackson 3、Jakarta 命名空间和自动配置可能存在迁移工作。

## 3. 决策

V1 使用：

- JDK 25。
- Spring Boot 4.1.x。
- Spring Framework 7.x。
- Spring Cloud 2025.1.x。
- Spring Cloud Alibaba 2025.1.x。

具体补丁版本由根 POM 和 `mom-dependencies` 锁定，禁止无评审漂移。

## 4. 实施约束

- 禁止新增 `javax.*` 依赖。
- Maven Enforcer 要求 JDK 25、Maven 3.9.9+。
- 使用 Maven Compiler `release=25`。
- Nacos、RocketMQ、Seata、MyBatis-Plus、SAS、Gateway、Feign 和 Testcontainers 必须逐项完成兼容性验证。
- Nacos Config 使用 `spring.config.import`。
- DTO、事件和数据库 JSON 字段必须有 Jackson 3 契约测试。
- 禁止使用未经批准的 Snapshot。

## 5. 兼容性处理策略

若某个 Starter 与 Boot 4.1 不兼容，处理顺序为：

1. 升级到官方兼容版本。
2. 排除冲突依赖并显式配置。
3. 替换 Starter，直接使用官方客户端或自有自动配置。
4. 暂缓该非核心能力。

不因单个 Starter 问题回退到 Spring Boot 3。

## 6. 正向后果

- 形成面向未来的 Java 技术基线。
- 可以验证 Boot 4、Framework 7、JDK 25 和现代可观测性体系。
- 提升项目的架构研究与面试展示价值。

## 7. 负向后果与风险

- 第三方兼容性验证成本增加。
- 部分资料和示例仍停留在 Boot 3。
- 需要处理 Jakarta、Jackson 3 和测试工具升级。

## 8. 验证方式

- CI 使用 JDK 25 执行 `mvn verify`。
- 完成 Nacos 注册与配置导入。
- 完成 RocketMQ 生产消费和 Trace 传播。
- 完成 Seata AT PoC。
- 完成 Gateway、SAS、Feign、PostgreSQL、Redis 和 Testcontainers 集成测试。
- 生成依赖树并检查版本冲突。

## 9. 替代条件

只有 Spring Boot 4 版本线出现无法通过官方客户端或自有适配解决的系统性阻塞时，才允许通过新 ADR 重新评估具体 Boot 4 补丁版本；JDK 25 和 Boot 4 总体方向保持不变。