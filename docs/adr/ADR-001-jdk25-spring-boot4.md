# ADR-001：JDK 25 与 Spring Boot 4 技术基线

- 状态：Accepted
- 日期：2026-07-18

## 决策

V1 使用：

- JDK 25
- Spring Boot 4.1.x
- Spring Framework 7.x
- Spring Cloud 2025.1.x
- Spring Cloud Alibaba 2025.1.x

## 约束

- 禁止新增 `javax.*` 依赖。
- Maven Enforcer 要求 JDK 25、Maven 3.9.9+。
- Nacos、RocketMQ、Seata、MyBatis-Plus 必须逐项完成 Boot 4 兼容性验证。
- 不因单个 Starter 问题回退至 Spring Boot 3。
