# mom-data-permission：数据权限预留模块

**状态：Maven 骨架。** [`pom.xml`](../../mom-framework/mom-data-permission/pom.xml) 声明对 `mom-core`、`mom-data` 的依赖，但 `src/main/java` 没有实现或公开 API。当前没有可接入的行级过滤器、数据范围上下文或业务消费者。

**接入判断：** 不能凭模块名认为查询已做数据隔离，也不能在业务开发中调用不存在的公共能力。当前数据 Owner 与授权边界分别按[模块分层规范](../engineering/standards/module-layering-standard.md)和[安全运行规范](../engineering/standards/security-protocol-runtime-standard.md)执行；未来数据权限模型需由业务设计决定。
