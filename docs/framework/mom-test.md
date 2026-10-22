# mom-test：测试依赖聚合

**状态：多测试模块采用；无自有运行时 Java API。** [`pom.xml`](../../mom-framework/mom-test/pom.xml) 聚合 `spring-boot-starter-test`、ArchUnit 和 Testcontainers JUnit/PostgreSQL 等测试依赖；`src/main/java` 无实现，不作为生产公共能力。

测试应按[测试策略](../engineering/standards/testing-strategy-standard.md)选择层级、真实基础设施与替身，按[Maven 测试生命周期](../engineering/standards/maven-test-lifecycle-standard.md)运行；依赖聚合本身不建立业务验收证据，也不能替代真实 PostgreSQL/Redis/Broker 故障验证。
