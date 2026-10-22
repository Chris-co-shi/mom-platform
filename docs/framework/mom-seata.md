# mom-seata：受控依赖入口

**状态：依赖模块，无自有运行时 Java API；默认关闭，未确认业务采用。** [`pom.xml`](../../mom-framework/mom-seata/pom.xml) 引入 Spring Cloud Alibaba Seata Starter；模块内没有统一分布式事务包装器或业务集成契约。

当前业务写优先按 Owner 本地事务处理；Outbox/Inbox 各自使用本地事务。不能仅因添加此依赖而把跨服务操作宣称为原子提交，也不能在业务 Slice 中自行开启 Seata。受控验证与适用边界见 [ADR-009](../adr/ADR-009-Seata使用边界.md) 和[事务规范](../engineering/standards/transaction-consistency-standard.md)。
