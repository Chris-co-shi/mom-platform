## 变更目标

请说明本 PR 解决的问题、所属 Phase/Slice，以及明确不在本 PR 范围内的内容。

## 架构边界

- [ ] 未让 `*-api` 依赖 WebMVC、数据访问或具体 Server
- [ ] 未让领域 Server 直接依赖其他领域 Server
- [ ] Gateway 保持纯 WebFlux
- [ ] 未破坏 PCS/WCS 独立仓库边界

## Java 中文注释

- [ ] 所有新增或实质修改的 Java 类型均包含完整中文 Javadoc
- [ ] 公共方法已说明参数、返回值、异常、幂等性和副作用
- [ ] 并发、一致性、事务、失败策略及非显然设计原因已有中文说明
- [ ] 未使用逐行翻译、空泛模板或与实现不一致的注释

## 官方技术基线

- [ ] 技术结论已核对对应版本的官方文档、发布说明、兼容矩阵或官方源码
- [ ] 未引入 JDK 25 Preview、Incubator、内部 API 或无 ADR 的 `--add-opens`/`--add-exports`
- [ ] Spring Boot、Spring Cloud 和 Spring Cloud Alibaba 依赖版本由对应 BOM/Release Train 管理
- [ ] 未关闭 Spring Cloud Compatibility Verifier 或 Nacos Config Import Check
- [ ] 未新增 `bootstrap.yml`、`bootstrap.yaml`、`bootstrap.properties`，Nacos Config 使用 `spring.config.import`
- [ ] 单元测试不启动或操作 PostgreSQL、Redis、Nacos、Seata、RocketMQ 等外部状态
- [ ] 真实 Integration Test/Smoke Test 已与默认 Surefire 单元测试范围隔离

## 基础设施与失败策略

- [ ] Redis、数据库、消息、注册中心等不可用时的 fail-open/fail-closed 策略已明确
- [ ] 临时状态、幂等键或锁均设置 TTL
- [ ] 未使用 Java 原生序列化存储不可信 Redis 数据
- [ ] 新增第三方依赖已核对官方来源与许可证

## 验证

- [ ] `bash scripts/codex-doctor.sh` 通过
- [ ] `bash scripts/codex-verify-changed.sh` 或等价定向验证通过
- [ ] JDK 25 `bash scripts/codex-mvn-test.sh clean verify` 通过（最终 Slice/Phase、公共依赖或发布变更）
- [ ] 单元测试或必要的真实中间件 Smoke Test 通过
- [ ] PR 中记录了实际验证结果和未完成项
