# ADR-013：Redis 分布式限流

- 状态：Accepted
- 日期：2026-07-18

Gateway 使用 Redis 令牌桶实现多实例共享限流；Integration Hub 对出站连接器提供第二层限流。

限流维度至少支持 route、IP、user、client、factory、supplier、customer 与 device。Redis 故障策略必须按接口等级区分，生产操作不得简单全量失败。
