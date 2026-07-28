# JDK 25 工程规范

## 官方基线

仓库统一使用 Java 25，并通过 Maven Compiler Plugin 的 `release` 语义编译。不得同时维护独立的 `source`/`target` 版本组合。

官方来源：

- Oracle JDK 25 文档：https://docs.oracle.com/en/java/javase/25/
- Oracle JDK 25 Migration Guide：https://docs.oracle.com/en/java/javase/25/migrate/
- Oracle Virtual Threads Guide：https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html

## 语言与 API

- 默认禁止 `--enable-preview`、Incubator 和 Experimental API；确需使用必须有 ADR、独立 Maven Profile、独立 CI 和回退方案。
- 公共 API 不得暴露 Preview 或 Incubator 类型。
- 禁止直接依赖 `sun.*`、`jdk.internal.*` 或通过 `--add-opens`、`--add-exports` 绕过模块封装。
- 依赖升级或 JDK 迁移必须运行 `jdeps --jdk-internals`；反射调用仍需人工审查，因为静态分析不能覆盖全部路径。

## Virtual Thread

Virtual Thread 用于提高大量阻塞 I/O 任务的吞吐，不用于降低单请求延迟。

允许前提：

- 线程请求模型和阻塞 I/O 明确；
- 有并发压测和资源预算；
- 数据库连接池、HTTP 连接池和下游容量仍有独立限流。

禁止：

- 在 Gateway WebFlux/Reactor Event Loop 上混入阻塞调用；
- 为 Virtual Thread 建固定大小线程池或复用线程；
- 用 Virtual Thread 绕过 PostgreSQL、Redis 或远程接口容量；
- 用于长时间 CPU 密集计算。

## JVM 与诊断

- 线程、锁、GC、I/O 和延迟问题优先采集 JFR，再决定 JVM 参数。
- 运行时诊断优先使用 `jcmd`；禁止只凭经验复制大量 `-XX` 参数。
- GC 选择和堆参数必须基于压测、JFR/GC 日志和容器内存预算。
- 不得关闭 JVM 容器资源感知来掩盖资源配置错误。

## 测试与兼容

- CI 必须使用 JDK 25 执行 `clean verify`。
- 第三方库必须确认支持 JDK 25；仅能编译不代表运行时兼容。
- Locale、日期、货币、反射、序列化和安全提供者相关变更必须执行行为回归。
